# Sponsor nonce replacement runbook

## Scope

The MOM-458 nonce tracker intentionally does not auto-replace a stuck sponsor nonce yet.
When the domain decision is `REPLACE_LOWEST_NONCE`, the coordinator moves the slot to
`OPERATOR_REVIEW_REQUIRED` and blocks new issuance for the sponsor until an operator closes the
lowest blocking slot.

This is deliberate: replacement requires a new signed transaction for the same nonce, gas-price
bump policy, superseding the previous attempt, and receipt reconciliation across RPC providers.
Until that flow is implemented, manual review is safer than automatic replacement.

## Trigger

Use this runbook when admin nonce slot review shows one of these conditions:

- lowest open nonce is `STUCK`
- lowest open nonce is timed-out `BROADCASTED` and replacement-eligible
- coordinator reason is `REPLACEMENT_REQUIRES_OPERATOR_IMPLEMENTATION`
- sponsor issuance is blocked by `OPERATOR_REVIEW_REQUIRED`

## Investigation checklist

1. Confirm the sponsor scope: `chain_id`, `from_address`, and lowest blocking `nonce`.
2. Query both RPC providers for `eth_getTransactionCount` with `latest` and `pending`.
3. Search the explorer and RPC receipt by the slot attempt `tx_hash`.
4. Compare the slot's `active_tx_id`, `active_attempt_id`, `status`, and retained evidence flags.
5. Do not close a slot as `DROPPED` if any raw transaction, tx hash, signing evidence, broadcast
   evidence, or receipt evidence exists.

## Resolution paths

### Receipt exists and belongs to backend transaction

Close the slot as `CONSUMED`.

Required evidence:

- receipt status is known
- `consumed_tx_id` points to the backend transaction
- `has_receipt_evidence = true`
- tx status is updated consistently through the transaction outcome flow

### Chain nonce advanced but backend receipt cannot be proven

Close the slot as `CONSUMED_UNKNOWN`.

Required evidence:

- latest nonce is greater than the slot nonce on at least one trusted RPC provider
- retained evidence explains why backend ownership cannot be proven
- `consumed_external_evidence_id` points to the operator/system evidence row

### No chain-reachable evidence exists

Close the slot as `DROPPED`.

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
