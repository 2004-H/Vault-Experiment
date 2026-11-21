package com.example.data.sync

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

data class VaultFileEntry(
    val path: String,
    val content: String,
    val lastModified: Long,
    val documentUri: Uri? = null
)

class LocalVaultProvider(private val context: Context) {

    private val internalVaultDir: File
        get() = File(context.filesDir, "obsidian_vault").apply {
            if (!exists()) mkdirs()
        }

    fun seedStarterVaultIfNeeded() {
        val existing = internalVaultDir.walkTopDown().filter { it.isFile && it.extension == "md" }.toList()
        if (existing.isNotEmpty()) return

        StarterVaultTemplates.starterNotes().forEach { (relativePath, content) ->
            val target = File(internalVaultDir, relativePath)
            target.parentFile?.mkdirs()
            target.writeText(content)
        }
    }

    fun resetStarterVault() {
        internalVaultDir.deleteRecursively()
        internalVaultDir.mkdirs()
        StarterVaultTemplates.starterNotes().forEach { (relativePath, content) ->
            val target = File(internalVaultDir, relativePath)
            target.parentFile?.mkdirs()
            target.writeText(content)
        }
    }

    fun readAllFiles(linkedTreeUri: String?): List<VaultFileEntry> {
        return if (linkedTreeUri.isNullOrBlank()) {
            readInternalVaultFiles()
        } else {
            readSafVaultFiles(Uri.parse(linkedTreeUri))
        }
    }

    fun writeFile(linkedTreeUri: String?, relativePath: String, content: String): Long {
        val now = System.currentTimeMillis()
        if (linkedTreeUri.isNullOrBlank()) {
            val file = File(internalVaultDir, relativePath)
            file.parentFile?.mkdirs()
            file.writeText(content)
            file.setLastModified(now)
            return file.lastModified()
        }

        val treeUri = Uri.parse(linkedTreeUri)
        val existingDocUri = findSafDocumentUri(treeUri, relativePath)
        val targetUri = existingDocUri ?: createSafDocumentUri(treeUri, relativePath)
        if (targetUri != null) {
            context.contentResolver.openOutputStream(targetUri, "wt")?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
                out.flush()
            }
        }
        return now
    }

    fun deleteFile(linkedTreeUri: String?, relativePath: String) {
        if (linkedTreeUri.isNullOrBlank()) {
            File(internalVaultDir, relativePath).delete()
            return
        }
        val treeUri = Uri.parse(linkedTreeUri)
        val docUri = findSafDocumentUri(treeUri, relativePath)
        if (docUri != null) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, docUri) }
        }
    }

    private fun readInternalVaultFiles(): List<VaultFileEntry> {
        val root = internalVaultDir
        return root.walkTopDown()
            .filter { file ->
                file.isFile &&
                    file.extension.equals("md", ignoreCase = true) &&
                    !file.relativeTo(root).path.startsWith(".")
            }
            .map { file ->
                val rel = file.relativeTo(root).path.replace(File.separatorChar, '/')
                VaultFileEntry(
                    path = rel,
                    content = file.readText(),
                    lastModified = file.lastModified()
                )
            }
            .toList()
    }

    private fun readSafVaultFiles(treeUri: Uri): List<VaultFileEntry> {
        val results = mutableListOf<VaultFileEntry>()
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        scanSafDirectory(treeUri, rootDocId, "", results)
        return results
    }

    private fun scanSafDirectory(
        treeUri: Uri,
        parentDocId: String,
        prefix: String,
        results: MutableList<VaultFileEntry>
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val modCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idCol)
                    val name = cursor.getString(nameCol) ?: continue
                    val mime = cursor.getString(mimeCol) ?: ""
                    val lastMod = cursor.getLong(modCol)
                    if (name.startsWith(".")) continue

                    val relPath = if (prefix.isEmpty()) name else "$prefix/$name"
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        scanSafDirectory(treeUri, docId, relPath, results)
                    } else if (name.endsWith(".md", ignoreCase = true)) {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        val text = context.contentResolver.openInputStream(docUri)?.use { stream ->
                            stream.bufferedReader(Charsets.UTF_8).readText()
                        } ?: ""
                        results.add(
                            VaultFileEntry(
                                path = relPath,
                                content = text,
                                lastModified = lastMod,
                                documentUri = docUri
                            )
                        )
                    }
                }
            }
        }
    }

    private fun findSafDocumentUri(treeUri: Uri, relativePath: String): Uri? {
        val segments = relativePath.split("/").filter { it.isNotBlank() }
        var currentDocId = DocumentsContract.getTreeDocumentId(treeUri)
        for (idx in segments.indices) {
            val targetName = segments[idx]
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, currentDocId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            )
            var foundId: String? = null
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameCol) == targetName) {
                        foundId = cursor.getString(idCol)
                        break
                    }
                }
            }
            if (foundId == null) return null
            if (idx == segments.lastIndex) {
                return DocumentsContract.buildDocumentUriUsingTree(treeUri, foundId!!)
            }
            currentDocId = foundId!!
        }
        return null
    }

    private fun createSafDocumentUri(treeUri: Uri, relativePath: String): Uri? {
        val segments = relativePath.split("/").filter { it.isNotBlank() }
        var parentDocId = DocumentsContract.getTreeDocumentId(treeUri)
        for (idx in 0 until segments.lastIndex) {
            val folderName = segments[idx]
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
            val existing = findChildDocId(treeUri, parentDocId, folderName)
            parentDocId = existing ?: run {
                val created = DocumentsContract.createDocument(
                    context.contentResolver,
                    parentUri,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    folderName
                ) ?: return null
                DocumentsContract.getDocumentId(created)
            }
        }
        val fileName = segments.lastOrNull() ?: return null
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
        return DocumentsContract.createDocument(
            context.contentResolver,
            parentUri,
            "text/markdown",
            fileName
        )
    }

    private fun findChildDocId(treeUri: Uri, parentDocId: String, name: String): String? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                if (cursor.getString(nameCol) == name) return cursor.getString(idCol)
            }
        }
        return null
    }
}

