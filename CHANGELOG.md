## [Unreleased]
### Added
- SQL function `getHexAddress(x)`: the object address as `0x...`, the form MAT command lines and labels use.
- SQL date and time functions: `toDateTime(x[, zone])` shows heap date objects as e.g. `2026-10-01 11:11:11.224`
  (java.util.Date, java.sql.Date/Time/Timestamp, Calendar, java.time Instant, LocalDate, LocalTime, LocalDateTime,
  ZonedDateTime, OffsetDateTime, and numbers as epoch milliseconds). Also `toEpochMillis(x[, zone])`,
  `millisToDateTime(long[, zone])`, `secondsToDateTime(long[, zone])` and `toDuration(x)`. Points in time default to
  UTC, zoned values use their own zone, java.sql.Date/Time use the dumped JVM's zone; `'jvm'` selects that zone.
- `instanceof."java.lang.ref.Reference"`: the instanceof schema takes a class's full name as one quoted
  identifier, like the root schema does, so package parts that are SQL words (`ref`, `user`) need no quoting.
- SQL editor: right-click menu with Run, Explain, Undo/Redo, Cut/Copy/Paste, Delete, Select All and
  Toggle Comment.
- SQL editor: "SQL Reference" toolbar button that opens tables, virtual columns and functions in a separate
  tab, so the current result stays open. The editor now starts with a short hint and a sample query instead of the 30-line help comment.
- SQL editor syntax colors are theme colors (Preferences > Appearance > Colors and Fonts > SQL (Calcite)
  editor), with defaults for the dark theme.
- SQL editor keys (F5 / Ctrl+Enter, F10, Ctrl+Space, Ctrl+/) are now workbench commands in their own key
  binding context. MAT binds F5, Ctrl+Enter and Ctrl+Space to its OQL commands, and the workbench consumed
  those keys before the SQL editor could see them.
- SQL editor: schema browser beside the editor (toolbar toggle) listing classes by package, classes with
  subclasses (instanceof), thread stacks and functions, with each class's columns and its this['@shallow'] /
  this['@retained'] sizes. Double-click inserts the name at the cursor, quoted where SQL needs it; the filter box
  lists matching tables.
- "SQL > Open in SQL Editor" on the context menu of objects and classes in MAT's views (histogram, dominator
  tree, object lists...). It adds a query for the selection to the last used SQL tab (a class row selects all
  instances, objects are selected by address, one statement per class) and selects it without running it.
- SQL editor: completion proposals pop up after typing '.' or '"', not only on Ctrl+Space.
- SQL result rows show MAT's object icons (instance, class, array, class loader, GC root overlay).
- Navigation history: re-opening a SQL result restores its query in the editor and runs it again, as the OQL
  pane does.

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
- SQL errors are shown without adding an entry to the navigation history, as the OQL pane does.
- The SQL result area takes 4/5 of the pane height, as in the OQL pane.

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
- SQL editor: an error in a query run from a selection (F5 on selected text, which "Open in SQL Editor" sets up)
  or from Explain was underlined at the wrong place: the position counted from the start of the editor and
  ignored the `explain plan for` prefix. A position outside the text threw instead, and the error was not shown.
- SQL editor: an error underline stayed after the query was fixed or another one ran; each run now clears it.
- SQL editor: Cancel in the progress view did not stop a running query, and closing the tab left it running. Both
  now stop the heap scan.
- SQL editor: a query that finished after its tab was closed failed on the disposed editor. Closing the tab while
  the editor had focus could leave its key bindings active and the Cut/Copy/Paste handlers pointing at it.
- "Open in SQL Editor" on a class row of a partial histogram (Show Retained Set, a dominator tree grouped by class)
  selected every instance of the class, not just the objects of the row.
- "Open in SQL Editor" added the query to the active editor, which with several dumps open is not necessarily
  the one the objects belong to; it now finds the editor of their dump.
- Schema browser filter: it sorted every table name of the dump on each keystroke (four per class, slow on dumps
  with 100,000 classes). The sorted names are now kept per schema, and the search runs when typing pauses.
- `toDateTime(x)` returned the stale time of a `java.util.Date` changed with a deprecated setter (`setHours`...)
  and of a `Calendar` changed with `set(...)` since its time was computed; it now returns null for them.
- `toDuration(x)` overflowed beyond about 292 years: `Duration.ofSeconds(Long.MAX_VALUE)`, a common "forever",
  showed as `-0s`.
- Identifiers are quoted by Calcite's own reserved words, not only SQL-92's: the schema browser
  wrote `java.lang.ref` unquoted, which does not parse (`REF` is reserved).
- The SQL reference used `instanceof.java.util.Map` as its example, which is always empty: heap
  dumps record superclasses but not interfaces. It now uses `java.util.AbstractMap` and says why.
- Running a query could scroll the SQL editor when it was scrolled to the end: JFace's
  ToolBarManager.relayout packs the toolbar's parent, which briefly lays the editor out taller, and StyledText
  scrolls to fill the extra space. The editor now restores its scroll position after such a transient resize.
- Line numbers of the SQL editor use the editor's colors, so they are readable in the dark theme.
- Backspace, Delete and the arrow keys stopped working in the query editor after a table result was shown, while
  letters still typed, until the window was deactivated and activated again. Cause: MAT shows table results with a
  "Retrieving view elements..." user job whose progress dialog is opened and closed without ever getting focus. SWT
  on GTK still records it as the active shell and, once it is disposed, has no active shell at all, so it drops every
  key press that is not committed text. On Wayland the window gets no new focus-in event to repair that. The SQL
  pane now re-activates the workbench window in that case (only if the window was never deactivated), which also
  fixes MAT's own OQL pane.
- The SQL pane leaked its title image and syntax colors. The title image now comes from the shared JFace image
  registry (the navigation history keeps showing it after the tab is closed), and the colors from the theme
  registry.
- Undo and Redo keys (Ctrl+Z, Ctrl+Y) did not work in the SQL editor; the right-click menu's Cut and Copy could be
  disabled while text was selected.
- Removed debug output printed to stdout on every result table refresh and explain plan.

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

