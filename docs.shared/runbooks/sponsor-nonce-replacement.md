# Sponsor nonce replacement runbook

## Scope

The MOM-458 nonce tracker intentionally does not auto-replace a stuck sponsor nonce yet.
When the domain decision is `REPLACE_LOWEST_NONCE`, the coordinator moves the slot to
`OPERATOR_REVIEW_REQUIRED` and blocks new issuance for the sponsor until an operator closes the
lowest blocking slot.

This is deliberate: replacement requires a new signed transaction for the same nonce, gas-price
bump policy, superseding the previous attempt, and receipt reconciliation across RPC providers.
Until that flow is implemented, manual review is safer than automatic replacement.

There is currently no public/admin write API for closing nonce slots. The admin nonce-slot API is
read-only. Treat this document as an investigation and escalation runbook, not as permission to run
ad-hoc SQL.

## Migration rollout checklist

Before applying the MOM-458 nonce-slot migrations to production-like data, run V084 on a staging
snapshot with comparable `web3_transactions` volume.

- Record the row counts for `web3_transactions`, rows with `nonce IS NOT NULL`, and active
  `CREATED`/`SIGNED`/`PENDING`/`UNCONFIRMED` transactions.
- Measure elapsed time for lower-case address normalization, `web3_nonce_slot_attempts` backfill,
  `web3_nonce_slots` backfill, and constraint validation.
- Watch PostgreSQL locks and blocked sessions during the normalization and validation statements.
- If V084 fails after a concurrent index statement, rerun the migration only after confirming the
  invalid-index cleanup path ran or manually dropping the invalid index with a reviewed DB change.
- Do not promote the migration if duplicate `(chain_id, lower(from_address), nonce)` transaction
  scopes are reported by the preflight guard. Resolve the duplicate transaction history first.

## Trigger

Use this runbook when admin nonce slot review shows one of these conditions:

- lowest open nonce is `STUCK`
- lowest open nonce is timed-out `BROADCASTED` and replacement-eligible
- coordinator reason is `REPLACEMENT_REQUIRES_OPERATOR_IMPLEMENTATION`
- sponsor issuance is blocked by `OPERATOR_REVIEW_REQUIRED`

## Alert sources

Use logs/monitoring to make sure operator-review states do not depend on user reports.

- Wallet registration retry flow emits this warning when a receipt-timeout approval is converted to
  `SPONSOR_NONCE_BLOCKED`:
  - log message prefix: `wallet registration sponsor nonce blocked`
  - fields: `registrationId`, `walletAddress`, `latestExecutionIntentId`, `errorCode`
- Alert condition:
  - page or create an incident when the warning appears in production.
  - group alerts by `walletAddress` and `latestExecutionIntentId`.
  - if multiple alerts share the same sponsor scope, inspect `GET /admin/web3/nonce-slots`.
- Future preferred metric:
  - `wallet_registration_sponsor_nonce_blocked_total`
  - labels: `chainId`, `status`, `errorCode`
  - alert on any increase over a short production window.

## Availability SLO

Sponsor nonce replacement is a manual recovery path until same-nonce replacement is implemented.
Treat the following as production availability targets:

- A sponsor scope must not stay in `OPERATOR_REVIEW_REQUIRED` for more than 15 minutes without an
  acknowledged incident.
- A sponsor scope must not block wallet registration or reward issuance for more than 30 minutes
  without an owner, evidence collection status, and explicit repair decision.
- If two or more users hit `SPONSOR_NONCE_BLOCKED` for the same sponsor scope within 10 minutes,
  escalate to backend on-call immediately.
- If the lowest blocking nonce cannot be classified as `CONSUMED`, `CONSUMED_UNKNOWN`, or
  `DROPPED` within 30 minutes, freeze new sponsor-dependent rollout activity until a maintainer
  decides whether to disable the affected flow or prepare a reviewed repair.

Recommended alerts:

- `OPERATOR_REVIEW_REQUIRED` slot count by `(chain_id, from_address)` is greater than zero for 15
  minutes.
- Oldest `OPERATOR_REVIEW_REQUIRED.updated_at` age exceeds 15 minutes.
- `SPONSOR_NONCE_BLOCKED` wallet-registration count increases for the same sponsor scope.
- Reward issuer emits repeated `SPONSOR_NONCE_WAIT_FOR_OPEN_WINDOW` or
  `SPONSOR_NONCE_OPERATOR_REVIEW_REQUIRED` for the same sponsor scope.

## Investigation checklist

1. Confirm the sponsor scope: `chain_id`, `from_address`, and lowest blocking `nonce`.
2. Query both RPC providers for `eth_getTransactionCount` with `latest` and `pending`.
3. Search the explorer and RPC receipt by the slot attempt `tx_hash`.
4. Compare the slot's `active_tx_id`, `active_attempt_id`, `status`, and retained evidence flags.
5. Do not close a slot as `DROPPED` if any raw transaction, tx hash, signing evidence, broadcast
   evidence, or receipt evidence exists.
6. Record the investigation result in the incident ticket before requesting any state repair.

## Resolution paths

### Receipt exists and belongs to backend transaction

Target repair state: `CONSUMED`.

Required evidence:

- receipt status is known
- `consumed_tx_id` points to the backend transaction
- `has_receipt_evidence = true`
- tx status is updated consistently through the transaction outcome flow

### Chain nonce advanced but backend receipt cannot be proven

Target repair state: `CONSUMED_UNKNOWN`.

Required evidence:

- latest nonce is greater than the slot nonce on at least one trusted RPC provider
- retained evidence explains why backend ownership cannot be proven
- `consumed_external_evidence_id` points to the operator/system evidence row

### No chain-reachable evidence exists

Target repair state: `DROPPED`.

Required evidence:

- no raw transaction was retained
- no tx hash is known
- no signing/broadcast/receipt evidence exists
- release reason explains the failed reservation or signing path

## Current limitation

Do not create a replacement attempt manually unless a dedicated implementation exists for all of:

- same-nonce replacement signing
- gas bump policy
- superseding previous attempt
- worker pickup of replacement attempt
- receipt reconciliation for both old and replacement tx hashes

Until then, resolve the slot to `CONSUMED`, `CONSUMED_UNKNOWN`, or `DROPPED` based on evidence.
After the lowest blocking slot is closed, normal sponsor issuance resumes through the coordinator.

## Escalation path

1. Admin/CS confirms the blocking slot in `GET /admin/web3/nonce-slots`.
2. Admin/CS attaches RPC/explorer evidence and the target repair state to the incident ticket.
3. Backend maintainer reviews the evidence against this runbook.
4. If repair is safe, backend maintainer prepares a one-off repair using the same lifecycle
   invariants as `NonceSlotLifecycleService`.
5. A second maintainer reviews the repair before execution.
6. After repair, admin/CS re-runs the nonce-slot read API and confirms sponsor issuance resumes.

## Emergency DB repair guardrails

Direct DB mutation is a last resort only. Do not update only `web3_nonce_slots.status`.

Any emergency repair must satisfy all of these conditions:

- execute in a single database transaction
- preserve `web3_transactions`, slot, attempt, and evidence consistency
- insert or reference durable evidence before changing the slot state
- update consumed/released ids and timestamps together with the status
- leave an incident ticket with the exact SQL, evidence, reviewer, executor, and execution time
- never mark `DROPPED` when raw tx, tx hash, signing evidence, broadcast evidence, or receipt
  evidence exists
- never create a replacement attempt manually

Preferred long-term fix: add an audited admin recovery command that calls the lifecycle validator
instead of relying on emergency SQL.
