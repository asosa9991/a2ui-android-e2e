# API surface (1.0.0-alpha01)

273 public types across 20 packages. These are the ones you touch.

## The processor — `androidx.a2ui.model.processor`

```kotlin
interface A2uiMessageProcessor {
    val activeSurfaces: StateFlow<List<A2uiSurfaceModel>>
    val outboundEvents: Flow<A2uiClientToServerMessage>   // every user action
    fun processMessage(msg: A2uiServerToClientMessage)
    fun processError(err: A2uiClientErrorMessage)
    suspend fun collectMessages()                          // drains the queue
}

interface A2uiSurfaceModel { val id: String }

interface A2uiActionInterceptor {
    suspend fun onInterceptAction(action: A2uiUserAction): A2uiUserAction
}
```

Built by the Compose factory in `androidx.a2ui.compose.ui`:

```kotlin
fun A2uiMessageProcessor(
    catalogs: List<A2uiCatalog>,
    interceptors: List<A2uiActionInterceptor> = emptyList(),
): A2uiMessageProcessor
```

`outboundEvents` is the hook for everything a user does, and it appears in no
documentation. `A2uiActionInterceptor` runs before an action leaves the device —
the seam for redaction, audit logging or an approval step.

## The renderer contract — `androidx.a2ui.compose.ui`

```kotlin
interface A2uiComponent {
    val name: String                                // the wire name
    val description: String
    val properties: List<A2uiProperty<*>>           // its schema
    fun isReady(scope, props): Boolean              // default true
    @Composable fun A2uiComponentScope.Content(props: A2uiComponentProperties,
                                               modifier: Modifier)
}

interface A2uiCatalog {
    val id: String                                  // the catalogId on the wire
    val components: A2uiComponentCollection
    val functions: A2uiFunctionCollection
    val themeSchema: A2uiSchema
    fun isInline(): Boolean
}

fun A2uiCatalog(id: String, components: List<A2uiComponent>,
                functions: List<A2uiFunction> = …, themeSchema: A2uiSchema = …,
                isInline: Boolean = false): A2uiCatalog
```

Nothing here mentions Material. `material3-a2ui` is one implementation.

## Properties — `androidx.a2ui.compose.runtime`

The concrete classes are `internal`. Use the factories:

```
STATIC   (fixed when the component is sent)
  A2uiProperty.string(key, required, description)
  A2uiProperty.number / boolean / booleanWithDefault / any
  A2uiProperty.stringList / numberList / booleanList / anyList
  A2uiProperty.stringEnum(key, values, required, description)
  A2uiProperty.numberEnum(key, values, required, description)
  A2uiProperty.componentId(key, required, description)
  A2uiProperty.childList(key, required, description)
  A2uiProperty.action(key, required, description)
  A2uiProperty.nested / nestedList

DYNAMIC  (may carry {"path": "/x"} and recompose)
  A2uiProperty.dynamicString / dynamicNumber / dynamicBoolean
  A2uiProperty.dynamicStringList / dynamicValue
```

## Binding — `androidx.a2ui.compose.runtime`

```kotlin
interface A2uiComponentScope {
    @Composable fun bindChildReferences(props, prop): List<A2uiComponentReference>
    @Composable fun observeA2uiComponentState(surfaceId, componentId): A2uiComponentState
    fun dispatchAction(action: Map<String, Any?>)
    fun reportError(e: A2uiException)
}

// extensions on the PROPERTIES, not the scope:
@Composable fun <T : Any> A2uiComponentProperties.bind(p: DynamicA2uiProperty<T>): T?
@Composable fun <T : Any> A2uiComponentProperties.bindUpdater(p): (T) -> Unit
operator fun <T> A2uiComponentProperties.get(p: StaticA2uiProperty<T>): T
```

`bindUpdater` is two-way binding: a text field calls it per keystroke and the
client data model updates with no network round trip.

## The function library — `androidx.a2ui.model.catalog.functions`

Thirteen functions, all `A2uiFunction` with `definition` and
`execute(args, context)`. **They run on the client** — validation does not round
trip.

```
validation  required  email  numeric  regex  length
logic       and  or  not
formatting  formatString  formatNumber  formatCurrency*  formatDate*  pluralize*
side effect openUrl*
```

`*` takes a dependency (locale provider, message formatter, URL opener) in its
constructor — which is why the Material catalog factory demands those from you.
Stateless ones are objects: `A2uiRequiredFunction.INSTANCE`. Look up at runtime
with `catalog.functions["formatCurrency"]`.

## Material — `androidx.compose.material3.a2ui`

```kotlin
@Composable fun A2uiSurface(surface: A2uiSurfaceModel, modifier: Modifier)
object A2uiSurfaceDefaults
object MaterialA2uiDefaults

// androidx.compose.material3.a2ui.catalog
fun materialA2uiBasicCatalogV1(
    image: A2uiBasicCatalogV1.Image,
    video: A2uiBasicCatalogV1.Video,
    audioPlayer: A2uiBasicCatalogV1.AudioPlayer,
    urlOpener: A2uiUrlOpener,
    messageFormatter: A2uiMessageFormatter,
    localeProvider: A2uiLocaleProvider,
): A2uiCatalog
```

## Protocol types — `androidx.a2ui.model.protocol`

```
server -> client   A2uiCreateSurfaceMessage  A2uiUpdateComponentsMessage
                   A2uiUpdateDataModelMessage  A2uiDeleteSurfaceMessage
client -> server   A2uiClientEventMessage  A2uiClientErrorMessage
actions            A2uiUserAction  A2uiEventAction  A2uiFunctionCallAction
supporting         A2uiClientDataModel (.toPayloadMap())  A2uiDataPath
                   A2uiComponentPayload  A2uiInlineCatalog  A2uiClientCapabilities
```
