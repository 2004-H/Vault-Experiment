package com.example.domain.engine

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.model.DataviewJsRenderNode
import com.example.data.model.VaultNote
import com.example.data.model.VaultTask
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class DataviewJsEngine(private val context: Context) {

    private val mutex = Mutex()
    private var webView: WebView? = null
    private var initialized = false

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun ensureWebViewReady(): WebView = withContext(Dispatchers.Main) {
        val existing = webView
        if (existing != null && initialized) return@withContext existing

        val readySignal = CompletableDeferred<Unit>()
        val wv = existing ?: WebView(context.applicationContext).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.blockNetworkLoads = true
            settings.blockNetworkImage = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (!readySignal.isCompleted) {
                        readySignal.complete(Unit)
                    }
                }
            }
            loadDataWithBaseURL(
                "https://vaultblock.local/",
                "<!DOCTYPE html><html><head><meta charset=\"utf-8\"></head><body></body></html>",
                "text/html",
                "utf-8",
                null
            )
        }
        webView = wv
        if (!initialized) {
            readySignal.await()
            initialized = true
        }
        wv
    }

    suspend fun execute(
        script: String,
        allNotes: List<VaultNote>,
        currentNote: VaultNote?
    ): List<DataviewJsRenderNode> = mutex.withLock {
        val pagesJson = buildPagesJson(allNotes)
        val currentJson = if (currentNote != null) pageToJson(currentNote).toString() else "null"
        val taskLookup = allNotes.flatMap { it.tasks }.associateBy { it.id }

        val jsPayload = buildRunnerScript(
            pagesJson = pagesJson.toString(),
            currentJson = currentJson,
            userScript = script
        )

        val rawOutput = withContext(Dispatchers.Main) {
            val wv = ensureWebViewReady()
            suspendCoroutine<String> { cont ->
                wv.evaluateJavascript(jsPayload) { result ->
                    cont.resume(result ?: "null")
                }
            }
        }

        parseJsResult(rawOutput, taskLookup)
    }

    private fun buildPagesJson(notes: List<VaultNote>): JSONArray {
        val arr = JSONArray()
        notes.forEach { note -> arr.put(pageToJson(note)) }
        return arr
    }

    private fun pageToJson(note: VaultNote): JSONObject {
        val obj = JSONObject()
        val fileObj = JSONObject().apply {
            put("name", note.title)
            put("path", note.path)
            put("folder", note.folder)
            put("link", "[[${note.path}|${note.title}]]")
            put("tags", JSONArray(note.tags.map { "#$it" }))
            put("etags", JSONArray(note.tags.map { "#$it" }))
            put("outlinks", JSONArray(note.outlinks))
            put("mtime", note.lastModified)
            put("size", note.wordCount)
            val tasksArr = JSONArray()
            note.tasks.forEach { task ->
                tasksArr.put(
                    JSONObject().apply {
                        put("id", task.id)
                        put("path", task.notePath)
                        put("noteTitle", task.noteTitle)
                        put("line", task.lineIndex)
                        put("text", task.text)
                        put("completed", task.completed)
                        put("due", task.dueDate ?: JSONObject.NULL)
                        put("priority", task.priority ?: JSONObject.NULL)
                        put("tags", JSONArray(task.tags.map { "#$it" }))
                    }
                )
            }
            put("tasks", tasksArr)
        }
        obj.put("file", fileObj)
        note.properties.forEach { prop ->
            val key = prop.key
            when {
                prop.listItems.isNotEmpty() -> obj.put(key, JSONArray(prop.listItems))
                prop.value.equals("true", ignoreCase = true) -> obj.put(key, true)
                prop.value.equals("false", ignoreCase = true) -> obj.put(key, false)
                prop.value.toDoubleOrNull() != null -> obj.put(key, prop.value.toDouble())
                else -> obj.put(key, prop.value)
            }
        }
        return obj
    }

    private fun buildRunnerScript(
        pagesJson: String,
        currentJson: String,
        userScript: String
    ): String {
        val encodedScript = JSONObject.quote(userScript)
        return """
            (function() {
              const __pages = $pagesJson;
              const __current = $currentJson;
              const __nodes = [];

              function makeDataArray(arr) {
                const items = Array.isArray(arr) ? arr.slice() : [];
                const da = {
                  values: items,
                  get length() { return items.length; },
                  array() { return items.slice(); },
                  where(fn) { return makeDataArray(items.filter(fn)); },
                  filter(fn) { return makeDataArray(items.filter(fn)); },
                  map(fn) { return makeDataArray(items.map(fn)); },
                  flatMap(fn) {
                    const out = [];
                    for (const x of items) {
                      const r = fn(x);
                      if (r && Array.isArray(r.values)) out.push(...r.values);
                      else if (Array.isArray(r)) out.push(...r);
                      else if (r !== undefined && r !== null) out.push(r);
                    }
                    return makeDataArray(out);
                  },
                  sort(keyFn, dir) {
                    const desc = (dir || 'asc').toLowerCase() === 'desc';
                    const copy = items.slice().sort((a, b) => {
                      const va = keyFn(a);
                      const vb = keyFn(b);
                      if (va === vb) return 0;
                      if (va === undefined || va === null) return 1;
                      if (vb === undefined || vb === null) return -1;
                      const cmp = va < vb ? -1 : 1;
                      return desc ? -cmp : cmp;
                    });
                    return makeDataArray(copy);
                  },
                  groupBy(keyFn) {
                    const groups = new Map();
                    for (const x of items) {
                      const k = String(keyFn(x) ?? 'Other');
                      if (!groups.has(k)) groups.set(k, []);
                      groups.get(k).push(x);
                    }
                    const out = [];
                    for (const [key, rows] of groups.entries()) {
                      out.push({ key, rows: makeDataArray(rows) });
                    }
                    return makeDataArray(out);
                  },
                  limit(n) { return makeDataArray(items.slice(0, n)); },
                  slice(s, e) { return makeDataArray(items.slice(s, e)); },
                  first() { return items[0]; },
                  last() { return items[items.length - 1]; },
                  distinct(fn) {
                    const seen = new Set();
                    const out = [];
                    for (const x of items) {
                      const k = fn ? fn(x) : JSON.stringify(x);
                      if (!seen.has(k)) { seen.add(k); out.push(x); }
                    }
                    return makeDataArray(out);
                  },
                  sum() { return items.reduce((acc, v) => acc + (Number(v) || 0), 0); },
                  avg() { return items.length ? this.sum() / items.length : 0; },
                  forEach(fn) { items.forEach(fn); },
                  [Symbol.iterator]() { return items[Symbol.iterator](); }
                };
                return da;
              }

              function normalizeVal(v) {
                if (v === null || v === undefined) return "—";
                if (typeof v === "object") {
                  if (Array.isArray(v.values)) return v.values.map(normalizeVal).join(", ");
                  if (Array.isArray(v)) return v.map(normalizeVal).join(", ");
                  if (v.path && v.name) return "[[" + v.path + "|" + v.name + "]]";
                  if (v.file && v.file.link) return v.file.link;
                  if (v.iso) return v.iso;
                  return JSON.stringify(v);
                }
                return String(v);
              }

              function matchSource(page, source) {
                if (!source || source === "" || source === '""') return true;
                const orParts = source.split(/\s+OR\s+/i);
                return orParts.some(orPart => {
                  const andParts = orPart.split(/\s+AND\s+/i);
                  return andParts.every(rawTok => {
                    let tok = rawTok.trim();
                    let neg = false;
                    if (tok.startsWith("!") || tok.startsWith("-")) {
                      neg = true;
                      tok = tok.slice(1).trim();
                    }
                    let ok = true;
                    if (tok.startsWith("#")) {
                      const t = tok.toLowerCase();
                      ok = (page.file.tags || []).some(pt => pt.toLowerCase() === t || pt.toLowerCase().startsWith(t + "/"));
                    } else if (tok.startsWith('"') && tok.endsWith('"')) {
                      const folder = tok.slice(1, -1).replace(/^\/+|\/+$/g, "").toLowerCase();
                      const pf = (page.file.folder || "").toLowerCase();
                      const pp = (page.file.path || "").toLowerCase();
                      ok = folder === "" || pf === folder || pf.startsWith(folder + "/") || pp.startsWith(folder + "/");
                    }
                    return neg ? !ok : ok;
                  });
                });
              }

              const dv = {
                pages(source) {
                  return makeDataArray(__pages.filter(p => matchSource(p, source)));
                },
                page(path) {
                  const needle = String(path || "").toLowerCase();
                  return __pages.find(p => p.file.path.toLowerCase() === needle || p.file.name.toLowerCase() === needle) || null;
                },
                current() { return __current; },
                array(arr) { return makeDataArray(arr); },
                fileLink(path, embed, display) {
                  return "[[" + path + (display ? "|" + display : "") + "]]";
                },
                date(str) {
                  const s = (!str || str === "today" || str === "now") ? new Date().toISOString().slice(0, 10) : String(str);
                  return { iso: s, toString() { return s; }, valueOf() { return Date.parse(s) || 0; } };
                },
                duration(str) { return { raw: String(str), toString() { return String(str); } }; },
                header(level, text) {
                  __nodes.push({ kind: "header", level: Number(level) || 2, text: normalizeVal(text) });
                },
                paragraph(text) {
                  __nodes.push({ kind: "paragraph", text: normalizeVal(text) });
                },
                span(text) {
                  __nodes.push({ kind: "span", text: normalizeVal(text) });
                },
                el(tag, text) {
                  if (/^h[1-6]$/i.test(tag)) {
                    __nodes.push({ kind: "header", level: Number(tag.slice(1)), text: normalizeVal(text) });
                  } else {
                    __nodes.push({ kind: "paragraph", text: normalizeVal(text) });
                  }
                },
                list(items) {
                  const raw = (items && items.values) ? items.values : (Array.isArray(items) ? items : [items]);
                  __nodes.push({ kind: "list", items: raw.map(normalizeVal) });
                },
                table(headers, rows) {
                  const rawRows = (rows && rows.values) ? rows.values : (Array.isArray(rows) ? rows : []);
                  const normRows = rawRows.map(r => (Array.isArray(r) ? r : [r]).map(normalizeVal));
                  __nodes.push({ kind: "table", headers: (headers || []).map(normalizeVal), rows: normRows });
                },
                taskList(tasks) {
                  const raw = (tasks && tasks.values) ? tasks.values : (Array.isArray(tasks) ? tasks : []);
                  const ids = raw.map(t => t && t.id ? String(t.id) : "").filter(Boolean);
                  __nodes.push({ kind: "taskList", taskIds: ids });
                }
              };

              try {
                const __fn = new Function("dv", $encodedScript);
                __fn(dv);
                return JSON.stringify({ ok: true, nodes: __nodes });
              } catch (err) {
                return JSON.stringify({ ok: false, error: err && err.message ? err.message : String(err), nodes: __nodes });
              }
            })();
        """.trimIndent()
    }

    private fun parseJsResult(
        rawEvalOutput: String,
        taskLookup: Map<String, VaultTask>
    ): List<DataviewJsRenderNode> {
        val unquoted = decodeEvaluateJavascriptString(rawEvalOutput)
        return try {
            val root = JSONObject(unquoted)
            val nodes = mutableListOf<DataviewJsRenderNode>()
            val arr = root.optJSONArray("nodes") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                when (item.optString("kind")) {
                    "header" -> nodes.add(
                        DataviewJsRenderNode.JsHeader(
                            level = item.optInt("level", 2),
                            text = item.optString("text")
                        )
                    )
                    "paragraph" -> nodes.add(
                        DataviewJsRenderNode.JsParagraph(item.optString("text"))
                    )
                    "span" -> nodes.add(
                        DataviewJsRenderNode.JsSpan(item.optString("text"))
                    )
                    "list" -> {
                        val itemsArr = item.optJSONArray("items") ?: JSONArray()
                        val list = (0 until itemsArr.length()).map { itemsArr.optString(it) }
                        nodes.add(DataviewJsRenderNode.JsList(list))
                    }
                    "table" -> {
                        val hArr = item.optJSONArray("headers") ?: JSONArray()
                        val headers = (0 until hArr.length()).map { hArr.optString(it) }
                        val rArr = item.optJSONArray("rows") ?: JSONArray()
                        val rows = (0 until rArr.length()).map { rIdx ->
                            val rowArr = rArr.optJSONArray(rIdx) ?: JSONArray()
                            (0 until rowArr.length()).map { cIdx -> rowArr.optString(cIdx) }
                        }
                        nodes.add(DataviewJsRenderNode.JsTable(headers, rows))
                    }
                    "taskList" -> {
                        val idsArr = item.optJSONArray("taskIds") ?: JSONArray()
                        val tasks = (0 until idsArr.length()).mapNotNull { taskLookup[idsArr.optString(it)] }
                        nodes.add(DataviewJsRenderNode.JsTaskList(tasks))
                    }
                }
            }
            if (!root.optBoolean("ok", true)) {
                nodes.add(DataviewJsRenderNode.JsError(root.optString("error", "Unknown JS error")))
            }
            if (nodes.isEmpty()) {
                nodes.add(DataviewJsRenderNode.JsParagraph("Script executed with no output."))
            }
            nodes
        } catch (e: Exception) {
            listOf(DataviewJsRenderNode.JsError("DataviewJS bridge error: ${e.message ?: unquoted}"))
        }
    }

    private fun decodeEvaluateJavascriptString(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed == "null" || trimmed.isEmpty()) return "{}"
        return if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            JSONObject("{\"v\":$trimmed}").getString("v")
        } else {
            trimmed
        }
    }
}
