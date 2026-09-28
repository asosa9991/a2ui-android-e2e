package com.example.a2uie2e

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.runtime.A2uiMessageParser
import androidx.a2ui.compose.ui.A2uiMessageProcessor
import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1
import androidx.a2ui.model.catalog.functions.A2uiLocaleProvider
import androidx.a2ui.model.catalog.functions.A2uiMessageFormatter
import androidx.a2ui.model.catalog.functions.A2uiUrlOpener
import androidx.a2ui.model.processor.A2uiMessageProcessor as CoreProcessor
import androidx.a2ui.model.protocol.A2uiClientEventMessage
import androidx.a2ui.model.processor.A2uiSurfaceModel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.a2ui.A2uiSurface
import androidx.compose.material3.a2ui.catalog.materialA2uiBasicCatalogV1
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import org.json.JSONObject

/** The host loopback as seen from the Android emulator. */
private const val AGENT = "http://10.0.2.2:8765"

/**
 * End-to-end A2UI: the agent streams a surface, the user edits it, and their
 * action goes back to the agent, which answers with the next surface.
 */
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { TransferFlow() } } }
  }
}

@Composable
private fun TransferFlow() {
  val catalog = remember {
    materialA2uiBasicCatalogV1(
      image = NoopImage,
      video = NoopVideo,
      audioPlayer = NoopAudio,
      urlOpener = NoopUrlOpener,
      messageFormatter = PassThroughMessageFormatter,
      localeProvider = A2uiLocaleProvider.Default,
    )
  }
  // One catalog: Material's components plus ours, under our own id.
  val processor: CoreProcessor = remember { A2uiMessageProcessor(listOf(extendedCatalog(catalog))) }
  val parser = remember { A2uiMessageParser() }

  // Drain the processor's inbound queue for as long as this screen is composed.
  LaunchedEffect(processor) { processor.collectMessages() }

  // 1. Ask the agent for the opening surface and feed it in as it streams.
  LaunchedEffect(processor) {
    stream("$AGENT/surface") { line -> processor.processMessage(parser.parse(line)) }
  }

  // 2. Every user action the renderer produces goes straight back to the agent,
  //    and the agent's reply streams in as the next surface.
  LaunchedEffect(processor) {
    processor.outboundEvents.collect { event ->
      if (event !is A2uiClientEventMessage) return@collect
      post("$AGENT/event", encodeEvent(event)) { line ->
        processor.processMessage(parser.parse(line))
      }
    }
  }

  val surfaces: List<A2uiSurfaceModel> by processor.activeSurfaces.collectAsState()
  val surface = surfaces.lastOrNull()

  Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
    if (surface == null) {
      Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Text("Waiting for the agent…", Modifier.padding(top = 12.dp),
             style = MaterialTheme.typography.bodyMedium)
      }
    } else {
      A2uiSurface(surface)
    }
  }
}

/** Serialise a client event into the wire shape the agent expects. */
private fun encodeEvent(e: A2uiClientEventMessage): String {
  val ctx = JSONObject()
  e.context.forEach { (k, v) -> ctx.put(k, v ?: JSONObject.NULL) }
  return JSONObject()
    .put("name", e.type)
    .put("surfaceId", e.surfaceId)
    .put("componentId", e.componentId)
    .put("timestamp", e.timestamp)
    .put("context", ctx)
    .put("dataModel", JSONObject((e.clientDataModel?.toPayloadMap() ?: emptyMap<String, Any>()) as Map<*, *>))
    .toString()
}

/** Read newline-delimited JSON, handing over each message the moment it lands. */
private suspend fun stream(url: String, onMessage: (String) -> Unit) =
  withContext(Dispatchers.IO) {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
      connectTimeout = 5000
      readTimeout = 60000
    }
    runCatching {
      conn.inputStream.bufferedReader().use { r: BufferedReader ->
        r.lineSequence().forEach { line ->
          if (line.isNotBlank()) onMessage(line)
        }
      }
    }
    conn.disconnect()
  }

private suspend fun post(url: String, body: String, onMessage: (String) -> Unit) =
  withContext(Dispatchers.IO) {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
      requestMethod = "POST"
      doOutput = true
      setRequestProperty("Content-Type", "application/json")
      connectTimeout = 5000
      readTimeout = 60000
    }
    runCatching {
      conn.outputStream.use { it.write(body.toByteArray()) }
      conn.inputStream.bufferedReader().use { r ->
        r.lineSequence().forEach { line -> if (line.isNotBlank()) onMessage(line) }
      }
    }
    conn.disconnect()
  }

// The catalog ships no image/video/audio loader by design; this payload uses
// none of those components, so no-op renderers are enough.
private object NoopImage : A2uiBasicCatalogV1.Image {
  @Composable
  override fun A2uiComponentScope.TypedContent(
    url: String,
    description: String?,
    fit: A2uiBasicCatalogV1.Image.Fit,
    variant: A2uiBasicCatalogV1.Image.Variant,
    accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
    modifier: Modifier,
  ) = Unit
}

private object NoopVideo : A2uiBasicCatalogV1.Video {
  @Composable
  override fun A2uiComponentScope.TypedContent(
    url: String,
    accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
    modifier: Modifier,
  ) = Unit
}

private object NoopAudio : A2uiBasicCatalogV1.AudioPlayer {
  @Composable
  override fun A2uiComponentScope.TypedContent(
    url: String,
    description: String?,
    accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
    modifier: Modifier,
  ) = Unit
}

private object NoopUrlOpener : A2uiUrlOpener {
  override fun openUrl(url: String) = Unit
}

private object PassThroughMessageFormatter : A2uiMessageFormatter {
  override fun format(pattern: String, locale: Locale, arguments: Map<String, Any>): String =
    pattern
}