object StarterVaultTemplates {
    fun starterNotes(): Map<String, String> = mapOf(
        "Dashboards/Command Center.md" to """
            ---
            status: active
            pinned: true
            priority: high
            tags: [dashboard, obsidian, dataview]
            updated: 2026-11-21
            ---

            # Command Center

            > [!tip] Mobile Dataview Blocks
            > Every property above can be updated in one tap. Edits immediately write back to your local Markdown files and refresh live Dataview & DataviewJS blocks below.

            ```dataview
            TABLE status AS "Status", priority AS "Priority", rating AS "Rating", due AS "Due"
            FROM #project
            SORT priority DESC, due ASC
            ```

            ```dataviewjs
            const active = dv.pages("#project").where(p => p.status === "active");
            dv.header(3, "Live JS Sprint Pulse (" + active.length + " active)");
            dv.table(
              ["Project", "Progress", "Owner"],
              active.sort(p => p.progress, "desc").map(p => [p.file.link, (p.progress || 0) + "%", p.owner || "Team"])
            );
            ```

            - [ ] Review [[Project Atlas]] mobile sync latency [due:: 2026-11-22] [priority:: high] #sprint
            - [x] Verify offline Room index hydration [due:: 2026-11-20] #architecture
        """.trimIndent() + "\n",

        "Projects/Project Atlas.md" to """
            ---
            status: active
            priority: high
            progress: 78
            rating: 5
            due: 2026-11-28
            owner: Alex
            pinned: true
            tags: [project, mobile, sync]
            ---

            # Project Atlas

            Offline-first Markdown sync engine with instant block rendering and zero-latency property mutations.

            ```dataview
            TASK
            FROM "Projects/Project Atlas.md"
            WHERE !completed
            ```

            - [ ] Finalize SAF conflict resolution merge [due:: 2026-11-23] [priority:: high] #sync
            - [ ] Benchmark 1,000-note Dataview query cache [due:: 2026-11-25] [priority:: medium] #perf
            - [x] Build DataviewJS WebView runtime bridge [due:: 2026-11-19] #done
        """.trimIndent() + "\n",

        "Projects/Q4 Launch Sprint.md" to """
            ---
            status: review
            priority: high
            progress: 92
            rating: 4
            due: 2026-11-24
            owner: Maya
            tags: [project, launch, sprint]
            ---

            # Q4 Launch Sprint

            > [!note] Release Readiness
            > Waiting on final metadata schema validation and adaptive tablet layout QA.

            - [ ] Publish release notes to [[Command Center]] [due:: 2026-11-22] [priority:: high] #launch
            - [ ] Verify dark & light theme contrast tokens [due:: 2026-11-23] #design
            - [x] Ship adaptive NavigationRail for foldables #ui
        """.trimIndent() + "\n",

        "Projects/Design System Spec.md" to """
            ---
            status: active
            priority: medium
            progress: 64
            rating: 5
            due: 2026-12-03
            owner: Jordan
            tags: [project, design, compose]
            ---

            # Design System Spec

            Material 3 semantic color tokens, tactile metadata chips, and JetBrains Mono query blocks.

            - [ ] Audit 48dp touch targets across table cells [due:: 2026-11-26] [priority:: medium] #a11y
            - [x] Bundle Plus Jakarta Sans & JetBrains Mono #typography
        """.trimIndent() + "\n",

        "Library/Reading List.md" to """
            ---
            status: active
            pinned: true
            tags: [dashboard, reading, knowledge]
            ---

            # Reading List Matrix

            ```dataview
            TABLE author AS "Author", rating AS "Rating", status AS "Status", genre AS "Genre"
            FROM #book
            SORT rating DESC
            ```

            ```dataviewjs
            const books = dv.pages("#book");
            const avg = books.map(b => b.rating || 0).avg().toFixed(1);
            dv.paragraph("Indexed " + books.length + " books · Average rating: " + avg + " / 5.0");
            ```
        """.trimIndent() + "\n",

        "Library/Designing Data-Intensive Apps.md" to """
            ---
            author: Martin Kleppmann
            status: done
            rating: 5
            genre: Systems
            completed: true
            tags: [book, architecture, distributed]
            ---

            # Designing Data-Intensive Apps

            Essential patterns for local-first storage engines, write-ahead logs, and conflict resolution.

            - [x] Summarize Chapter 5 replication notes [due:: 2026-11-15] #notes
        """.trimIndent() + "\n",

        "Library/How to Take Smart Notes.md" to """
            ---
            author: Sonke Ahrens
            status: active
            rating: 4
            genre: PKM
            completed: false
            tags: [book, pkm, obsidian]
            ---

            # How to Take Smart Notes

            Atomic evergreen blocks connected via wikilinks [[Project Atlas]] and [[Command Center]].

            - [ ] Extract 3 permanent notes on slip-box indexing [due:: 2026-11-27] [priority:: low] #pkm
        """.trimIndent() + "\n",

        "Journal/Weekly Review.md" to """
            ---
            status: active
            date: 2026-11-21
            energy: 4
            tags: [journal, review]
            ---

            # Weekly Review — 2026-11-21

            ```dataview
            CALENDAR file.mtime
            ```

            ```dataview
            LIST status
            FROM #project OR #book
            WHERE status = "active"
            SORT file.name ASC
            ```

            - [ ] Archive completed sprint items before Monday [due:: 2026-11-24] [priority:: medium] #weekly
        """.trimIndent() + "\n"
    )
}
