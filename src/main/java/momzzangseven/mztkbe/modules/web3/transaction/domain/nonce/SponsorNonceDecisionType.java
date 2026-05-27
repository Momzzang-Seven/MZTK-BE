package momzzangseven.mztkbe.modules.web3.transaction.domain.nonce;

/** Explicit decision names for sponsor nonce coordination logs and audits. */
public enum SponsorNonceDecisionType {
  ISSUE_NONCE,
  WAIT_FOR_OPEN_WINDOW,
  WAIT_FOR_IN_FLIGHT_SLOT,
  WAIT_FOR_IN_FLIGHT_REPLACEMENT,
  REPLACE_LOWEST_NONCE,
  DROP_UNBROADCASTABLE_RESERVATION,
  CONSUME_KNOWN_NONCE,
  CONSUME_UNKNOWN_NONCE,
  OPERATOR_REVIEW_REQUIRED,
  RPC_DISAGREEMENT,
  REPAIR_DB_SLOT_GAP,
  REPAIR_CHAIN_PENDING_GAP
}
