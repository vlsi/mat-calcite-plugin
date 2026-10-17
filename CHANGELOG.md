## [Unreleased]
### Added
- SQL function `getHexAddress(x)`: the object address as `0x...`, the form MAT command lines and labels use.

### Changed
- **BREAKING**: Eclipse Memory Analyzer 1.17.0 or higher is now required (previously 1.14.0).
  `org.eclipse.mat.api` 1.17.0 declares `Bundle-RequiredExecutionEnvironment: JavaSE-21`, so Java 21 is
  now required. Users on older MAT must stay on plugin version 1.6.x.
- **BREAKING**: The build target platform moved from Eclipse 2022-12 to Eclipse 4.40, matching the
  Eclipse release that MAT 1.17.0 itself builds against.
- Update Apache Calcite to 1.42.0 (requires Avatica 1.28.0, Jackson 2.18.6, Guava >= 33.4.8).
- MAT bundles are now resolved from the official MAT 1.17.0 update site
  (`https://download.eclipse.org/mat/1.17.0/update-site/`) instead of an Eclipse simultaneous release,
  so the MAT API version matches the MAT users run. The URL names the release: `latest` would move to
  the next MAT release by itself.

### Fixed
- `getDominator(x)` failed with "Object -1 not found" for objects dominated by the virtual root (e.g. shared
  objects); it now returns null.
- `ORDER BY this` (and any `ORDER BY` over a heap object column) no longer fails with
  `UnsupportedOperationException`, and sorts heap objects by address. Calcite 1.37+ compares `Map`-typed values
  with `Functions.compareMaps()`, which walks `entrySet()`; `HeapReference` implements `Map` only to support
  `this['field']` syntax and threw from `entrySet()`. It now returns one entry that orders like
  `HeapReference.compareTo`, which orders by address. (An empty set, tried first, made every two heap objects
  equal: `ORDER BY this` kept scan order, and merge joins on heap objects dropped matches, e.g. `count(cl.this)`
  of `java.lang.Class left join instanceof.java.lang.ClassLoader` gave 469 instead of 985.) Both must give the
  same order: Calcite sorts merge-join inputs through `entrySet()` but merges them with `compareTo`. Upstream's
  `compareTo` ordered by label; by address is constant time, where a label was rebuilt on every comparison
  (`ORDER BY this` over 4 million objects took about 2 minutes instead of 10 s). `ORDER BY toString(this)` sorts
  by label. Sorting an object column in a result grid is by address too.
- Added `httpclient5` and `httpcore5` (new Avatica 1.28 dependencies) and `org.jooq:joou-java-6` to the
  target platform. Without these the plugin failed at runtime with
  `NoClassDefFoundError: org/apache/hc/core5/util/Timeout` and `NoClassDefFoundError: org/joou/UInteger`.
- Excluded `jakarta.transaction:jakarta.transaction-api` from `commons-dbcp2`. It declares an
  unsatisfiable `Import-Package` (`javax.enterprise.context`) and blocked
  `geronimo-jta_1.1_spec` from satisfying the `javax.transaction` packages Calcite and dbcp2 need.
- Fixed a dependency drift bug where Calcite was declared twice with different versions:
  `MatCalcitePlugin/pom.xml` said 1.41.0 while `MatCalciteTargetPlatform.target` said 1.36.0. The
  plugin could compile against one Calcite and run against another. Both files are now kept in sync.
- `testStackTraces` failed: MAT now renders a native frame with its source file,
  `(Object.java(Native Method))` instead of `(Native Method)`. That text comes from MAT unchanged, so the
  test ignores the source file part.

### Notes
- Only the MAT units this plugin needs are listed in the target (api, report, ui, parser, hprof).
  Pulling the full `org.eclipse.mat.feature.feature.group` would drag in `mat.chart`/`mat.chart.ui`
  and therefore BIRT and Rhino, which this plugin does not use.

## [1.6.2] - 2023-11-17
### Fixed
- Fixed java.lang.IllegalArgumentException: Illegal group reference when explain plan has table names with dollars

## [1.6.1] - 2023-11-15
### Fixed
- Fixed java.lang.NoClassDefFoundError: org/apache/calcite/runtime/CalciteContextException when SQL fails in MAT UI (regression since 1.6.0)

## [1.6.0] - 2023-11-15
### Changed
- Eclipse Memory Analyzer 1.14.0 or higher is required
- Java 17 is required
- Update Apache Calcite to 1.36.0 (see https://calcite.apache.org/news/2023/11/10/release-1.36.0/)
- Update Guava to 32.1.3-jre
- Update net.minidev:accessors-smart to 2.5.0
- Update net.minidev:json-smart to 2.5.0

## [1.5.0] - 2020-10-12
### Added
- Extra property for all objects: `@class` (references to `java.lang.Class`)
- Extra property for all objects: `@className` (returns the name of the class)
- Extra property for Class objects: `@classLoader` (references to `java.lang.ClassLoader`)
- Extra property for Class objects: `@super` (references to `java.lang.Class`)

### Changed
- Eclipse Memory Analyzer 1.8.0 or higher is required
- Java 1.8 or higher is required
- Update Apache Calcite to 1.26.0 (see https://calcite.apache.org/news/2020/10/06/release-1.26.0/)
- Update Guava to 29.0-jre

### Fixed
- `name` for `IClass` return class name rather than the value of `Class.name` field (OpenJDK uses the field as a cache, so it might be null)
- Declare reference columns as nullable, so the engine does not optimize `count(name)` into `count(*)`

## [1.4.0] - 2018-09-08
### Added
- New functions: `asMap`, `getMapEntries`, `asMultiSet`, `getStringContent`
- Support entries extraction from Dexx HashMap
- Support entries extraction from vlsi.CompactHashMap
- Commandline mode

### Changed
- `ThreadStackFrames` is moved to `native` schema

### Fixed
- Autocomplete is improved in lots of cases

## [1.3.0] - 2017-09-14
### Added
- "class" tables can be accessed without quotes (e.g. `java.lang.HashMap`). Technically speaking, Java package maps to SQL schema. (Alexey Makhmutov)
- Ability to access properties via `[...]` operation. For instance `x.this['field']`
- New functions: `getId`, `getAddress`, `getType`, `getId`, `toString`, `shallowSize`, `retainedSize`, `length`, `getSize`, `getByKey`, `getField` (Alexey Makhmutov)
- Table functions: `getValues`, `getRetainedSet`, `getOutboundReferences`, `getInboundReferences` (Alexey Makhmutov)
- `CROSS APPLY`, `OUTER APPLY` syntax to call table functions
- Error highlight in the SQL editor
- Highlight of known heap functions

### Changed
- `@THIS` column is renamed to `this`
- `get_id` is renamed to `getId`
- Eclipse Memory Analyzer 1.5.0 or higher is required

### Removed
- `@RETAINED`, `@SHALLOW` columns from heap tables. Use `shallowSize(ref)` and `retainedSize(ref)` functions
- `@ID` column from heap tables. Use `getId(ref)` function

## [1.2.0] - 2014-12-06
### Added
- Functions: `length`, `get_type`, `get_by_key`, `get_id`
- F10 hotkey for explain plan

### Fixed
- Ctrl+Enter

### Changed
- `@ID` column is replaced to `@THIS`

### Removed
- `@PK` column, use `get_id` instead

