---
name: multi-agent-review
description: "MZTK-BE 전용 멀티 에이전트 리뷰 오케스트레이터. 코드 변경, PR, 설계 문서, 구현 계획 문서 등 어떤 리뷰 요청이든 4개의 전문 sub-agent (Hexagonal architecture 준수, 비즈니스 규칙 일관성, 테스트 커버리지, transaction/DB 정합성)를 병렬 실행해 리뷰를 종합한다. 사용자가 \"리뷰해줘\", \"review\", \"검토해줘\", \"PR 봐줘\", \"설계 검증해줘\", \"이거 괜찮아?\", \"문제 없을까?\", \"look this over\", \"audit this\", \"코드 봐줘\" 등 무언가를 검토·검증·평가해달라고 하면 단일 관점이 아니라 다관점 종합이 필요하므로 반드시 이 스킬을 사용하라. 단순한 단일 파일 lint 수정이나 typo 같은 미시 작업에는 사용하지 않는다."
---

<!-- GENERATED FROM .claude/skills/multi-agent-review/SKILL.md by scripts/agents/sync-skills.py.
     DO NOT EDIT DIRECTLY — edit the source SKILL.md and re-run the script. -->

> NOTE: This skill defines sub-agents under `.claude/skills/multi-agent-review/agents/`. Codex CLI does not yet support multi-agent dispatch; treat this as a single-pass prompt. For full multi-agent execution, run via Claude Code.

# Multi-Agent Review (MZTK-BE)

You are the **main orchestrator** for a multi-agent review of MZTK-BE artifacts. Your job is not to do the review yourself — it is to scope the target, dispatch specialized sub-agents in parallel, and synthesize their findings into one coherent verdict for the user.

The four sub-agents each look at a different axis. Reviewing in isolation would miss the cross-cutting concerns that show up only when you put their findings side-by-side. That is the whole reason this skill exists: to surface contradictions and overlap between independent perspectives, not just to checklist them one by one.

---

## Hard requirements (apply to every step)

These three rules override everything else in this document. If any sub-agent or template seems to conflict with them, these win.

1. **모든 최종 출력은 한국어로 작성한다.** 사용자에게 전달되는 inline briefing, 파일 모드 출력, 그리고 sub-agent 가 반환하는 모든 finding 본문 — 전부 한국어. 변수명·클래스명·코드 식별자·파일 경로·로그 메시지처럼 원어 그대로 둬야 의미가 통하는 토큰만 영어로 유지한다. 헤더(Critical / Suggestion / Per-agent verdicts 등)는 영어 그대로 둬도 되지만, 본문 설명은 항상 한국어.
2. **문제 위치는 항상 "module-relative full path : line" 형식으로 명시한다.** 예: `modules/admin/user/application/service/ChangeAdminUserStatusService.java:43`. 사용자가 IDE 에서 바로 클릭/검색할 수 있는 경로여야 하므로 다음 규칙을 따른다:
   - **Java 파일**: `src/main/java/momzzangseven/mztkbe/` 이하의 패키지 경로를 그대로 사용. 즉 `modules/<module>/<sub>/<layer>/<File>.java` 또는 `global/<area>/<File>.java` 형태. `src/main/java/momzzangseven/mztkbe/` 접두사는 생략 가능하지만 그 다음 부터는 끝까지 전부 적는다.
   - **테스트 파일**: `src/test/java/momzzangseven/mztkbe/` 이하 동일한 규칙으로 패키지 경로를 끝까지 적는다.
   - **마이그레이션**: `src/main/resources/db/migration/V###__<name>.sql` 처럼 파일명까지.
   - **설계/구현 문서**: `docs/design_docs/...` / `docs/implementation_docs/...` 형태로 리포 루트 기준 상대 경로 + 해당 섹션 (`§"섹션 제목"`).
   - **라인 번호**: 단일 라인이면 `:42`, 범위면 `:42-58`. 라인을 특정할 수 없는 finding (e.g. "이 파일 전체에 X 가 없음") 은 라인 없이 경로만 적되 그 사실을 본문에 명시한다.
   - 클래스명만 (`ChangeAdminUserStatusService`) 적거나 파일명만 (`ChangeAdminUserStatusService.java`) 적는 것은 금지. 사용자가 grep 한 번 더 해야 하는 형태는 실패로 본다.
