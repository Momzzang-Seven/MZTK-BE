# EXTERNAL_SYSTEM_SYNC.md — External-System ↔ DB Transaction Rules

This document defines the rules for synchronizing JPA/DB transactions with external-system
side effects (AWS KMS, S3, on-chain RPC, third-party APIs). Treat it as a contract: any flow
that mutates both the DB and an external system must satisfy every rule below.

The patterns generalise — KMS is the canonical reference implementation, but the rules apply
to every external mutation.

---

## The Core Race

A `@Transactional` method's body-level `try/catch` only catches exceptions thrown inside the
body. The actual `commit()` happens at the Spring AOP proxy boundary, *after* the method
returns. So when the body succeeds and triggers an external mutation, then the proxy commit
fails:

- The external system has already been mutated.
- The DB silently rolls back.
- The body-level catch never runs.

`REQUIRES_NEW` audits suffer the mirror image: an inner audit transaction commits before the
outer commit, so a "success" audit row can survive an outer rollback. Every rule below exists
to eliminate one of these silent inconsistencies.

---

## Rule 1 — DB-First, External-After-Commit (Default)

Inside `@Transactional`, do DB writes and `publishEvent` only. Move every external-system
mutation to an `@TransactionalEventListener(phase = AFTER_COMMIT)` handler in
`infrastructure/event/`.

```java
@Transactional
public Result execute(Command cmd) {
    Wallet wallet = loadPort.loadByAlias(...).disable(clock);
    Wallet saved  = savePort.save(wallet);
    publisher.publishEvent(new WalletDisabledEvent(saved.alias(), saved.kmsKeyId(), ...));
    return Result.from(saved);
}

// infrastructure/event/
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onDisabled(WalletDisabledEvent ev) {
    try { disableExternalUseCase.execute(...); }
    catch (RuntimeException ex) { log.warn("...; audit row recorded for operator follow-up", ex); }
}
```

**Why.** The DB row commits first. If the commit fails, no external mutation happens. If the
external call fails afterward, the DB remains the source of truth and the read path stays
consistent. The residual failure mode "DB committed, external not yet mutated" is recorded in
a dedicated audit table (e.g. `web3_treasury_kms_audits`) for idempotent operator retry.

---

## Rule 2 — Pre-Commit External Calls Need `TransactionSynchronization`

When the use case **must** call the external system before the DB commits (e.g. Provision:
`createKey → importKeyMaterial → sanity-sign → save`), the body-level `try/catch` is not
enough. Register a `TransactionSynchronization` immediately after the first irreversible
external call so cleanup runs at the proxy commit boundary too.

```java
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override public void afterCompletion(int status) {
        switch (status) {
            case STATUS_COMMITTED:   return;
            case STATUS_ROLLED_BACK:
                if (cleanupInvoked.compareAndSet(false, true)) cleanupExternal(externalId);
                return;
            case STATUS_UNKNOWN:
            default:
                log.error("...skipping cleanup to avoid orphaning a possibly-committed row");
                auditRecorder.record(operator, target, false, "TX_STATUS_UNKNOWN");
        }
    }
});
```

**Hard rules:**

1. **Cleanup runs only on `STATUS_ROLLED_BACK`.** `STATUS_UNKNOWN` (or any unrecognised
   status) means the commit may actually have succeeded — cleaning up would orphan a
   committed DB row pointing at a torn-down external resource. Skip cleanup, record an alert
   audit, escalate to operators.
2. **`AtomicBoolean cleanupInvoked` interlock** is shared between the body's `catch` block
   and the synchronization. On in-method exceptions both fire; the interlock guarantees
   single execution.
3. **Guard registration** with `TransactionSynchronizationManager.isSynchronizationActive()`
   so unit tests that exercise the service without a Spring transaction context do not blow
   up at registration time.

---

## Rule 3 — Audit Split: Failure Inline, Success Post-Commit

The two audit kinds have opposite requirements:

| Kind        | Where                                       | Tx propagation                     | Why                                                                                                                                              |
|-------------|---------------------------------------------|------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------|
| **Failure** | service `catch` block, inline               | `REQUIRES_NEW` (on the recorder)   | Must survive the outer rollback. "We tried and it blew up" has to be recorded.                                                                   |
| **Success** | `infrastructure/event/` AFTER_COMMIT handler | none (recorder still REQUIRES_NEW) | Must NOT land if the outer commit fails. Inline `REQUIRES_NEW` would leave "audit success / DB rolled back" inconsistencies invisible to operators. |

