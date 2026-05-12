# Heirlooms — Team

## Organisation

Heirlooms is a solo-founded project. The team is small by design.

---

## Bret Adam Calvey — Founder & CTO

Product vision, technical direction, and all final decisions. Bret determines what
gets built, in what order, and why. He holds the domain knowledge about the problem
space (digital legacy, family memory, milestone delivery) and is the sole human
on the team.

Contact: GitHub @bacalv

---

## Claude (claude.ai) — PA to the CTO

Strategic and architectural thinking partner. Responsible for:
- Product and technical brainstorming
- Architecture decisions and tradeoff analysis
- Generating new files, documents, and significant code changes
- Maintaining project documentation (PROMPT_LOG.md, ROADMAP.md, TEAM.md)
- Reviewing and understanding the full project history across sessions

Works via the claude.ai chat interface. Has no direct access to the codebase —
receives context through file uploads, pastes, and shared documents.
Uses PROMPT_LOG.md as the primary mechanism for staying in sync with the codebase.

Current model: Claude Sonnet 4.6

---

## The Software Engineer (Claude Code in IntelliJ IDEA) — Engineer

Hands-on implementation. Responsible for:
- Fixing compiler errors and build issues
- Refactoring and code quality improvements
- Writing and running tests
- Git commits (but not pushes — see below)
- Keeping PROMPT_LOG.md updated with code-level decisions

Works directly inside IntelliJ IDEA via the Claude Code [Beta] plugin. Has full
read/write access to the local filesystem and can run terminal commands.
References @PROMPT_LOG.md for project context at the start of significant tasks.

---

## Early Users

The first humans outside the founding team to use Heirlooms on real devices.

**Sadaar** — Bret's daughter. Account on Fire OS tablet. First account created via
the invite flow (M8). Used for cross-device sharing testing with Bret's account.

**Wighty** — Bret's friend. Account on TCL T517D (Android 15). First external human
tester onboarded 2026-05-11. Used for real-world friend-sharing and cross-account
testing across M9 features.

---

## How the team works together

Bret brings direction. The PA brings perspective and documentation. The Software
Engineer brings execution. Decisions flow from Bret, shaped by conversation with
the PA, and implemented by the Software Engineer.

The Software Engineer may create commits but **Bret always does the final push**
to the remote repository. This ensures a human has final authority over what
enters the shared codebase.

PROMPT_LOG.md is the shared memory of the team — updated by both Claude instances
and reviewed by Bret. It is the single source of truth for why things are the way
they are.

---

## Tools

| Tool | Used by |
|---|---|
| claude.ai | Bret + PA |
| IntelliJ IDEA (Community) + Claude Code [Beta] | Bret + Software Engineer |
| GitHub (`github.com/bacalv/Heirlooms`) | All |
| Docker Desktop (macOS) | Software Engineer via terminal |
| Android Studio SDK (macOS, SDK only) | Software Engineer |