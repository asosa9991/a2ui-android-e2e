<!-- Also installed as an agent skill: ~/.claude/skills/a2ui-android/
     Any agent can load it by name; this copy is for humans and for
     agents working inside this repo. -->

# A2UI on Android (alpha)

The androidx A2UI libraries let an agent describe a screen as JSON and have a
Compose app render it natively. This is the working knowledge for adding that to
an app, verified against `1.0.0-alpha01`.

**There is no published API reference for these libraries.** Everything here was
read out of the artifacts or found by compiling. When a signature here disagrees
with the compiler, the compiler is right — see *Let the compiler tell you* below.

## Pick your task

| You are… | Go to |
| --- | --- |
| adding A2UI to an app for the first time | §1, §2, §3 |
| adding a component to an existing integration | §4 |
| a surface renders wrong or shows a red **Error** chip | §6 |
| writing the payloads an agent sends | `reference/wire-format.md` |
| looking for a type or function | `reference/api.md` |

---

## 1. Dependencies

Five artifacts across **three** Maven groups. All from Google's Maven, all pinned
to the same version — they release together and mismatched versions are not
supported.

```kotlin
implementation("androidx.a2ui:a2ui-model:1.0.0-alpha01")
implementation("androidx.a2ui:a2ui-engine:1.0.0-alpha01")
implementation("androidx.a2ui.compose:compose-runtime:1.0.0-alpha01")
implementation("androidx.a2ui.compose:compose-ui:1.0.0-alpha01")
implementation("androidx.compose.material3:material3-a2ui:1.0.0-alpha01")
```

> The `androidx.a2ui` group index lists only `a2ui-model` and `a2ui-engine`, which
> are the data layer and carry no Compose dependency. The renderer is in
> `androidx.a2ui.compose`, and a ready-made catalog in `androidx.compose.material3`.
> Reading only the first group leads to "Android has no renderer", which is wrong.

## 2. Build configuration

Three things that fail confusingly if you get them wrong:

```kotlin
plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.plugin.compose")   // and NOT kotlin.android
}

android {
  compileSdk = 37
  compileSdkMinor = 1        // 37.1 is TWO properties
}
```

- **AGP 9 rejects `org.jetbrains.kotlin.android`** with "no longer required since
  AGP 9.0". Remove it, and remove any `kotlin { jvmToolchain() }` block too.
- **`compileSdk` 37.1** is `compileSdk = 37` plus `compileSdkMinor = 1`.
- **Use the JDK bundled with Android Studio.** Newer JDKs break AGP.
- **Android Studio must be a 2026 release** to sync AGP 9.4.1. Older ones refuse
  to sync while `./gradlew` builds fine, so you lose indexing and completion with
  no obvious cause. Also commit a Gradle wrapper pinned to your build version, or
  Studio picks its own and fails with an unhelpful `NoClassDefFoundError`.

Verified working: AGP 9.4.1, Gradle 9.6.1, `minSdk` 24, Compose BOM 2026.09.00.

## 3. Rendering a surface

The whole integration. `collectMessages()` must keep running or nothing renders.

```kotlin
val catalog = remember {
  materialA2uiBasicCatalogV1(
    image = NoopImage, video = NoopVideo, audioPlayer = NoopAudio,
    urlOpener = NoopUrlOpener, messageFormatter = PassThroughMessageFormatter,
    localeProvider = A2uiLocaleProvider.Default,
  )
}
val processor = remember { A2uiMessageProcessor(listOf(catalog)) }
val parser = remember { A2uiMessageParser() }

LaunchedEffect(processor) { processor.collectMessages() }          // required
LaunchedEffect(processor) { messages.forEach { processor.processMessage(parser.parse(it)) } }

val surfaces by processor.activeSurfaces.collectAsState()
surfaces.lastOrNull()?.let { A2uiSurface(it) }
```

`materialA2uiBasicCatalogV1()` takes **six parameters with no defaults** — the
library deliberately ships no image, video or audio loader. If your payload does
not use those components, no-op implementations are enough. The concrete
`MaterialA2uiBasicCatalogV1Image/Video/AudioPlayer` classes are `internal`;
implement the public `A2uiBasicCatalogV1.Image` / `.Video` / `.AudioPlayer`
interfaces instead. Full stubs in `reference/recipes.md`.

To send user actions back to an agent, collect the outbound flow:

```kotlin
LaunchedEffect(processor) {
  processor.outboundEvents.collect { event ->
    if (event is A2uiClientEventMessage) send(event)   // your transport
  }
}
```

## 4. Adding a custom component

Implement `A2uiComponent`. The agent then sends `{"component": "YourName", ...}`.

