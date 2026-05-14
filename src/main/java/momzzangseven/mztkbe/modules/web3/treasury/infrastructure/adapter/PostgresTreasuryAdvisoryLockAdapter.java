package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.adapter;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.TreasuryAdvisoryLockPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Postgres-backed {@link TreasuryAdvisoryLockPort}. Serializes treasury lifecycle operations on the
 * same {@code treasury_address} via {@code pg_advisory_xact_lock(bigint)} — a transaction-scoped
 * lock that releases automatically on commit or rollback, so there is no unlock path to leak.
 *
 * <p>Active on every non-{@code test} profile (dev, prod, and the {@code integration} E2E profile);
 * mutually exclusive with {@code NoopTreasuryAdvisoryLockAdapter} which serves the H2-backed {@code
 * test} profile.
 *
 * <p>The lock key is a stable 64-bit hash of the lowercased address computed in Java rather than
 * via SQL {@code hashtext()} so the value is reproducible and independent of the database. Hash
 * collisions only widen the serialization scope (two unrelated addresses share a lock) — they never
 * compromise correctness.
 */
@Component
@Profile("!test")
public class PostgresTreasuryAdvisoryLockAdapter implements TreasuryAdvisoryLockPort {

  @PersistenceContext private EntityManager entityManager;

  @Override
  public void lockForAddress(String walletAddress) {
    if (walletAddress == null || walletAddress.isBlank()) {
      throw new Web3InvalidInputException("walletAddress is required");
    }
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException(
          "lockForAddress must be called within an active transaction so the advisory lock is "
              + "released on transaction completion");
    }
    entityManager
        .createNativeQuery("SELECT pg_advisory_xact_lock(?1)")
        .setParameter(1, lockKey(walletAddress))
        .getSingleResult();
  }

  /** Stable 64-bit FNV-1a hash of the lowercased address. */
  private static long lockKey(String walletAddress) {
    final long fnvOffsetBasis = 0xcbf29ce484222325L;
    final long fnvPrime = 0x100000001b3L;
    long hash = fnvOffsetBasis;
    for (byte b : walletAddress.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)) {
      hash ^= (b & 0xff);
      hash *= fnvPrime;
    }
    return hash;
  }
}
