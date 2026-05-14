package momzzangseven.mztkbe.modules.web3.treasury.application.port.out;

/**
 * Serializes treasury lifecycle operations (provision / disable / archive) that target the same
 * {@code treasury_address}. A cohort is the set of wallet rows sharing one {@code
 * (treasury_address, kms_key_id)} pair; concurrent lifecycle calls on the same address must not
 * interleave or they could split a cohort's state or double-fire KMS mutations.
 *
 * <p>Every lifecycle endpoint must call {@link #lockForAddress(String)} immediately after deriving
 * the address, inside the surrounding transaction. The lock is released automatically on
 * transaction completion.
 *
 * <p>The recommended implementation uses a Postgres advisory lock ({@code pg_advisory_xact_lock});
 * H2 / unit-test contexts wire a no-op implementation since those tests do not exercise
 * concurrency.
 */
public interface TreasuryAdvisoryLockPort {

  /**
   * Acquires a transaction-scoped advisory lock keyed on {@code walletAddress}. Blocks until the
   * lock is available; released when the calling transaction commits or rolls back.
   *
   * @param walletAddress {@code 0x}-prefixed treasury address whose cohort is being mutated
   */
  void lockForAddress(String walletAddress);
}
