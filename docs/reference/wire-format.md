# Wire format (v0.9 / v0.9.1)

What an agent sends. Newline-delimited JSON over any transport.

## The four server-to-client messages

```json
{"version": "v0.9", "createSurface": {
    "surfaceId": "s1", "catalogId": "yourcompany.com:cat-v1", "sendDataModel": true}}

{"version": "v0.9", "updateComponents": {"surfaceId": "s1", "components": [ … ]}}

{"version": "v0.9", "updateDataModel": {"surfaceId": "s1", "value": { … }}}

{"version": "v0.9", "deleteSurface": {"surfaceId": "s1"}}
```

**`updateDataModel` takes `value`, not `contents`.** Using `contents` renders the
form perfectly with every field empty, and reports nothing.

**`catalogId` is a single string.** The surface resolves only against that
catalog. See SKILL.md §4.

## Components are an adjacency list, not a tree

Each component has an `id` and names children by id. The entry point is `root`.
Because it is flat, a partial tree is legal and the client draws what it has —
which is what makes progressive rendering work.

```json
{"id": "root", "component": "Column", "children": ["title", "list"],
 "justify": "start", "align": "stretch"}
{"id": "title", "component": "Text", "text": "Holdings", "variant": "h2"}
```

## Binding

Any dynamic property can take a path instead of a literal:

```json
{"id": "amount", "component": "Text", "text": {"path": "/summary/total"}}
```

Leading `/` is absolute. A bare path is **relative to the item currently being
rendered**, which is what makes templates nest.

On inputs the binding runs **both ways** — typing updates the client data model
locally, with no round trip:

```json
{"id": "f", "component": "TextField", "label": "Amount",
 "value": {"path": "/amount"}, "variant": "number"}
```

## Templates: variable-length data from fixed structure

`children` accepts either an array of ids, or a template object:

```json
{"id": "list", "component": "Column",
 "children": {"componentId": "row", "path": "/holdings"}}
```

That renders `row` once per item at `/holdings`. Templates nest via relative
paths, so a fixed component set renders a tree whose depth and width you did not
know when you wrote it. Cardinality and depth are **data**; only a change of
component *kind* needs another template.

## Actions

```json
{"id": "submit", "component": "Button", "child": "label", "variant": "primary",
 "action": {"event": {"name": "submitted",
                      "context": {"amount": {"path": "/amount"}}}}}
```

On tap the processor emits an `A2uiClientEventMessage` on `outboundEvents`,
carrying the event name, component, context and the client data model
(`.toPayloadMap()`). `{"action": {"functionCall": …}}` instead invokes a catalog
function locally and never leaves the device.

## Basic catalog enums — get these wrong and you get a red Error chip

```
Text.variant          h1 h2 h3 h4 h5 caption body
Button.variant        default primary borderless
Icon.name             camelCase, a closed set of 59: accountCircle add arrowBack
                      arrowForward attachFile calendarToday call camera check close
                      delete download edit event …
Column/Row.justify    start center end spaceBetween spaceAround spaceEvenly stretch
Column/Row.align      start center end stretch
List.direction        vertical horizontal
List.align            start center end stretch
TextField.variant     shortText longText number obscured
ChoicePicker.variant  multipleSelection mutuallyExclusive
ChoicePicker.displayStyle  checkbox chips
```

Components: `Text Button Card Column Row List Divider Icon Image Video
AudioPlayer CheckBox ChoicePicker Slider TextField DateTimeInput Tabs Modal`

Authoritative source: `specification/v0_9_1/catalogs/basic/catalog.json` in the
a2ui repo. Validate payloads against it before sending — an invalid enum renders
a wrong screen rather than failing.
