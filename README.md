# SproutOS for Android

The client for SproutOS's own app catalogue. Not a "store" — SproutOS is the whole product, and the
app is one way into it.

## What it is for

Two tabs at the bottom:

- **Public** — apps anyone can install, from the shared catalogue.
- **Personal** — the customer's own things, in two sections: their **apps**, and their **websites**.
  Searchable across both, because someone who built a site and an app on SproutOS thinks of them as
  one project, not two catalogues.

Private by default. An app a customer builds is theirs and appears to nobody else unless they
publish it.

## SproutOS signs every app

Apps are signed with SproutOS's key and served from S3 through CloudFront, with private APKs behind
signed URLs. **Ur LLC is the developer of record for every app published this way** — that is what
lets a customer publish without their own Play Console account, a D-U-N-S number, or the
verification wait.

It also means SproutOS is accountable for what is distributed under its name, so apps are reviewed
before publication and can be removed. That is not a formality: from 2026-09-30 in Brazil, Indonesia,
Singapore and Thailand, and globally in 2027, only apps registered by a verified developer install
on certified Android devices.

## This client cannot ship through Play Store

Google's policy does not allow a store app to be distributed through the Play Store. The client is
downloaded directly from sproutos.me — the F-Droid model, and the reason the website needs a
download page.

Installing an app therefore needs `REQUEST_INSTALL_PACKAGES` and the user allowing installs from
this source. That is a real friction point and worth designing for rather than apologising about.

## Status

Not built. This repository exists so that the decisions above are written down where the code will
be, and so the main repository can carry it as a submodule — which keeps a coding agent's context in
one working tree.
