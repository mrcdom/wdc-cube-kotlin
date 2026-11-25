# 0013. The server decides the price of an item

- **Date:** 2026-11-22
- **Status:** Accepted

## Context

A purchase item carries the price paid. The client sent it, copied from the product it had shown, and the server stored what came. A client that the user controls can send any price: a REST client could buy anything for a cent.

## Decision

The price of an item is the server's. On insert, the item repository reads the product's current price (a `SELECT` before the `INSERT`, in the same transaction) on every path: a loose item, or items inside a purchase. An update through the API that touches the price or the product recomputes it (`ItemPrices`, in `persistence-rest`). A price sent by the client is never used: a customer who sends one is refused (403 naming `price`, from the data policy, see [0006](0006-access-control-at-the-rest-boundary.md)); the administrator's grant allows the field, but the value is replaced by the product's price. A product that does not exist is a 400.

## Consequences

- What the user pays is what the catalog says at the moment of the purchase.
- A price changed between showing the cart and buying is charged at the new value. The receipt shows what was charged.
- Tests of items and purchases use the real prices of the products.

## Alternatives considered

- **Accept the client's price and check it.** Rejected: two sources of truth for the same value, and a check is easy to forget on one path.
