package momzzangseven.mztkbe.modules.web3.treasury.domain.event;

/**
 * Domain events that drive post-commit KMS side effects for a treasury cohort. A cohort is the set
 * of wallet rows sharing one {@code (treasury_address, kms_key_id)} pair.
 *
 * <p>Cardinality differs per variant — read carefully:
 *
 * <ul>
 *   <li>{@link BoundAlias} — <b>alias-level</b>. Fires once per sibling alias; each publication
 *       triggers one KMS {@code CreateAlias} binding that alias to the shared key. AWS KMS has no
 *       batch alias API, so N aliases mean N {@code CreateAlias} calls — this is the documented
 *       exception to "one KMS mutation per cohort".
 *   <li>{@link Disabled} — <b>key-level</b>. Fires exactly once per cohort; triggers one KMS {@code
 *       DisableKey}.
 *   <li>{@link ScheduledDeletion} — <b>key-level</b>. Fires exactly once per cohort; triggers one
 *       KMS {@code ScheduleKeyDeletion}.
 * </ul>
 *
 * <p>The key-level variants carry the <b>trigger alias</b> — the alias the operator invoked the
 * lifecycle endpoint on. It is not used to scope the KMS mutation (that is keyed by {@code
 * kmsKeyId}); it only labels the single {@code web3_treasury_kms_audits} row, whose {@code
 * wallet_alias} column is NOT NULL.
 */
public sealed interface KeyLifecycleEvent {

  /**
   * Bind one wallet alias to a KMS key. Fires once per sibling alias in a cohort.
   *
   * @param kmsKeyId the (possibly shared) KMS key id
   * @param walletAlias the alias to bind
   * @param walletAddress the cohort's treasury address
   * @param operatorUserId operator who triggered the lifecycle operation
   */
  record BoundAlias(String kmsKeyId, String walletAlias, String walletAddress, Long operatorUserId)
      implements KeyLifecycleEvent {}

  /**
   * Disable the cohort's shared KMS key. Fires once per cohort.
   *
   * @param kmsKeyId the cohort's shared KMS key id
   * @param walletAlias the trigger alias — labels the KMS audit row only
   * @param walletAddress the cohort's treasury address
   * @param operatorUserId operator who triggered the disable
   */
  record Disabled(String kmsKeyId, String walletAlias, String walletAddress, Long operatorUserId)
      implements KeyLifecycleEvent {}

  /**
   * Schedule deletion of the cohort's shared KMS key. Fires once per cohort.
   *
   * @param kmsKeyId the cohort's shared KMS key id
   * @param walletAlias the trigger alias — labels the KMS audit row only
   * @param walletAddress the cohort's treasury address
   * @param operatorUserId operator who triggered the archive
   * @param pendingWindowDays KMS pending-deletion window in days
   */
  record ScheduledDeletion(
      String kmsKeyId,
      String walletAlias,
      String walletAddress,
      Long operatorUserId,
      int pendingWindowDays)
      implements KeyLifecycleEvent {}
}
