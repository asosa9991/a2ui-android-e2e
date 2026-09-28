# Recipes

Working code, taken from a running app.

## The media stubs the Material catalog demands

Required because the library ships no image/video/audio loader. If your payload
does not use those components, these are enough. The concrete Material classes
are `internal`, so implement the public interfaces.

```kotlin
private object NoopImage : A2uiBasicCatalogV1.Image {
  @Composable
  override fun A2uiComponentScope.TypedContent(
    url: String, description: String?,
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
    url: String, description: String?,
    accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
    modifier: Modifier,
  ) = Unit
}

private object NoopUrlOpener : A2uiUrlOpener {
  override fun openUrl(url: String) = Unit
}

private object PassThroughMessageFormatter : A2uiMessageFormatter {
  override fun format(pattern: String, locale: Locale,
                      arguments: Map<String, Any>): String = pattern
}
```

`TypedContent` is an extension on `A2uiComponentScope` and `accessibility` is
nullable. If the signature drifts, let the compiler print it.

## Talking to an agent over HTTP

Stream a surface in, send every action back out. Two loops is the whole thing.

```kotlin
LaunchedEffect(processor) { processor.collectMessages() }

LaunchedEffect(processor) {                       // 1. ask for a surface
  stream("$AGENT/surface") { line -> processor.processMessage(parser.parse(line)) }
}

LaunchedEffect(processor) {                       // 2. send actions back
  processor.outboundEvents.collect { event ->
    if (event !is A2uiClientEventMessage) return@collect
    post("$AGENT/event", encodeEvent(event)) { line ->
      processor.processMessage(parser.parse(line))
    }
  }
}
```

Serialising an outbound event:

```kotlin
private fun encodeEvent(e: A2uiClientEventMessage): String {
  val ctx = JSONObject()
  e.context.forEach { (k, v) -> ctx.put(k, v ?: JSONObject.NULL) }
  return JSONObject()
    .put("name", e.type).put("surfaceId", e.surfaceId)
    .put("componentId", e.componentId).put("timestamp", e.timestamp)
    .put("context", ctx)
    .put("dataModel", JSONObject(
      (e.clientDataModel?.toPayloadMap() ?: emptyMap<String, Any>()) as Map<*, *>))
    .toString()
}
```

`clientDataModel` is nullable. The emulator reaches the host at `10.0.2.2`, and
plain HTTP needs `android:usesCleartextTraffic="true"` plus the `INTERNET`
permission.

## Manifest

A Compose-only app has no XML theme resources, so use a framework theme:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<application android:theme="@android:style/Theme.Material.Light.NoActionBar"
             android:usesCleartextTraffic="true">
```

Referencing `@style/Theme.Material3.*` without the Material XML library fails at
AAPT with "resource style/... not found".

## Streaming so the user sees structure first

Send the components in several `updateComponents` messages, then the data model.
The adjacency list makes a partial tree legal, so each piece draws as it lands.

```python
[ createSurface(sid),
  updateComponents(sid, comps[:4]),
  updateComponents(sid, comps[4:9]),
  updateComponents(sid, comps[9:]),
  updateDataModel(sid, values) ]
```

## Driving the app to verify a change

```bash
adb shell uiautomator dump /sdcard/u.xml
adb shell cat /sdcard/u.xml | tr '>' '\n' | grep -F 'Button label' \
  | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -1 \
  | sed 's/[^0-9]/ /g' | awk '{print int(($1+$3)/2), int(($2+$4)/2)}'
# -> tap coordinates, then: adb shell input tap X Y
```

Resolve coordinates from the hierarchy rather than hardcoding them — the layout
moves as data changes.

## Reference implementations

Two complete apps, both runnable:

- `a2ui-android-test` — renders a static payload from `assets/`
- `a2ui-android-e2e` — a Compose client and a Python agent with a full user-action
  round trip, plus a custom component (`PositionRow.kt`) and a variable-data demo
  (`server/variable.py`, run with `python3 server.py --variable`)
