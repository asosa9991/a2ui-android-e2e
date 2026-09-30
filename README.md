# A2UI on Android, end to end

A Jetpack Compose client and a Python agent that talk to each other over the
A2UI protocol. The agent streams a surface, the user edits it and taps, the
action goes back to the agent, and the agent answers with the next surface.

The sibling project `a2ui-android-test` renders a static payload from assets.
This one closes the loop.

## The flow

Three surfaces, two round trips, no layout code on the client:

1. **Transfer form** — streamed as four messages so it renders progressively
2. **Review** — the agent fills a template with the values the client sent back
3. **Receipt** — composed at confirm time, with a fresh reference number

Tick "repeat this transfer every month" and the review screen comes back reading
`Frequency: Monthly`. That value travelled to the agent inside the client data
model and returned inside its reply.

## Running it

```bash
# 1. the agent (listens on :8765; the emulator reaches the host at 10.0.2.2)
python3 server/server.py

# 2. the app
./gradlew installDebug
adb shell am start -n com.example.a2uie2e/.MainActivity
```

## How the client works

Two loops, and that is the whole integration:

```kotlin
// stream a surface in
LaunchedEffect(processor) {
  stream("$AGENT/surface") { line -> processor.processMessage(parser.parse(line)) }
}

// send every user action back out
LaunchedEffect(processor) {
  processor.outboundEvents.collect { event ->
    post("$AGENT/event", encodeEvent(event)) { line ->
      processor.processMessage(parser.parse(line))
    }
  }
}
```

`outboundEvents` is a `Flow<A2uiClientToServerMessage>` on the processor. It is
the hook for every user action and it is not in the documentation — it was found
by disassembling the AAR.

## Why the agent's surfaces are pre-authored

Every surface the agent can send is written out in `server/server.py`. Nothing is
generated when a request arrives; the agent picks a template and fills it from
data the client sent. Structure is fixed and reviewable, only values move — which
is what a regulated client needs.

## Wire-format traps

None of these throw. They render a wrong screen and leave you reading your own code.

| Trap | Symptom |
| --- | --- |
| `updateDataModel` takes **`value`**, not `contents` | Form renders, every field empty |
| `Text.variant` is `h1`–`h5`, `caption`, `body` | `bodyMedium` draws a red **Error** chip |
| `Button.variant` is `default`, `primary`, `borderless` | `text` / `secondary` are invalid |
| `Icon.name` is camelCase | `check` works, `check_circle` does not |

Build traps: AGP 9 rejects `org.jetbrains.kotlin.android`; `compileSdk` 37.1 is
`compileSdk = 37` plus `compileSdkMinor = 1`; and Android Studio must be a 2026
release or Gradle sync refuses AGP 9.4.1 while the command line builds fine.

## The playground

The app has two screens. The agent flow above, and a **playground** that renders
pasted A2UI JSON — useful for checking a payload without wiring up an agent.

- Accepts NDJSON, a JSON array, `{"messages": [ … ]}`, or a single message, and
  pretty-printed or not (it splits on brace depth, not newlines).
- Six bundled samples under `app/src/main/assets/samples/`, including one that
  exercises the custom `PositionRow`.
- It **adopts whatever `catalogId` the payload names**, building our component
  set under that id. Without that, a payload written against the spec's basic
  catalog would resolve nothing and render error chips — see the one-catalog-per
  -surface rule in `docs/A2UI-ANDROID.md`.

## Adding A2UI to another app

`docs/A2UI-ANDROID.md` is a task-oriented onboarding guide for the alpha
libraries: dependencies, the build traps, the render path, writing a custom
component, the wire format, and a symptom-to-cause table for the failures that
render a wrong screen instead of throwing. `docs/reference/` carries the API
surface, the wire format and copy-paste recipes.

It is also installed as an agent skill at `~/.claude/skills/a2ui-android/`, so an
agent can load it by name when asked to add or extend A2UI in any project.

## demo/

Narrated videos built from this project with the
[demo-video](https://github.com/asosa9991/claude-skill-demo-video) skill, each
with its storyboard, VHS tapes and captions:

- `a2ui-android-e2e.mp4` — the round trip, 2m13s
- `dev-guide/` — using the alpha libraries, 8m50s
- `deep-dive/` — every API in the alpha, 15m02s

Rebuild any of them without re-recording:

```bash
cd demo/deep-dive
python3 ~/.claude/skills/demo-video/scripts/demo_video.py build storyboard.json
```
