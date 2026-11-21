# Changelog

## v1.0.0 — Initial Release
- **Fast Dataview Block Pages**: Opens Markdown notes as structured block pages from a local Room cache for rapid editing of YAML frontmatter and inline `key:: value` metadata properties.
- **Native Dataview (DQL) Engine**: Offline support for `TABLE`, `LIST`, `TASK`, and `CALENDAR` queries with `FROM`, `WHERE`, `SORT`, `GROUP BY`, `LIMIT`, and Dataview functions.
- **Offline DataviewJS Runtime**: Executes `dataviewjs` scripts inside an isolated `WebView` sandbox with `dv.pages()`, `dv.current()`, `DataArray` chaining, and native Material 3 table/list/task rendering.
- **Intuitive Block Editor**: Reorderable, inline-editable `Dataview`, `DataviewJS`, `Task`, `Heading`, `Callout`, and `Paragraph` blocks plus a Visual Query Builder.
- **Local Vault & SAF Sync**: Built-in on-device starter vault and Storage Access Framework (`OpenDocumentTree`) folder linking with two-way `.md` writeback.
- **Query Lab & Cross-Vault Tasks**: Interactive DQL/DataviewJS playground and vault-wide task checklist with live file sync.