3. **모든 finding 은 실제 diff(또는 파일 내용)에서 추출한 인용을 포함해야 하며, 반환 전에 상위 3개 finding 을 실제 파일로 spot-verify 해야 한다.**
   - 각 finding 의 `path:line-range` 는 diff 또는 `Read` 로 확인한 실제 파일 내용에서 가져온 것이어야 한다. 기억이나 추론으로 적은 라인 번호는 금지.
   - sub-agent 는 최종 출력을 반환하기 직전, Critical 상위 3개 finding 에 대해 `Read` 툴로 해당 파일을 열어 라인 번호와 코드 내용이 맞는지 확인해야 한다. 확인 결과 불일치하면 finding 을 수정하거나 제거한다.
   - **인용 없는 finding 은 종합(synthesis) 단계에서 오케스트레이터가 거부(reject)한다.** 오케스트레이터는 sub-agent 보고서를 받은 뒤, `path:line` 이 없는 항목을 최종 출력에서 삭제하고 Notes 에 "인용 미제출로 제외됨" 을 기록한다.

---

## Step 1: Scope the review target

Before dispatching anything, figure out **what** is being reviewed and **what kind** of artifact it is. Do this concisely — you don't need to read every file, just identify the boundary.

Ask yourself, and if the answer is unclear ask the user **one** question:

1. **What is the target?** (current branch diff vs `develop`? a specific PR? a specific design doc path? a specific commit range? a list of files?)
2. **Is this code review or document review?**
   - **Code review**: Java source under `src/`, migrations under `src/main/resources/db/migration/`, build files, configuration. Includes "review my branch" / "review PR #123" — these are code unless explicitly stated otherwise.
   - **Document review**: design doc (`docs/design_docs/`), implementation plan (`docs/implementation_docs/`), ADR (`docs/decisions/`), refactor plan (`docs/refactor/`), etc.
   - **Mixed**: code + accompanying docs. Treat as code review and include the docs in the target list.
3. **Output mode**: did the user explicitly ask for an `.md` file? Phrases like "파일로 만들어줘", "문서로 남겨줘", "save as md", "write to docs/review", "기록 남겨" → file mode. Otherwise default to **inline briefing** (no file).

Gather the actual target content using the appropriate tool:
- Branch diff: `git diff develop...HEAD --stat` then `git diff develop...HEAD` for the full diff. Also `git log develop..HEAD --oneline` for commit context.
- Specific PR: `gh pr view <num> --json title,body,number,url,files` and `gh pr diff <num>`.
- Specific files / docs: `Read` them.

**PR 리뷰인 경우 — 방향성 설정 (필수):**
PR description(`body`)과 기존 리뷰 코멘트를 반드시 읽어 리뷰 방향을 설정한다.

1. **PR description 분석**: `body` 에서 (a) 작성자가 이 PR로 달성하려는 의도·목표, (b) 명시된 설계 결정이나 트레이드오프, (c) 의도적으로 제외한 범위, (d) 리뷰어에게 특별히 봐달라고 요청한 항목을 추출한다. 이 내용을 **"PR 컨텍스트 요약"** 으로 정리해 Step 2 에서 각 sub-agent brief 에 포함시킨다. sub-agent 들은 이 요약을 보고 작성자의 의도에 비춰 코드를 평가해야 한다.

2. **기존 리뷰 코멘트 수집**: PR에 이미 달린 리뷰 코멘트가 있다면 다음 명령으로 가져온다.
   ```bash
   gh pr view <num> --json reviews,reviewRequests
   gh api repos/<owner>/<repo>/pulls/<num>/comments
   ```
   이미 논의됐거나 resolved 된 항목을 sub-agent brief 에 명시해, sub-agent 가 같은 내용을 re-flag 하지 않도록 한다. 아직 unresolved 인 코멘트가 있다면 해당 관점을 담당하는 sub-agent 에게 우선 검토하도록 안내한다.

Keep this scoping pass **lean** — you're handing the actual deep reading to the sub-agents. You only need enough to write each sub-agent a self-contained brief.

---

## Step 2: Dispatch sub-agents in parallel

Spawn the relevant sub-agents in **a single message** with multiple `Agent` tool calls so they run concurrently. Sub-agent 3 (test coverage) is **conditional** — only spawn it when the target is a code review (or the code-review portion of a mixed review). The other three always run.

