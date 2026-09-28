"""Non-deterministic data, rendered by ONE reviewed template.

The structure below is fixed. It is written once, reviewed once, and never
changes at request time. Only the data model moves. Tapping "next dataset"
swaps the data underneath the same components:

    0 holdings  ->  empty state
    1 holding   ->  a single row
   40 holdings  ->  forty rows
    nested      ->  groups of holdings, arbitrary depth

Nothing about the component tree differs between those four. That is the
whole point: an agent that may not generate UI can still render data whose
shape it did not know in advance.
"""

import random

VERSION = "v0.9"
CATALOG = "example.com:positions-v1"   # Material's components + PositionRow
SID = "holdings"

SYMBOLS = ["AAPL", "MSFT", "NVDA", "AMZN", "GOOGL", "META", "TSLA", "JPM",
           "V", "UNH", "XOM", "JNJ", "WMT", "PG", "MA", "HD", "CVX", "ABBV",
           "KO", "PEP", "COST", "MRK", "ADBE", "CRM", "NFLX", "AMD", "INTC",
           "QCOM", "TXN", "HON", "IBM", "GE", "CAT", "BA", "MMM", "NKE",
           "SBUX", "LOW", "UPS", "RTX"]


# ─────────────────────────────── the template, written once

def components():
    """The entire component tree. Four templates, two of them nested.

    `children` as an object rather than an array is what makes this work:
    {componentId, path} says "render this one component once per item at
    that path". A path starting with / is absolute; a bare path is relative
    to the item currently being rendered, which is what allows nesting.
    """
    return [
        {"id": "root", "component": "Column",
         "children": ["title", "subtitle", "empty_note", "flat_list",
                      "group_list", "next_btn"],
         "justify": "start", "align": "stretch"},

        {"id": "title", "component": "Text",
         "text": {"path": "/heading"}, "variant": "h2"},
        {"id": "subtitle", "component": "Text", "text": {"path": "/caption"}},

        # "Hidden" when the list is non-empty by binding it to an empty string.
        # v0.9 has no conditional rendering, so a container would still draw its
        # box -- bind a bare Text instead and let empty text take no space.
        {"id": "empty_note", "component": "Text", "text": {"path": "/emptyText"}},

        # TEMPLATE 1 — flat: one row component, N rows
        {"id": "flat_list", "component": "Column",
         "children": {"componentId": "holding_row", "path": "/holdings"},
         "justify": "start", "align": "stretch"},

        # the row: now OUR custom component instead of three basic ones
        {"id": "holding_row", "component": "PositionRow",
         "symbol": {"path": "symbol"},
         "value": {"path": "value"},
         "kind": {"path": "kind"},
         "detail": {"path": "detail"}},

        # TEMPLATE 2 — nested: groups, each rendering the SAME row template
        {"id": "group_list", "component": "Column",
         "children": {"componentId": "group_block", "path": "/groups"},
         "justify": "start", "align": "stretch"},
        {"id": "group_block", "component": "Card", "child": "group_col"},
        {"id": "group_col", "component": "Column",
         "children": ["group_title", "group_rows"],
         "justify": "start", "align": "stretch"},
        {"id": "group_title", "component": "Text", "text": {"path": "name"},
         "variant": "h3"},
        # relative path: "holdings" inside the group currently rendering
        {"id": "group_rows", "component": "Column",
         "children": {"componentId": "holding_row", "path": "holdings"},
         "justify": "start", "align": "stretch"},

        {"id": "next_btn", "component": "Button", "child": "next_label",
         "variant": "primary",
         "action": {"event": {"name": "next_dataset",
                              "context": {"index": {"path": "/index"}}}}},
        {"id": "next_label", "component": "Text", "text": {"path": "/nextLabel"}},
    ]


KINDS = [("equity", ""), ("option", "Jan 17 '26 $200 Call  \u00b7  delta 0.62"),
         ("bond", "2.750%  \u00b7  matures Feb 15 2032")]


def holding(sym, kind_idx=0):
    kind, detail = KINDS[kind_idx % len(KINDS)]
    return {"symbol": sym, "kind": kind, "detail": detail,
            "value": "$%s" % format(random.randint(4_000, 900_000), ",d")}


# ─────────────────────────────── four datasets, one template

def dataset(i):
    """Only this function changes between screens. The tree above does not."""
    i = i % 4
    nxt = "Next dataset  (%d of 4)" % (i + 1)

    if i == 0:
        return {"heading": "Holdings", "caption": "Nothing to show yet.",
                "emptyText": "No holdings in this account.",
                "holdings": [], "groups": [], "index": 1, "nextLabel": nxt}

    if i == 1:
        return {"heading": "Holdings", "caption": "One position.",
                "emptyText": "", "holdings": [holding("AAPL", 1)],
                "groups": [], "index": 2, "nextLabel": nxt}

    if i == 2:
        return {"heading": "Holdings", "caption": "Forty positions.",
                "emptyText": "",
                "holdings": [holding(s, i) for i, s in enumerate(SYMBOLS)],
                "groups": [], "index": 3, "nextLabel": nxt}

    return {"heading": "Holdings", "caption": "Grouped, two levels deep.",
            "emptyText": "", "holdings": [],
            "groups": [
                {"name": "Technology",
                 "holdings": [holding(s, i) for i, s in enumerate(SYMBOLS[:6])]},
                {"name": "Financials",
                 "holdings": [holding(s, i + 1) for i, s in enumerate(SYMBOLS[7:10])]},
                {"name": "Healthcare",
                 "holdings": [holding(s, i + 2) for i, s in enumerate(SYMBOLS[9:12])]},
            ],
            "index": 0, "nextLabel": nxt}


def messages(i=0, first=True):
    """Structure is sent once. After that only the data model is replaced."""
    out = []
    if first:
        out.append({"version": VERSION,
                    "createSurface": {"surfaceId": SID, "catalogId": CATALOG,
                                      "sendDataModel": True}})
        out.append({"version": VERSION,
                    "updateComponents": {"surfaceId": SID,
                                         "components": components()}})
    out.append({"version": VERSION,
                "updateDataModel": {"surfaceId": SID, "value": dataset(i)}})
    return out
