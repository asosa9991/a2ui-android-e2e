---
output: a2ui-android-e2e-v2.mp4
voice: Zoe (Premium)
rate: 178
resolution: 1920x1080
captions: soft
---

# A2UI end to end
> An agent streams a surface, the user acts, the agent answers

## What end to end means
@card 1 | The agent | Pre-authored surfaces, streamed to the client
The first Android walkthrough rendered a payload from a file. Nothing talked
back. This one closes the loop. An agent streams a surface over the network, the
renderer draws it, the user edits and taps, and that action travels back to the
agent, which answers with the next surface. Three surfaces, two round trips, no
layout code on the client.

## The agent's surfaces
@run sed -n '/^def form_messages/,/^    ]/p' ~/pocs/a2ui-android-e2e/server/server.py
Here is the agent. Every surface it can send is written out in advance, right
here in the source. Nothing is generated when the request arrives. That matters
if you work somewhere that has to review and sign off on what the user sees: the
structure is fixed and auditable, and only the values move. Notice the form is
sent as four separate messages rather than one, so the client can draw each piece
as it lands.

## The client loop
@card 2 | The client loop | Stream in, act, send the action back
The client side is two loops. The first asks the agent for a surface and feeds
each line into the processor the moment it arrives. The second collects outbound
events. Every time the user triggers an action the renderer emits one, and we
post it straight back to the agent and stream the reply in.

## Two loops, and that is all
@run sed -n '/1. Ask the agent/,/^  }/p' ~/pocs/a2ui-android-e2e/app/src/main/java/com/example/a2uie2e/MainActivity.kt
That is the entire integration. Everything else is the library's job. The hook
worth knowing is outbound events, a flow of client to server messages on the
processor. It carries every user action, and it appears in no documentation.

## Streaming the first surface
@clip flow.mp4 2-20
Watch the first surface arrive. The app starts with nothing, asks the agent, and
the form assembles as the messages land. Title and description first, then the
card of fields, then the button, and finally the data model that fills every
field in. This is progressive rendering: the user sees structure immediately
instead of waiting for the whole payload.

## The first round trip
@clip flow.mp4 20-40
Now the user acts. Ticking the box writes to the client data model through
two-way binding. Tapping review sends an event to the agent carrying that data
model with it. The agent picks its review template, fills it with the values the
client just sent, and streams it back. Look at the frequency row: it says
Monthly, because the checkbox we ticked made the trip and came home again.

## Confirming
@clip flow.mp4 40-55
Confirm sends the second event and the agent replies with a receipt it built at
that moment, with a fresh reference number. Three surfaces have now been rendered
natively, and the client never knew what any of them would look like. It only
knew how to render whatever the agent sent.

## The agent's view
@run grep -E "GET /surface|POST /event|replying" /tmp/a2ui-agent.log | tail -8
And this is the same conversation from the agent's side. One request for the
opening surface, then events arriving with the user's data attached, each
answered with the next surface. Swap this agent for your own service and the
client does not change at all.