```kotlin
object PositionRow : A2uiComponent {
  private val Symbol = A2uiProperty.dynamicString("symbol", true, "Ticker.")
  private val Kind = A2uiProperty.dynamicString("kind", false, "equity | option")

  override val name = "PositionRow"
  override val description = "One holding, rendered by instrument kind."
  override val properties = listOf(Symbol, Kind)

  @Composable
  override fun A2uiComponentScope.Content(          // an EXTENSION on the scope
    properties: A2uiComponentProperties,
    modifier: Modifier,
  ) {
    val symbol = properties.bind(Symbol) ?: ""      // bind() is on the PROPERTIES
    val kind = properties.bind(Kind) ?: "equity"
    // ...ordinary Compose, branching on kind...
  }
}
```

Four rules that are not guessable:

1. **The concrete property classes are `internal`.** Build them through
   `A2uiProperty.dynamicString/…` factories. Full list in `reference/api.md`.
2. **Static vs dynamic decides whether it can be bound.** Only a `dynamic*`
   property accepts `{"path": "/x"}` and recomposes when the data model changes.
   Choosing static for something an agent binds gives a component that renders
   once and never updates.
3. **`Content` is an extension on `A2uiComponentScope`**, not a plain method.
4. **`bind` is an extension on the properties**, not on the scope:
   `properties.bind(Symbol)`. It returns `T?` even for a required property —
   `required` is a schema statement, not a runtime guarantee. Default, don't assert.

### Registering it — read this before you debug

**v0.9.1 allows exactly one `catalogId` per surface.** A custom component cannot
live in a second catalog handed to the processor alongside Material's: the
surface resolves only against the catalog it names, and anything missing renders
as a red **Error** chip with nothing in logcat.

Build one catalog that extends Material's, and have the agent name **your** id:

```kotlin
const val CATALOG_ID = "yourcompany.com:yourcatalog-v1"

fun extendedCatalog(base: A2uiCatalog) = A2uiCatalog(
  CATALOG_ID, base.components + PositionRow, base.functions, base.themeSchema,
)

val processor = remember { A2uiMessageProcessor(listOf(extendedCatalog(catalog))) }
```

Keep `base.components +` — dropping it makes every `Text` and `Column` fail the
same silent way. v1.0 adds mixable catalogs and removes this constraint.

## 5. Let the compiler tell you

These are alpha signatures with no reference site, and they move. The fastest
lookup is a build:

```
e: 'Content' overrides nothing. Potential signatures for overriding:
   fun A2uiComponentScope.Content(properties: A2uiComponentProperties,
                                  modifier: Modifier): Unit
```

Write the shape you expect, run `./gradlew assembleDebug`, and copy what it
prints. In a synced 2026 Android Studio, completion on a `processor.` or a
`properties.` is the same reference, live.

## 6. When a surface renders wrong

Most failures are silent — a wrong screen, not an exception.

| Symptom | Cause |
| --- | --- |
| Red **Error** chip where a component should be | The named catalog does not contain that component, **or** an enum value is not one the catalog allows |
| Screen never updates; nothing in logcat | `collectMessages()` is not running |
| Form renders but every field is empty | `updateDataModel` uses key **`value`**, not `contents` |
| Component renders once, never reacts | The property is static; it must be `dynamic*` to bind |
| Stuck on a loading state | The surface's `catalogId` is not one the client registered |
| `NoClassDefFoundError` on Gradle sync | No Gradle wrapper; Studio picked its own version |
| Studio won't sync but `./gradlew` builds | Studio predates AGP 9.4.1; use a 2026 release |

Check enum values against the catalog before suspecting your code:

- `Text.variant` — `h1 h2 h3 h4 h5 caption body` (**not** `bodyMedium`)
- `Button.variant` — `default primary borderless` (**not** `text`, `secondary`)
- `Icon.name` — camelCase, e.g. `check` (**not** `check_circle`)

## 7. Verifying a change

```bash
./gradlew assembleDebug                                  # compiles
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n <pkg>/.MainActivity
adb exec-out screencap -p > /tmp/s.png                   # LOOK at it
adb shell uiautomator dump /sdcard/u.xml && adb shell cat /sdcard/u.xml \
  | tr '>' '\n' | grep -oE 'text="[^"]+"' | head          # what rendered
```

A successful build proves nothing about rendering — these failures are silent, so
always read the screen. `uiautomator` text is the fastest check: if you expected
a symbol and see `text="Error"`, go to §6.

## Reference

- `reference/api.md` — the public API surface, package by package
- `reference/wire-format.md` — message types, binding, templates, actions
- `reference/recipes.md` — copy-paste working code
