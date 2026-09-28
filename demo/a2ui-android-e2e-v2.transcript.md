# A2UI end to end

The first Android walkthrough rendered a payload from a file. Nothing talked back. This one closes the loop. An agent streams a surface over the network, the renderer draws it, the user edits and taps, and that action travels back to the agent, which answers with the next surface. Three surfaces, two round trips, no layout code on the client.

Here is the agent. Every surface it can send is written out in advance, right here in the source. Nothing is generated when the request arrives. That matters if you work somewhere that has to review and sign off on what the user sees: the structure is fixed and auditable, and only the values move. Notice the form is sent as four separate messages rather than one, so the client can draw each piece as it lands.

The client side is two loops. The first asks the agent for a surface and feeds each line into the processor the moment it arrives. The second collects outbound events. Every time the user triggers an action the renderer emits one, and we post it straight back to the agent and stream the reply in.

That is the entire integration. Everything else is the library's job. The hook worth knowing is outbound events, a flow of client to server messages on the processor. It carries every user action, and it appears in no documentation.

Watch the first surface arrive. The app starts with nothing, asks the agent, and the form assembles as the messages land. Title and description first, then the card of fields, then the button, and finally the data model that fills every field in. This is progressive rendering: the user sees structure immediately instead of waiting for the whole payload.

Now the user acts. Ticking the box writes to the client data model through two-way binding. Tapping review sends an event to the agent carrying that data model with it. The agent picks its review template, fills it with the values the client just sent, and streams it back. Look at the frequency row: it says Monthly, because the checkbox we ticked made the trip and came home again.

Confirm sends the second event and the agent replies with a receipt it built at that moment, with a fresh reference number. Three surfaces have now been rendered natively, and the client never knew what any of them would look like. It only knew how to render whatever the agent sent.

And this is the same conversation from the agent's side. One request for the opening surface, then events arriving with the user's data attached, each answered with the next surface. Swap this agent for your own service and the client does not change at all.
