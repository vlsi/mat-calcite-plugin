## [Unreleased]

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

