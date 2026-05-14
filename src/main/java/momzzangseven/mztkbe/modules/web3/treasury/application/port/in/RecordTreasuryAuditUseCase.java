package momzzangseven.mztkbe.modules.web3.treasury.application.port.in;

/**
 * Input port for writing a single {@code web3_treasury_provision_audits} row.
 *
 * <p>Exists so {@code infrastructure/event} handlers can record audits without importing {@code
 * application/service} directly (ARCHITECTURE.md: event handlers are driving adapters and must
 * depend only on {@code port/in}). The implementing bean keeps the {@code REQUIRES_NEW} +
 * separate-bean pattern so audit rows survive an outer transaction rollback.
 */
public interface RecordTreasuryAuditUseCase {

  /**
   * Persist a single audit row in its own committed transaction.
   *
   * @param operatorUserId admin user id invoking the operation
   * @param walletAlias canonical wallet alias this audit row belongs to, or {@code null} for legacy
   *     / pre-alias-derivation failure rows
   * @param walletAddress {@code 0x}-prefixed wallet address, or {@code null} when the failure
   *     happened before the address could be derived
   * @param success {@code true} for successful flows, {@code false} for caught exceptions
   * @param reason simple class name of the thrown exception or a failure code, or {@code null} on
   *     success
   */
  void record(
      Long operatorUserId,
      String walletAlias,
      String walletAddress,
      boolean success,
      String reason);
}