```java
// failure — inline
catch (RuntimeException e) {
    auditRecorder.record(operator, target, false, e.getClass().getSimpleName());
    throw e;
}

// success — AFTER_COMMIT handler
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onSucceeded(SomethingDoneEvent ev) {
    try { auditRecorder.record(ev.operator(), ev.target(), true, null); }
    catch (RuntimeException ex) { log.warn("success audit failed post-commit ...", ex); }
}
```

Bean-validation / null-command failures short-circuit before any audit and are not recorded;
the controller's `@Valid` chain is the source of truth for those cases.

---

## Rule 4 — Every AFTER_COMMIT Handler: try/catch Swallow + log

Spring's `TransactionSynchronization.afterCommit` invokes registered listeners in a loop with
**no per-listener exception isolation**. A throwing handler will:

1. Surface a 500 to the caller even though the DB has already committed.
2. Skip every subsequent AFTER_COMMIT listener registered for the same event.

For a Provision flow with two AFTER_COMMIT listeners (success-audit + alias bind), this
becomes "audit insert failed → alias never bound → unrecoverable half-bound state".

**Rule:** Every `@TransactionalEventListener(AFTER_COMMIT)` body must be wrapped:

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onX(XEvent ev) {
    try { useCase.execute(...); }
    catch (RuntimeException ex) {
        log.warn("X failed post-commit for {} (operator={}); audit row recorded for operator follow-up",
                 ev.target(), ev.operatorUserId(), ex);
    }
}
```

Operator visibility comes from a dedicated `*_audits` table written inside the use case
(`REQUIRES_NEW`), never from the listener exception itself.

---

## Rule 5 — Idempotent Recovery for "DB Committed, External Failed"

Rule 1 reduces every silent inconsistency to a single, operator-actionable state: the DB
committed but the external mutation has not (yet) succeeded. Recovery must be idempotent so
operators can retry the same input safely.

- **Disable / schedule-deletion / send-message** flows: re-running the same command must be a
  no-op when the external state already matches. Use lookup-then-act adapters, not blind
  issue.
- **Provision-style flows** (where a resource was created externally but the DB rolled back):
  on retry with the same input, query the external state. If a stale resource exists, enter
  **repair mode** — skip resource creation, only re-fire the AFTER_COMMIT handler that
  finishes the binding (see `ProvisionTreasuryKeyService` alias-repair branch).
- **Existence checks** must distinguish "row exists but missing external binding" (allow
  backfill) from "row + binding both exist" (true duplicate).

---

## Rule 6 — Read Path Gates on DB, Not External State

External-system state is best-effort consistent with the DB after Rules 1–5; the read path
must not depend on it. Sign / send / withdraw / publish paths gate on the DB row's status
(`ACTIVE` / `DISABLED` / `ARCHIVED`) before invoking the external system.

This makes "DB committed, external not yet mutated" safe by construction: even if a late KMS
disable has not run, the wallet is already unusable because the DB says so.

---

## Decision Checklist

When designing a new flow that mutates both the DB and an external system, walk through this
in order:

1. **Can the external mutation be deferred to AFTER_COMMIT?** → Default yes. Apply Rule 1.
2. **If no, does it need the DB row to be visible?** → It cannot, since the outer transaction
   has not committed. Restructure, or apply Rule 2 (synchronization + cleanup-on-rollback +
   `STATUS_UNKNOWN` handling).
3. **Where do success/failure audits land?** → Rule 3.
4. **Are all AFTER_COMMIT handlers exception-isolated?** → Rule 4.
5. **What does retry of the same input look like?** → Rule 5 (idempotent / repair mode).
6. **Does the read path gate on DB status?** → Rule 6.

---

## Reference Implementations (`modules/web3/treasury/`)

| Concern                                                          | File                                                                       |
|------------------------------------------------------------------|----------------------------------------------------------------------------|
| DB-first + AFTER_COMMIT split (Rule 1)                           | `DisableTreasuryWalletService`, `ArchiveTreasuryWalletService`             |
| Pre-commit external + synchronization + `STATUS_UNKNOWN` (Rule 2) | `ProvisionTreasuryKeyService#registerCleanupOnRollback`                    |
| Success-audit AFTER_COMMIT + swallow (Rule 3, 4)                 | `TreasuryAuditEventHandler`                                                |
| External-side AFTER_COMMIT handler with swallow (Rule 4)         | `TreasuryWalletProvisionedKmsHandler`, `TreasuryWalletDisabledKmsHandler`  |
| Idempotent retry / repair mode (Rule 5)                          | `ProvisionTreasuryKeyService#handleExistingProvisionedRow`                 |
| Operator visibility table                                        | `web3_treasury_kms_audits`                                                 |
