# VaultBlock

> Companion Android app for **Obsidian** that renders **Dataview** and **DataviewJS** queries offline on mobile, syncs directly with local Markdown vaults, and opens every note as a fast, structured block page for rapid metadata editing.

*Built as a mobile-first PKM experiment for interacting with Obsidian vaults without waiting on heavy full-note editors.*

---

## Features

- **Fast Dataview Block Pages**
  - Notes open directly from a local Room cache as modular block pages rather than a single raw text buffer.
  - **Rapid Metadata Grid**: Tap any YAML frontmatter or inline `key:: value` property chip to edit with typed mobile controls—status presets (`backlog`, `active`, `review`, `done`, `blocked`), 1–5 star ratings, boolean switches, quick date presets, numeric steppers, and tag lists.
  - **Raw Markdown Peek**: Inspect the underlying `.md` source at any time to verify formatting and frontmatter integrity.

- **Native Dataview (DQL) Engine**
  - Evaluates `TABLE`, `LIST`, `TASK`, and `CALENDAR` queries offline in Kotlin.
  - Supports `FROM` (folders, `#tags`, `AND`/`OR`/negation), `WHERE`, `SORT`, `GROUP BY`, `LIMIT`, and built-in functions (`contains`, `icontains`, `startswith`, `endswith`, `choice`, `default`, `length`, `round`, `date`, `link`, `upper`, `lower`, `join`).

- **Offline DataviewJS Runtime**
  - Executes `dataviewjs` code blocks inside an isolated, network-disabled Android `WebView` sandbox bridged to your indexed vault.
  - Provides `dv.pages()`, `dv.page()`, `dv.current()`, `dv.array()`, `dv.date()`, `dv.fileLink()`, `DataArray` chaining (`.where`, `.filter`, `.map`, `.sort`, `.groupBy`, `.limit`, `.sum`, `.avg`), and native Material 3 renderers for `dv.table()`, `dv.list()`, `dv.taskList()`, `dv.header()`, `dv.paragraph()`, and `dv.span()`.

- **Intuitive Block Editor**
  - Edit, reorder, add, or remove `Dataview`, `DataviewJS`, `Task`, `Heading`, `Callout`, and `Markdown Paragraph` blocks individually.
  - Includes a **Visual Query Builder** for creating DQL queries without typing syntax manually.

- **Two-Way Local Vault & SAF Sync**
  - **On-Device Starter Vault**: Ships with a local `.md` sample vault on first launch so every feature works immediately offline.
  - **External Folder Sync (SAF)**: Link any local Obsidian vault directory on your device using Android's Storage Access Framework (`OpenDocumentTree`) with zero broad storage permissions.
  - Property updates, block edits, and task checkbox toggles write back directly to the local `.md` files and queue offline edits cleanly if the external folder is temporarily unavailable.

- **Query Lab & Cross-Vault Tasks**
  - **Query Lab**: Interactive playground for testing DQL and DataviewJS snippets with presets and one-tap **Save as Block Page**.
  - **Tasks**: Aggregates `- [ ]` / `- [x]` tasks across your entire vault with priority/due filters and live checkbox writeback.

---

## Tech Stack

- **Language & UI**: Kotlin, Jetpack Compose, Material 3 (Adaptive `NavigationBar` / `NavigationRail`)
- **Persistence & Preferences**: Room (`vaultblock_cache.db`), Jetpack DataStore Preferences
- **Navigation & Concurrency**: Navigation Compose, Kotlin Coroutines & `StateFlow`
- **Vault Access**: Android Storage Access Framework (`DocumentsContract` / `ContentResolver`) + local app files storage
- **DataviewJS Execution**: Isolated offline `WebView` JS bridge

---

## Project Structure

```text
app/src/main/java/com/example/
├── MainActivity.kt
├── data/
│   ├── local/          # Room entities, DAO, database, DataStore preferences
│   ├── model/          # VaultNote, NoteProperty, VaultBlock, DataviewResult models
│   └── sync/           # LocalVaultProvider (on-device + SAF) & VaultRepository
├── domain/
│   ├── engine/         # DataviewQueryEngine (DQL) & DataviewJsEngine (JS bridge)
│   └── parser/         # MarkdownParser (frontmatter, inline:: fields, tasks, blocks)
└── ui/
    ├── VaultBlockApp.kt
    ├── VaultViewModel.kt
    ├── components/     # DataviewRenderers, RapidPropertyGrid, BlockEditorComponents
    ├── screens/        # BlocksHome, BlockPage, QueryLab, Tasks, VaultSync
    └── theme/          # Color, Typography, Theme
```

---

## Building Locally

### Prerequisites
- Android Studio (or Android SDK 36 with JDK 11+)
- Gradle installed on `PATH` (or via Android Studio)

### Build Debug APK
```bash
gradle :app:assembleDebug
```
The generated APK will be located at:
```text
app/build/outputs/apk/debug/app-debug.apk
```

### Run Unit Tests
```bash
gradle :app:testDebugUnitTest --tests com.example.DataviewEngineTest
```

---

## Permissions & Privacy

VaultBlock runs **100% offline** with no external servers or broad storage permissions (`READ_EXTERNAL_STORAGE` / `MANAGE_EXTERNAL_STORAGE` are not requested). Access to external Obsidian folders is granted strictly by the user via Android's Storage Access Framework folder picker.
