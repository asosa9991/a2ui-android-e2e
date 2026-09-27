"""A2UI agent for the transfer flow.

Every surface here is pre-authored and reviewed — nothing is generated at
request time. The server picks a template and fills it from data the client
sent back, which is the pattern a regulated client needs: the structure is
fixed and auditable, only the values move.

Run:  python3 server.py        # listens on 0.0.0.0:8765
The emulator reaches the host at 10.0.2.2.
"""

import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = 8765
VERSION = "v0.9"
CATALOG = "https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json"


def surface(sid):
    return {"version": VERSION,
            "createSurface": {"surfaceId": sid, "catalogId": CATALOG,
                              "sendDataModel": True}}


def components(sid, comps):
    return {"version": VERSION,
            "updateComponents": {"surfaceId": sid, "components": comps}}


def data(sid, value):
    # The wire key is `value`, not `contents` — an empty form is the symptom.
    return {"version": VERSION,
            "updateDataModel": {"surfaceId": sid, "value": value}}


def text(cid, value, variant=None, **kw):
    c = {"id": cid, "component": "Text", "text": value}
    if variant:
        c["variant"] = variant
    c.update(kw)
    return c


def row(cid, children, **kw):
    return {"id": cid, "component": "Row", "children": children,
            "justify": "spaceBetween", "align": "center", **kw}


def label_value(idx, label, value_ref):
    """One line of a summary card."""
    return [
        row(f"r_{idx}", [f"l_{idx}", f"v_{idx}"]),
        text(f"l_{idx}", label),
        text(f"v_{idx}", value_ref, variant="body"),
    ]


# ─────────────────────────────── surface 1: the form

FORM_ID = "transfer-form"


def form_messages():
    comps = [
        {"id": "root", "component": "Column",
         "children": ["title", "sub", "card", "recurring", "submit"],
         "justify": "start", "align": "stretch"},
        text("title", "New Transfer", variant="h2"),
        text("sub", "Moving money between your own accounts settles same day."),
        {"id": "card", "component": "Card",
         "child": "card_col"},
        {"id": "card_col", "component": "Column",
         "children": ["from_field", "to_field", "amount_field", "memo_field"],
         "justify": "start", "align": "stretch"},
        {"id": "from_field", "component": "TextField", "label": "From account",
         "value": {"path": "/from"}, "variant": "shortText"},
        {"id": "to_field", "component": "TextField", "label": "To account",
         "value": {"path": "/to"}, "variant": "shortText"},
        {"id": "amount_field", "component": "TextField", "label": "Amount (USD)",
         "value": {"path": "/amount"}, "variant": "number"},
        {"id": "memo_field", "component": "TextField", "label": "Memo",
         "value": {"path": "/memo"}, "variant": "shortText"},
        {"id": "recurring", "component": "CheckBox",
         "label": "Repeat this transfer every month",
         "value": {"path": "/recurring"}},
        {"id": "submit", "component": "Button", "child": "submit_label",
         "variant": "primary",
         "action": {"event": {"name": "transfer_review", "context": {
             "from": {"path": "/from"},
             "to": {"path": "/to"},
             "amount": {"path": "/amount"},
             "memo": {"path": "/memo"},
             "recurring": {"path": "/recurring"}}}}},
        text("submit_label", "Review transfer"),
    ]
    # Streamed in pieces so the client renders progressively rather than at once.
    return [
        surface(FORM_ID),
        components(FORM_ID, comps[:4]),
        components(FORM_ID, comps[4:9]),
        components(FORM_ID, comps[9:]),
        data(FORM_ID, {"from": "Checking ••4417", "to": "Savings ••8820",
                       "amount": "2500.00", "memo": "Quarterly top-up",
                       "recurring": False}),
    ]


# ─────────────────────────────── surface 2: review, filled from the client

REVIEW_ID = "transfer-review"


