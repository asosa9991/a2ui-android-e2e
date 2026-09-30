package com.example.a2uie2e

import androidx.a2ui.compose.runtime.A2uiMessageParser
import androidx.a2ui.compose.ui.A2uiMessageProcessor
import androidx.a2ui.compose.ui.A2uiCatalog
import androidx.a2ui.model.processor.A2uiMessageProcessor as CoreProcessor
import androidx.a2ui.model.processor.A2uiSurfaceModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.a2ui.A2uiSurface
import org.json.JSONArray
import org.json.JSONObject

/**
 * Paste A2UI surface JSON, render it.
 *
 * Accepts any of the shapes payloads turn up in:
 *   - newline-delimited JSON, one message per line
 *   - a JSON array of messages
 *   - the gallery format, {"messages": [ ... ]}
 *   - a single message object
 */
@Composable
fun PlaygroundScreen(baseCatalog: A2uiCatalog, onBack: () -> Unit) {
  val context = LocalContext.current
  var input by remember { mutableStateOf(STARTER) }
  var error by remember { mutableStateOf<String?>(null) }
  var editorOpen by remember { mutableStateOf(true) }
  var samplesOpen by remember { mutableStateOf(false) }

  // Rendering a new payload needs a clean processor: there is no reset, and a
  // second createSurface for a live surfaceId is an error. Bumping the
  // generation rebuilds everything below it.
  var generation by remember { mutableIntStateOf(0) }
  var messages by remember { mutableStateOf<List<String>>(emptyList()) }
  var catalogId by remember { mutableStateOf(POSITIONS_CATALOG_ID) }

  val samples = remember { runCatching { context.assets.list("samples")?.toList() }
    .getOrNull().orEmpty().sorted() }

  Column(Modifier.fillMaxSize().padding(12.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
      Text("A2UI Playground", style = MaterialTheme.typography.titleLarge)
      Row {
        Box {
          TextButton(onClick = { samplesOpen = true }) { Text("Samples") }
          DropdownMenu(expanded = samplesOpen, onDismissRequest = { samplesOpen = false }) {
            if (samples.isEmpty()) {
              DropdownMenuItem(text = { Text("none bundled") }, onClick = {})
            }
            samples.forEach { name ->
              DropdownMenuItem(text = { Text(name.removeSuffix(".json")) }, onClick = {
                samplesOpen = false
                input = runCatching {
                  context.assets.open("samples/$name").bufferedReader().use { it.readText() }
                }.getOrElse { "// could not read $name: ${it.message}" }
                error = null
              })
            }
          }
        }
        TextButton(onClick = onBack) { Text("Agent") }
      }
    }

    TextButton(onClick = { editorOpen = !editorOpen }) {
      Text(if (editorOpen) "Hide editor" else "Show editor")
    }

    if (editorOpen) {
      OutlinedTextField(
        value = input,
        onValueChange = { input = it; error = null },
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 260.dp),
        label = { Text("Surface JSON") },
        textStyle = MaterialTheme.typography.bodySmall.copy(
          fontFamily = FontFamily.Monospace, fontSize = 11.sp),
      )
      Row(Modifier.fillMaxWidth().padding(top = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {
          when (val parsed = parsePayload(input)) {
            is ParseResult.Failure -> error = parsed.reason
            is ParseResult.Success -> {
              error = null
              catalogId = parsed.catalogId ?: POSITIONS_CATALOG_ID
              messages = parsed.messages
              generation += 1
              editorOpen = false
            }
          }
        }) { Text("Render") }
        TextButton(onClick = {
          input = ""; messages = emptyList(); error = null; generation += 1
        }) { Text("Clear") }
      }
    }

    error?.let {
      Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(it, Modifier.padding(12.dp),
             style = MaterialTheme.typography.bodySmall,
             color = MaterialTheme.colorScheme.error)
      }
    }

    HorizontalDivider(Modifier.padding(vertical = 10.dp))

    if (messages.isEmpty()) {
      Text("Paste a payload or pick a sample, then Render.",
           style = MaterialTheme.typography.bodyMedium,
           color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
      // keyed on generation so each Render gets a fresh processor
      RenderedSurface(generation, messages, baseCatalog, catalogId)
    }
  }
}

