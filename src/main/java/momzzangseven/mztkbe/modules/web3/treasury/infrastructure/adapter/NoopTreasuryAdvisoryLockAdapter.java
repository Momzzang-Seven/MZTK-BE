package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.adapter;

import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.TreasuryAdvisoryLockPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * No-op {@link TreasuryAdvisoryLockPort} for the H2-backed {@code test} profile. Unit / slice tests
 * run single-threaded against an in-memory database and do not exercise cohort concurrency, so
 * acquiring a real advisory lock would add nothing. Mutually exclusive with {@code
 * PostgresTreasuryAdvisoryLockAdapter}, which is active on every non-{@code test} profile (dev,
 * prod, and the {@code integration} E2E profile).
 *
 * <p>Placed in {@code infrastructure/adapter/} rather than {@code infrastructure/persistence/} — it
 * has no entity↔domain mapping responsibility, mirroring {@code KmsKeyLifecycleAdapter}.
 */
@Component
@Profile("test")
public class NoopTreasuryAdvisoryLockAdapter implements TreasuryAdvisoryLockPort {

  @Override
  public void lockForAddress(String walletAddress) {
    // no-op: H2 test profile does not exercise cohort concurrency
  }
}
