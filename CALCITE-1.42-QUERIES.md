# What Calcite 1.42 adds to heap queries

The plugin used to run on Calcite **1.36** in practice. That was the version in the target platform; see
MIGRATION-NOTES §4.5. It now runs on **1.42**. This note covers what the six releases in between (1.37 to 1.42)
add for SQL against a heap dump. It also covers what does **not** work yet with the plugin's configuration, and why.

**Everything here was checked.** Every query below was run against the test dump
(`MatCalciteTest/dumps/mvn1m_jdk18.hprof`) through the plugin's own connection. The check used a temporary probe
test on 2026-10-15; the outputs shown are real. Release attribution comes from the
[Calcite release history](https://calcite.apache.org/docs/history.html).

The plugin's connection is `lex=JAVA`, `quoting=DOUBLE_QUOTE`, `conformance=LENIENT`, with no `fun` library
(`CalciteDataSource.getConnection`). That setup decides most of what follows.

---

## 1. Works today, no change needed

### Bitwise operators and functions (1.41, 1.38)

`&`, `^` and `<<` (1.41), and `BITAND`, `BITOR`, `BITXOR`, `BITNOT`, `BITCOUNT` (1.38).
Together with `getAddress()`, they make **object addresses** something you can analyse.

Object alignment, grouping strings by the low 4 bits of their address:

```sql
select getAddress(this) & 15 a, count(*) c
  from java.lang.String
 group by getAddress(this) & 15
```
```
a | c
0 | 1597
8 | 1659
```

The result: half the strings sit on 16-byte boundaries, half on 8 only. This is the kind of check behind
compressed oops and object alignment questions.

More examples:

```sql
select bitand(getAddress(this), 4095) page_offset from java.lang.String   -- position inside a 4 KiB page
select bitcount(getAddress(this)) from java.lang.String                   -- bits set
select getAddress(this) << 1, getAddress(this) ^ 1 from java.lang.String
```

### ASOF JOIN (1.38): "nearest object by address"

An ASOF join matches each row with the **closest** row on the other side that satisfies an inequality. On a heap,
that means memory neighbourhood: which object sits right before or after this one.

For each `HashMap`, this finds the `String` at the nearest lower address:

```sql
select m.a map_address, s.a string_before
  from (select 1 k, getAddress(this) a from java.util.HashMap) m
  asof join (select 1 k, getAddress(this) a from java.lang.String) s
    match_condition m.a > s.a
    on m.k = s.k
 order by m.a
```
```
map_address  | string_before
33283927176  | 33283901048
33283929848  | 33283901048
...
```

**Gotcha:** the `ON` part must compare plain **columns** with `=`. Expressions such as `getType(m.this) =
getType(s.this)` or `1 = 1` are rejected ("ASOF JOIN condition must be a conjunction of equality comparisons of
columns from both sides"). Compute everything in sub-selects first, as above. For a real per-group match, put the
grouping key in both sub-selects, e.g. a class name column.

### Window frames with `EXCLUDE CURRENT ROW` (1.38)

This compares each object with **the others** of its kind, without self-joins:

```sql
select getType(this) t, retainedSize(this) r,
       sum(retainedSize(this)) over (partition by getType(this)
           rows between unbounded preceding and unbounded following
           exclude current row) retained_by_the_others
  from instanceof.java.util.AbstractMap
 order by 1, 2
```

Use this to spot the one map that dominates its class: `r` much larger than `retained_by_the_others`.

**Gotcha:** `avg(...)` over such a frame fails with `/ by zero` when a class has a single instance, since the
frame is then empty. Use `sum(...) / nullif(count(...), 0)` instead.

### Smaller additions

- **`DUAL`** (1.39): `select 1 from dual` works, handy for testing expressions.
- **Array comparison** (1.38): `array[1, 2] < array[1, 3]` returns `true`.

Not new, but worth knowing: **`QUALIFY`** has worked since Calcite 1.32, and it is the shortest way to say "top
N per class":

```sql
select getType(this) t, retainedSize(this) r
  from instanceof.java.util.AbstractMap
qualify row_number() over (partition by getType(this) order by retainedSize(this) desc) = 1
```

### Things you get without writing anything new

- **Planner work.** 1.42 adds `LEFT_MARK` joins for hash and nested-loop joins, used for `IN` / `EXISTS`
  sub-queries. 1.41 improves join reordering. These should help sub-query-heavy heap queries. *Not measured here.*

---

## 2. Works with a one-line configuration change: function libraries

Calcite 1.37 to 1.42 added many functions to its **library** operator tables (PostgreSQL, Spark, Hive, Oracle…).
They are only visible when the connection names them in the `fun` property. The plugin sets no `fun`, so today
they are **not available**.

With `fun=standard,postgresql,spark,hive` added to the connection properties in `CalciteDataSource`, all of these
worked against the heap:

| Function | Library | Example | Result on the test dump |
|---|---|---|---|
| `SPLIT_PART` (1.39) | PostgreSQL | `SPLIT_PART(getType(this), '.', 3)` | `HashMap` |
| `STARTS_WITH` (1.37) | PostgreSQL | `where STARTS_WITH(getType(this), 'java.util')` | 177 maps |
| `REGEXP_REPLACE` (1.38) | PostgreSQL | `REGEXP_REPLACE(getType(this), '^java\.util\.', '')` | `HashMap` |
| `REGEXP_LIKE` (1.37) | several | `where REGEXP_LIKE(getType(this), 'Hash.*Map')` | 175 maps |
| `SUBSTRING_INDEX` (1.38) | Spark | `SUBSTRING_INDEX(getType(this), '.', -1)` | simple class name |
| `CONCAT_WS` (1.38) | Spark | `CONCAT_WS('|', getType(this), 'x')` | `java.util.HashMap|x` |
| `REVERSE` (1.38) | Spark | `REVERSE(getType(this))` | `paMhsaH.litu.avaj` |
| `INSTR` (1.39) | Hive | `INSTR(getType(this), 'Map')` | `15` |

String functions on class names and on `toString(this)` are the main win: grouping by package, short class names
and regex filters, without writing plugin functions.

**Before turning it on:**

- **Names must be UPPER CASE.** With `lex=JAVA`, function lookup is case-sensitive and library functions are
  registered in upper case: `split_part(...)` fails, `SPLIT_PART(...)` works. Built-in standard functions
  (`bitand`, `count`…) are matched case-insensitively and are not affected. This should be documented, or the
  lexer settings revisited.
- **Overloads can surprise.** `HEX(getAddress(this))` returned the hex of the *decimal digits*
  (`3333323833393031303438`), not of the address. `STRING_TO_ARRAY(getType(this), '.')` (1.40) returned `[]`.
  Check each function before relying on it.
- **Libraries can change shared names.** Libraries redefine some common functions (`LOG`, `GREATEST`, `LEAST`,
  `LENGTH`…). Turn on only the libraries you need, and run the test suite afterwards.

---

## 3. In the release notes, but not usable with the current setup

| Feature | Release | What happened | What it would take |
|---|---|---|---|
| `SELECT * EXCLUDE (...)`, `* EXCEPT (...)`, `* REPLACE (... AS col)` | 1.42 | Parse error | The core parser never builds `SqlStarExclude`; only the **Babel** parser does. Needs the `calcite-babel` dependency (target platform + `Import-Package`) and `parserFactory=...SqlBabelParserImpl#FACTORY`. Not tried. |
| `SELECT ... BY` (GROUP BY + ORDER BY shorthand) | 1.42 | Parse error | Same as above, presumably Babel. |
| Lambdas `x -> ...` | 1.37 | Parse error | Lambdas are accepted only as function arguments. The only higher-order function (Spark `EXISTS`) collides with the `EXISTS` keyword in the core parser. |
| Lateral column alias (`select f(x) r, r / 1024 kb`) | 1.40 | "Column 'r' not found" | Not enabled through the JDBC settings used here. |
| `MEASURE` columns | 1.38 | `AGGREGATE` not found | Not usable through this connection. |
| Table function in `FROM` without `TABLE()` | 1.37 | Parses, but `getMapEntries(m.this)` fails with "Table 'm' not found" | Correlated use still needs `lateral table(...)` or `cross apply table(...)`. |
| `LEFT_SHIFT(x, n)` function | 1.41 | Not found, with or without libraries | Use the `<<` operator. |

---

## 4. Suggested follow-ups

1. **Turn on a curated `fun` list** (start with `postgresql`) in `CalciteDataSource`. Mention upper-case names in
   the SQL Reference tab, and add the functions to the schema browser's Functions group.
2. **Evaluate the Babel parser** for `SELECT * EXCLUDE/REPLACE`. Heap tables have many columns, so
   `select * exclude ("@class", "@classLoader")` would be a real ergonomic win. Check that it does not break the
   plugin's own syntax: `this['field']`, `instanceof.` names, `cross apply`.
3. **Add regression tests** for the ASOF join, `EXCLUDE CURRENT ROW` and bitwise queries above, so future Calcite
   upgrades keep them working.
