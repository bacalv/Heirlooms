---
id: RES-005
title: Glossary self-reference audit — ensure every term cited in a description has its own entry
category: Research
priority: Low
status: queued
assigned_to: ResearchManager
depends_on: []
estimated: 1 session
---

## Context

The GLOSSARY.md is the team's canonical plain-language reference for cryptographic and
domain terms. It has grown substantially across RES-001 through RES-004 and is in good
shape for primary terms. However, a review of existing entries has revealed a pattern:
descriptions frequently cite compound concepts, abbreviations, or portmanteaus that are
themselves not defined as standalone entries.

A concrete example raised during review: the term **"PQC migration"** is used throughout
the glossary (e.g. in entries for Attack window, DEK, DEK re-wrap, Key rotation, Migration
phase, Re-wrap) but there is no standalone `PQC migration` entry that brings the concept
together. A reader who encounters the phrase for the first time in an entry description is
not pointed to a definition — they must infer meaning from two separate entries
(Post-quantum cryptography and Migration phase).

The invariant the glossary should uphold: **if a description mentions "X" or "X-Y", there
should be a findable entry under X (or X-Y, or a cross-reference "X — see Y").**

## Research questions

1. Perform a full pass of every entry in `docs/research/GLOSSARY.md`. For each description,
   identify every term, acronym, or compound phrase that:
   - is used as though the reader already knows it, AND
   - does not have its own entry AND has no "see also" cross-reference pointing to one.

2. For each gap found, determine the right resolution — one of:
   - **New entry**: the term is substantive enough to warrant its own definition.
   - **Cross-reference stub**: add a one-liner `**Term** — see *Existing entry*.` under
     the correct letter heading.
   - **Inline clarification**: the parent entry's description should be expanded to define
     the term inline (appropriate for very narrow phrases that don't stand alone).

3. Pay particular attention to:
   - Abbreviations expanded inline but never given their own heading (e.g. "PQC migration",
     "HNDL attack window", "re-wrap cost")
   - Compound terms where both halves have entries but the combination does not
     (e.g. "hybrid key exchange" is defined, but "hybrid scheme" used elsewhere may not
     point to it clearly)
   - Acronyms used in passing without expansion the first time they appear in that entry

## Deliverables

- Updated `docs/research/GLOSSARY.md` with all gaps resolved (new entries, stubs, or
  inline expansions as appropriate).
- A short audit summary appended as a comment at the top of the file or in a companion
  `docs/research/GLOSSARY_AUDIT_RES-005.md` note, listing: terms reviewed, gaps found,
  resolution chosen for each.

## Acceptance criteria

- No entry description contains a bolded or all-caps term that lacks a reachable definition
  within the glossary (either its own heading or a "see" cross-reference).
- The `PQC migration` gap specifically is resolved with at minimum a cross-reference stub
  and ideally a short standalone entry.
- GLOSSARY.md header is updated with the new last-updated date and RES-005 reference.
