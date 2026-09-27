# A2UI on Android: The Developer Guide

This is a working guide to the Android A2UI libraries as they stand in alpha. By the end you will know what is published and where, how to wire a renderer into a Compose app, how the protocol works on the wire, how data binding and user actions close the loop back to your agent, and the ten traps that will otherwise each cost you a build cycle. Everything shown here is running code, not pseudocode. Two projects back it: one that renders a static payload, and one that talks to a live agent.

Start with what is actually published. Five artifacts, spread across three separate Maven groups. The androidx dot a 2 u i group holds the data layer: the model, which is schemas and data types, and the engine, which parses protocol messages and drives the state machine. The androidx dot a 2 u i dot compose group is the renderer itself. And the third group, under Material 3, holds a ready-made catalog: all eighteen Basic Catalog components, already implemented in Material. Now the first trap, and it is the most expensive one, because it stops people before they start. The androidx dot a 2 u i group index lists only the model and the engine. Neither carries a Compose dependency. Read only that page and you will conclude Android has no renderer. It does. It is simply in a different group.

So let us wire one up. Three things to get right in Gradle, and two of them are not obvious.

Here is the dependency block from a working app. A Compose bill of materials, the usual Compose UI and Material 3, activity Compose, and then the five A2UI artifacts. Everything resolves from Google's Maven repository, so make sure google is listed in your settings file. All five are pinned to the same alpha version, and you should keep them in lockstep. This is alpha software: the artifacts are released together and there is no compatibility promise across mismatched versions.

Now the Android block, and two traps. First, the plugins. There are only two here: the Android application plugin and the Compose compiler plugin. Android Gradle Plugin nine rejects the old Kotlin Android plugin outright, with an error telling you it has not been required since AGP nine. If you copied a template from last year, delete that line, and delete the J V M toolchain block with it. Second, look at the compile SDK. Targeting thirty seven point one is not one property, it is two: compile SDK thirty seven, and compile SDK minor one. And use the J D K that ships inside Android Studio. Newer J D Ks break the build in ways that are hard to read.

With the project building, the first object you construct is a catalog. It tells the renderer which components exist and how to draw them.

If you use the Material catalog, you call this factory. Notice that it takes six parameters and none of them have defaults, which surprises people. The library deliberately ships no image loader, no video player and no audio player, because those are opinionated dependencies and it will not choose one for you. You supply them, along with a URL opener, a message formatter, and a locale provider. If your payload never uses images, video or audio, no-op implementations are perfectly legitimate, which is what this app does.

Here is how you write one, and here is the third trap. The concrete Material implementations of image, video and audio are marked internal, so you cannot reference or subclass them. Implement the public interfaces instead. And note the shape of the function: Typed Content is an extension function on A2UI component scope, not a plain override, and its accessibility parameter is nullable. Getting that signature wrong by hand is fiddly, so let the Kotlin compiler print its potential signatures for overriding, and copy what it gives you.

That is the setup done. The rendering itself is short.

Six lines, and this is the whole of it. Create a message processor from your list of catalogs. Launch a coroutine that calls collect messages, which drains the processor's inbound queue, and keep it alive for as long as the screen is composed. Forgetting that one line is a common mistake, and the symptom is a screen that never updates. Feed each parsed message in. Then collect active surfaces as Compose state, and hand a surface to A2UI Surface. From there the library owns the tree, the diffing and the recomposition.

And that is enough to produce this: a brokerage positions screen, scrolling, with every component driven by a payload rather than by layout code you wrote.

Now the wire. Understanding three message types is most of understanding A2UI.

Create surface opens a surface and names the catalog it will use. Update components sends components, and here is the important idea: it is an adjacency list, not a tree. Each component has an identifier and refers to its children by their identifiers. The component named root is the entry point. Because it is a flat list, you can send components before their children exist, and fill in the gaps later. Update data model sends the values those components bind to. Keep these separate in your head: structure and data travel independently, and that separation is what makes streaming and updates cheap.

Which brings us to how a component gets its value.

Instead of a literal, a property can take a path: a JSON pointer into the surface's data model. A text field pointing at slash amount displays whatever lives there. The part people miss is that on inputs this binding runs both ways. When the user types in that field or ticks that checkbox, the client data model is updated underneath. Nothing round trips to your agent for that. It is local, immediate, and it is why a form feels native rather than remote.

Separating structure from data also buys you progressive rendering. Watch this form arrive. The agent sends it as four messages, not one, so the client draws each piece as it lands: heading first, then the card of fields, then the button, and finally the data model that fills every field in. The user sees structure immediately instead of a spinner.

So far everything flows one way. Here is how it comes back.

A button carries an action, and that action names an event plus the context to send with it. When the user taps, the processor emits on outbound events, which is a Kotlin flow of client to server messages. This is the piece that is not in the documentation, so it is worth writing down: outbound events is the hook for everything a user does. Collect it, serialise the event, and send it wherever your agent lives. Here that is an H T T P post. The agent's reply streams straight back into the same processor, and the next surface renders.

Here it is end to end. Ticking the box writes to the client data model. Tapping review sends the event, with that data model attached. The agent fills a template with the values the client just sent, and streams it back. Look at the frequency row: it reads Monthly, because the checkbox made the trip and came home again. Two surfaces, one round trip, no layout code on the client.

Before you build anything, here is the whole list in one place.

The build traps we covered. The catalog traps we covered. The four at the bottom are the dangerous ones, because none of them throw. They render the wrong screen and leave you looking at your own code. Update data model takes a key called value, not contents. Get that wrong and the form renders perfectly with every field empty. The variant enumerations are small and strict: text variants are h1 through h5, caption and body, so body medium is not valid and it will draw a red error chip where your text should be. Button variants are default, primary and borderless. And icon names are camel case, so check works and check underscore circle does not. When a component renders as an error chip, suspect an enum value before you suspect your code.

One last thing, and for most teams it is the most important part.

The renderer does not know what Material is. These are the two interfaces the whole system is built on. A component has a name, a description, a list of properties, and a composable content function. A catalog has an identifier, a collection of components, a collection of functions, and a theme schema. Material 3 dash a 2 u i is simply one implementation of that catalog interface. If you have your own design system, you implement the same two interfaces and register yours instead, or alongside, since a processor accepts several catalogs. You keep the protocol engine and supply the pixels, which is the right split for most product teams.

That is the guide. The Android documentation page is the official reference, though it carries no code samples today. The source is browsable on cs dot android dot com. And if you are building your own renderer, the conformance suites in the A2UI repository are thirty nine language-agnostic test definitions of correct behaviour, which is the closest thing to a specification of what a renderer must do. Both projects shown here are on disk and runnable. Start with the static one, then move to the agent round trip once rendering makes sense.
