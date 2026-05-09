---
name: impl-docs-skill
description: "MZTK-BE implementation plan document generation skill. Reads a design doc and writes a commit-by-commit implementation plan as a Markdown file under docs/implementation_docs/, following Hexagonal Architecture and java-best-practice. Always use this skill when the user asks for: - \"write an implementation plan\", \"create an impl doc\", \"plan the implementation\", \"organize the implementation order\" - \"create an implementation plan from the design doc\", \"plan based on the design\" - Any situation where an implementation blueprint is needed after a design doc is complete and before coding starts - Implementation planning for a Jira ticket"
---

<!-- GENERATED FROM .claude/skills/impl-docs-skill/SKILL.md by scripts/agents/sync-skills.py.
     DO NOT EDIT DIRECTLY — edit the source SKILL.md and re-run the script. -->

# Implementation Plan Document Skill (MZTK-BE)

## Purpose

Read a design doc and produce a concise, commit-by-commit implementation plan in Markdown
that follows Hexagonal Architecture and java-best-practice conventions.

---

## Workflow

### Step 1 — Resolve the Design Doc Path

1. **If the user specifies a path directly**: use that file.
2. **No path given (default)**: extract the Jira ticket ID from the current git branch name.
   ```bash
   git branch --show-current
   # e.g. feature/MOM-330-admin-user-role-management → ticket: MOM-330
   ```
   Find the directory under `docs/design/` whose name contains the ticket ID.
   Read all `.md` files inside it.
   If no matching directory exists, ask the user to provide the path directly.

### Step 2 — Analyze the Design Doc

Identify the following from the design doc:

- **Feature scope**: which layers/modules are newly created, which existing files are modified
- **Dependencies**: if A depends on B, B must be implemented first
- **Ripple effects**: impact on existing tests, other modules, and the DB schema
- **External dependencies**: AWS, third-party APIs, new library introductions

### Step 3 — Load Reference Files

Always read these files before planning:
- `ARCHITECTURE.md` — layer dependency rules, package structure
- `.claude/skills/java-best-practice/SKILL.md` — coding conventions, naming rules

### Step 4 — Plan the Commits

Determine implementation order based on Hexagonal Architecture layer dependencies:

**Default commit order (bottom-up by layer)**:
1. `domain` layer (model, vo) — no dependencies
2. DB migration (Flyway `.sql`) — after domain
3. `infrastructure/persistence` (entity, repository, adapter)
4. `application/port/out` interfaces
5. `application/port/in` + `application/service` (use-case)
6. `infrastructure/<external>` adapters (AWS, external APIs)
7. `api` layer (controller, dto)
8. Security/Config changes
9. Bootstrap/scheduler components

> This order is a baseline. Adjust it by analyzing the actual dependencies in the design doc.
> Group loosely related changes into a single commit to keep the commit count minimal.

Each commit must:
- Be independently buildable and testable
- Have a single responsibility — one logical unit of change
- Include test modifications in the same commit if existing tests are affected

### Step 5 — Write the Implementation Doc

Follow the **Output Format** below.

### Step 6 — Save the File

1. **Determine the save path**: look for an appropriate directory under `docs/implementation_docs/`.
   - If a directory matching the Jira ticket or feature name already exists, save there.
   - Otherwise create a new directory (e.g. `docs/implementation_docs/admin/`).
2. **File name**: derive from the H1 title of the design doc.
   - English title: kebab-case + `-impl.md`
   - Title with Korean: ticket ID + summary slug + `-impl.md`
   - Example: `MOM-330-admin-account-management-impl.md`
3. Create the file and report the path to the user.

---

## Output Format

```markdown
# {Design Doc Title} — Implementation Plan

Design doc: `{relative path to design doc}`

---

## Commit Order Summary

| # | Commit | Key Changes | Depends On |
|---|--------|-------------|------------|
| 1 | {commit title} | {file/class summary} | none |
| 2 | {commit title} | {file/class summary} | #1 |
| N | ... | ... | ... |

---

## Commit N: {Title}

**Scope**: {affected layers/modules}

### New Files

**`{FileName}.java`** — `{package path}/`
- {list only key fields/methods as bullets}
- Note any caveats in one line if needed

### Modified Files

**`{FileName}.java`** (modify)
- {change description as bullets}

### Existing Test Impact
- {TestClass}: {one-line reason for modification}

---
```

**Writing principles**:
- No code blocks — reference file names, class names, and method signatures as plain text only
- Keep bullets to 5 or fewer per section (essentials only)
- Omit "New Files" or "Existing Test Impact" sections when there is nothing to list
- Do not duplicate content already in the design doc (DDL, decision rationale, etc.)
- Assume the implementer reads the design doc and impl doc side-by-side

---

## Key Architecture Rules Summary

(See `ARCHITECTURE.md` for full details)

- `api` → references `application/port/in` only; direct `infrastructure` imports are forbidden
- `application/service` → references `domain` + `application/port/out` only; `infrastructure` imports are forbidden
- `infrastructure` → implements `application/port/out`; must not import `api`
- Cross-module: `A/application/service` must not reference module B directly;
  call `B/application/port/in` through `A/infrastructure/external/<b>/` adapter
- DB migrations: never modify existing `.sql` files; only add new `V{N+1}__*.sql`