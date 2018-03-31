## [Unreleased]
### Added
- New functions: `asMap`, `getMapEntries`

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