@Composable
private fun RenderedSurface(
  generation: Int,
  messages: List<String>,
  baseCatalog: A2uiCatalog,
  catalogId: String,
) {
  // The payload names its own catalogId, and a surface resolves only against
  // the catalog with that id. So build the catalog under whatever id this
  // payload asked for — that is what lets an arbitrary pasted payload render.
  val processor: CoreProcessor = remember(generation) {
    A2uiMessageProcessor(listOf(catalogAs(catalogId, baseCatalog)))
  }
  val parser = remember(generation) { A2uiMessageParser() }

  LaunchedEffect(processor) { processor.collectMessages() }
  LaunchedEffect(processor) {
    messages.forEach { processor.processMessage(parser.parse(it)) }
  }

  val surfaces: List<A2uiSurfaceModel> by processor.activeSurfaces.collectAsState()
  val surface = surfaces.lastOrNull()

  Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
    if (surface == null) {
      Text("No surface yet — the payload may not contain a createSurface.",
           style = MaterialTheme.typography.bodyMedium)
    } else {
      A2uiSurface(surface)
    }
  }
}

// ─────────────────────────────── payload parsing


/** Split concatenated JSON objects by brace depth, ignoring braces in strings. */
private fun splitObjects(text: String): List<String> {
  val parts = mutableListOf<String>()
  var depth = 0
  var start = -1
  var inString = false
  var escaped = false
  for (i in text.indices) {
    val c = text[i]
    when {
      escaped -> escaped = false
      c == '\\' && inString -> escaped = true
      c == '"' -> inString = !inString
      inString -> {}
      c == '{' -> { if (depth == 0) start = i; depth++ }
      c == '}' -> {
        depth--
        if (depth == 0 && start >= 0) { parts += text.substring(start, i + 1); start = -1 }
      }
    }
  }
  return parts
}

sealed interface ParseResult {
  data class Success(val messages: List<String>, val catalogId: String?) : ParseResult
  data class Failure(val reason: String) : ParseResult
}

/** Normalise the shapes a payload turns up in into one message per string. */
fun parsePayload(raw: String): ParseResult {
  val text = raw.trim()
  if (text.isEmpty()) return ParseResult.Failure("Nothing to render.")

  val out = mutableListOf<String>()
  try {
    when {
      text.startsWith("[") -> {
        val arr = JSONArray(text)
        for (i in 0 until arr.length()) out += arr.getJSONObject(i).toString()
      }
      text.startsWith("{") && JSONObject(text).has("messages") -> {
        val arr = JSONObject(text).getJSONArray("messages")
        for (i in 0 until arr.length()) out += arr.getJSONObject(i).toString()
      }
      // One or more objects back to back. Splitting on newlines breaks the
      // moment someone pastes pretty-printed JSON, which is most of the time,
      // so walk brace depth instead. Handles NDJSON and pretty-printed alike.
      else -> splitObjects(text).forEach { out += JSONObject(it).toString() }
    }
  } catch (e: Exception) {
    return ParseResult.Failure("Could not parse JSON: ${e.message}")
  }

  if (out.isEmpty()) return ParseResult.Failure("No messages found.")

  val catalogId = out.firstNotNullOfOrNull { m ->
    runCatching { JSONObject(m).getJSONObject("createSurface").getString("catalogId") }
      .getOrNull()
  }
  val hasSurface = out.any { runCatching { JSONObject(it).has("createSurface") }.getOrDefault(false) }
  if (!hasSurface) {
    return ParseResult.Failure(
      "No createSurface message. A payload needs one before updateComponents.")
  }
  return ParseResult.Success(out, catalogId)
}

private val STARTER = """
{"version":"v0.9","createSurface":{"surfaceId":"demo","catalogId":"example.com:positions-v1","sendDataModel":true}}
{"version":"v0.9","updateComponents":{"surfaceId":"demo","components":[
  {"id":"root","component":"Column","children":["t","s"],"justify":"start","align":"stretch"},
  {"id":"t","component":"Text","text":"Hello from a pasted payload","variant":"h2"},
  {"id":"s","component":"Text","text":{"path":"/subtitle"}}]}}
{"version":"v0.9","updateDataModel":{"surfaceId":"demo","value":{"subtitle":"Edit the JSON above and hit Render."}}}
""".trim()