Each sub-agent prompt template lives in `agents/`. Read the relevant template, then write the sub-agent's prompt by concatenating:
1. The template contents (the agent's role, rules, output format).
2. A **target brief**: a clear, self-contained description of what to review — file paths, diff hunks (or `git diff` command they should run), the design doc body, the PR number/URL. Sub-agents start cold; assume zero prior context.
3. **PR 컨텍스트 요약** (PR 리뷰인 경우 필수): Step 1 에서 추출한 (a) 작성자 의도·목표, (b) 명시된 설계 결정, (c) 의도적 제외 범위, (d) 리뷰어 요청 항목, (e) unresolved 리뷰 코멘트 요약을 포함한다. sub-agent 는 이 컨텍스트를 바탕으로 작성자의 의도에 비춰 코드를 평가하고, 이미 논의·resolved 된 항목은 re-flag 하지 않는다.
4. A **report-back instruction**: explicit format spec (see "Sub-agent output contract" below) and a length cap (~300–500 words per agent).
5. **Hard-rule 재강조** (sub-agent 들은 이 SKILL.md 를 읽지 않으므로 매번 prompt 에 직접 박아 넣어야 한다):
   > 출력 본문은 반드시 한국어로 작성한다 (식별자·경로·로그 토큰 제외). 문제 위치는 반드시 module-relative full path 와 line 번호를 함께 적는다. 예: `modules/admin/user/application/service/ChangeAdminUserStatusService.java:43`. `ChangeAdminUserStatusService.java` 처럼 파일명만 적거나 클래스명만 적는 것은 금지. **각 finding 의 `path:line-range` 는 반드시 실제 diff 또는 `Read` 로 확인한 파일 내용에서 가져와야 한다.** 최종 출력을 반환하기 직전, Critical 상위 3개 finding 에 대해 `Read` 툴로 해당 파일을 열어 라인 번호·코드 내용이 실제와 일치하는지 spot-verify 하라. 불일치하면 finding 을 수정하거나 제거하라. **인용(`path:line`) 없는 finding 은 오케스트레이터가 거부한다.**

For the `Agent` tool call:

| Sub-agent | `subagent_type` | `model` | When |
|-----------|----------------|---------|------|
| 1. Hexagonal architecture | `general-purpose` | `sonnet` | Always |
| 2. Business rule consistency | `general-purpose` | `opus` | Always |
| 3. Test coverage | `general-purpose` | `opus` | **Code review only** |
| 4. Transaction / DB consistency | `general-purpose` | `opus` | Always |

Set a short `description` like `"Architecture review of MOM-XXX branch"`.

### Sub-agent output contract

Tell every sub-agent to return findings in this exact structure so synthesis stays mechanical:

```
## Verdict
<one of: PASS / PASS_WITH_NITS / NEEDS_CHANGES / BLOCKER>

## Findings
### 🔴 Critical
- [path/to/File.java:42] <problem> — <why it matters> — <suggested fix>

### 🟡 Suggestion
- [path/to/File.java:88] <observation> — <suggestion>

### 🟢 Nice to have
- [path:line] <minor>

## Notes
<1–3 sentences of context the orchestrator should know — e.g. "I couldn't see the migration file referenced in PR description", or "this overlaps with sub-agent 2's domain">
```

Verdict mapping:
- **BLOCKER**: must not merge / must not implement as-designed.
- **NEEDS_CHANGES**: real issues, but scope is bounded.
- **PASS_WITH_NITS**: only Suggestion / Nice-to-have items.
- **PASS**: nothing material found.

---

## Step 3: Synthesize, don't relay

When the sub-agents return, **do not just paste their outputs back**. The user is hiring you for synthesis. Read all four reports together and:

0. **Reject uncited findings first**: scan every finding for a `path:line` or `path:line-range` token. Any finding that lacks one is dropped from the synthesis and noted as "인용 미제출로 제외됨" in the final Notes section. Do this before any other synthesis step.
1. **Deduplicate**: the same root cause often surfaces from multiple axes (e.g. a missing `@Transactional` shows up in #4 *and* in #3 as a missing test). Merge into one finding and note both perspectives.
2. **Surface contradictions**: if architecture says "use port X" and consistency says "but X has no transaction boundary", call that out — the user needs to know it's not orthogonal.
3. **Re-rank by blast radius**: a Critical finding from one agent that contradicts another's "looks fine" is more important than a unanimous Critical, because it implies a hidden assumption.
4. **Compute an overall verdict**: take the worst sub-agent verdict (BLOCKER > NEEDS_CHANGES > PASS_WITH_NITS > PASS) but downgrade if the Critical items are all duplicates of each other.

---

## Step 4: Deliver

### Default — inline briefing

If the user did **not** ask for an `.md` file, respond directly in chat using this structure. **본문은 반드시 한국어**로 작성한다 (위 Hard requirements §1 참조). 헤더는 영어 그대로 둬도 좋다. Keep it tight — the user reads this in the terminal, not as a report.

```markdown
## 리뷰 요약 — <target name>

**종합 판정**: ✅ PASS / 🔶 PASS_WITH_NITS / ⚠️ NEEDS_CHANGES / ❌ BLOCKER
**실행된 sub-agent**: <4 / 4 또는 3 / 4 — test coverage 는 문서 리뷰라 스킵>

### 🔴 Critical
- `modules/<module>/<sub>/<layer>/<File>.java:42` — <한국어로 정확한 문제 설명> — <한국어로 수정 방향> _(architecture + consistency)_

### 🟡 Suggestion
- `modules/<module>/<sub>/<layer>/<File>.java:88` — <한국어 설명> _(business-rule)_

### 🟢 Nice to have
- `modules/<...>:line` — <한국어 minor>

### 교차 관심사 (Cross-cutting concerns)
- <sub-agent 간에 수렴 / 모순한 지점, 한국어로>

### 에이전트별 판정
- Architecture: ✅ PASS — <한 줄 한국어 요약>
- Business rule: ⚠️ NEEDS_CHANGES — <한 줄 한국어>
- Test coverage: 🔶 PASS_WITH_NITS — <한 줄 한국어>
- Consistency: ✅ PASS — <한 줄 한국어>

### develop 머지 전 권장 순서
1. <한국어 우선순위 액션, 가능하면 해당 finding 의 경로:line 같이 명시>
2. ...
```

각 finding 의 경로는 반드시 `modules/...` 또는 `global/...` 또는 `src/test/.../...` 처럼 module-relative 전체 경로 + 라인 번호로 적는다. 파일명만, 클래스명만 적지 않는다.

### File mode — only if explicitly requested

If the user asked for a file, write to `docs/review/<YYYYMMDD>-<short-target-slug>.md` (e.g. `docs/review/20260503-MOM-384-kms-refactor.md`). 본문은 inline briefing 과 동일하게 한국어 + module-relative 전체 경로로 작성한다. 구조는 inline briefing 과 동일하되 추가로:
- Front matter with target, branch / PR, date, reviewer (multi-agent-review skill)
- An expanded **Per-agent reports** section reproducing each sub-agent's full output verbatim under its own H2 — this is for the audit trail. (sub-agent 들이 한국어로 응답하도록 prompt 한 결과를 그대로 옮긴다.)

After writing, tell the user the path and give them the inline summary anyway. Don't make them open the file just to see the verdict.

---

## What this skill is NOT

- Not a substitute for `/ultrareview` — that is a separate cloud-billed product. If the user invokes `/ultrareview`, do not intercept.
- Not for single-line fixes, lint cleanups, formatter runs, or pure typo edits. Those are direct work, not review work.
- Not a code-writing tool. The orchestrator and all sub-agents review only. If the user wants the issues fixed, they will follow up with a separate request — confirm before editing.

---

## Reference docs the sub-agents will need

The sub-agent templates already point each agent at the right docs, but for awareness:

- `ARCHITECTURE.md` (repo root) — Hexagonal rules. Agent 1's source of truth.
- `src/main/CLAUDE.md` — production patterns, DB profiles, security.
- `src/test/CLAUDE.md` — test tier rules. Agent 3's source of truth.
- `EXTERNAL_SYSTEM_SYNC.md` (repo root, if present) — DB ↔ external system sync rules. Relevant to Agent 4 when the change touches KMS/S3/RPC.
- `docs/CLAUDE.md` — commit conventions, doc directory map.