def review_messages(ctx):
    amount = ctx.get("amount") or "0.00"
    recurring = "Monthly" if ctx.get("recurring") in (True, "true") else "One time"
    comps = [
        {"id": "root", "component": "Column",
         "children": ["title", "card", "note", "actions"],
         "justify": "start", "align": "stretch"},
        text("title", "Review transfer", variant="h2"),
        {"id": "card", "component": "Card", "child": "card_col"},
        {"id": "card_col", "component": "Column",
         "children": ["amt", "div", "r_0", "r_1", "r_2", "r_3"],
         "justify": "start", "align": "stretch"},
        text("amt", f"${amount}", variant="h1"),
        {"id": "div", "component": "Divider"},
        text("note", "Funds leave your account immediately once confirmed."),
        {"id": "actions", "component": "Row",
         "children": ["back", "confirm"], "justify": "spaceBetween",
         "align": "center"},
        {"id": "back", "component": "Button", "child": "back_label",
         "variant": "borderless",
         "action": {"event": {"name": "transfer_back", "context": {}}}},
        text("back_label", "Back"),
        {"id": "confirm", "component": "Button", "child": "confirm_label",
         "variant": "primary",
         "action": {"event": {"name": "transfer_confirm",
                              "context": {"amount": amount}}}},
        text("confirm_label", "Confirm transfer"),
    ]
    for i, (label, value) in enumerate([
            ("From", ctx.get("from", "—")),
            ("To", ctx.get("to", "—")),
            ("Memo", ctx.get("memo", "—")),
            ("Frequency", recurring)]):
        comps += label_value(i, label, str(value))
    return [surface(REVIEW_ID), components(REVIEW_ID, comps)]


# ─────────────────────────────── surface 3: the receipt

DONE_ID = "transfer-done"


def done_messages(ctx):
    amount = ctx.get("amount") or "0.00"
    ref = "TRN-" + str(int(time.time()))[-8:]
    comps = [
        {"id": "root", "component": "Column",
         "children": ["icon", "title", "sub", "card", "again"],
         "justify": "start", "align": "center"},
        {"id": "icon", "component": "Icon", "name": "check"},
        text("title", "Transfer scheduled", variant="h2"),
        text("sub", f"${amount} is on its way."),
        {"id": "card", "component": "Card", "child": "card_col"},
        {"id": "card_col", "component": "Column",
         "children": ["r_0", "r_1"], "justify": "start", "align": "stretch"},
        {"id": "again", "component": "Button", "child": "again_label",
         "variant": "default",
         "action": {"event": {"name": "transfer_back", "context": {}}}},
        text("again_label", "Make another transfer"),
    ]
    for i, (label, value) in enumerate([("Reference", ref),
                                        ("Posts on", "Today, 4:00 PM ET")]):
        comps += label_value(i, label, value)
    return [surface(DONE_ID), components(DONE_ID, comps)]


ROUTES = {
    "transfer_review": review_messages,
    "transfer_confirm": done_messages,
    "transfer_back": lambda ctx: form_messages(),
}


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *a):
        print("  " + fmt % a, flush=True)

    def _stream(self, messages, pace=0.45):
        """Newline-delimited JSON, flushed per message so the client can
        render each one as it lands instead of waiting for the whole body."""
        self.send_response(200)
        self.send_header("Content-Type", "application/x-ndjson")
        self.send_header("Transfer-Encoding", "chunked")
        self.end_headers()
        for m in messages:
            chunk = (json.dumps(m) + "\n").encode()
            self.wfile.write(hex(len(chunk))[2:].encode() + b"\r\n" + chunk + b"\r\n")
            self.wfile.flush()
            time.sleep(pace)
        self.wfile.write(b"0\r\n\r\n")
        self.wfile.flush()

    def do_GET(self):
        if self.path.startswith("/surface"):
            print(f"→ GET {self.path}  serving the transfer form", flush=True)
            self._stream(form_messages())
        else:
            self.send_error(404)

    def do_POST(self):
        if not self.path.startswith("/event"):
            self.send_error(404)
            return
        body = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        evt = json.loads(body or b"{}")
        name = evt.get("name", "")
        ctx = evt.get("context") or {}
        print(f"← POST /event  {name}  context={json.dumps(ctx)}", flush=True)
        handler = ROUTES.get(name)
        if not handler:
            self.send_error(400, "unknown event")
            return
        print(f"→ replying with the {name.replace('transfer_', '')} surface", flush=True)
        self._stream(handler(ctx), pace=0.3)


if __name__ == "__main__":
    print(f"A2UI transfer agent on :{PORT}  (emulator reaches it at 10.0.2.2)",
          flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
