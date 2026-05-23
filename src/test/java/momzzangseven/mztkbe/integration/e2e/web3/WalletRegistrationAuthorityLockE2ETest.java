package momzzangseven.mztkbe.integration.e2e.web3;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.AcquireWalletRegistrationAuthorityLockPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=4")
@DisplayName("[E2E] wallet registration authority advisory lock")
class WalletRegistrationAuthorityLockE2ETest extends E2ETestBase {

  private static final String WALLET_A = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
  private static final String WALLET_B = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

  @Autowired private AcquireWalletRegistrationAuthorityLockPort authorityLockPort;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  @DisplayName("[E-1] 동일 user + 동일 wallet 등록 락은 실제 PostgreSQL 트랜잭션에서 직렬화된다")
  void lock_sameUserAndSameWallet_blocksConcurrentTransaction() throws Exception {
    assertSecondCallWaitsForFirstLock(101L, WALLET_A, 101L, WALLET_A);
  }

  @Test
  @DisplayName("[E-2] 동일 wallet + 다른 user 등록 락은 실제 PostgreSQL 트랜잭션에서 직렬화된다")
  void lock_sameWalletAndDifferentUser_blocksConcurrentTransaction() throws Exception {
    assertSecondCallWaitsForFirstLock(201L, WALLET_A, 202L, WALLET_A);
  }

  @Test
  @DisplayName("[E-3] 동일 user + 다른 wallet 등록 락은 실제 PostgreSQL 트랜잭션에서 직렬화된다")
  void lock_sameUserAndDifferentWallet_blocksConcurrentTransaction() throws Exception {
    assertSecondCallWaitsForFirstLock(301L, WALLET_A, 301L, WALLET_B);
  }

  private void assertSecondCallWaitsForFirstLock(
      Long firstUserId, String firstWallet, Long secondUserId, String secondWallet)
      throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch firstLocked = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);
    CountDownLatch secondAttempting = new CountDownLatch(1);
    AtomicBoolean secondAcquired = new AtomicBoolean(false);
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

    Future<Boolean> first =
        executor.submit(
            () ->
                transactionTemplate.execute(
                    status -> {
                      authorityLockPort.lock(firstUserId, firstWallet);
                      firstLocked.countDown();
                      await(releaseFirst, "first lock release");
                      return true;
                    }));

    Future<Boolean> second =
        executor.submit(
            () -> {
              await(firstLocked, "first lock acquisition");
              return transactionTemplate.execute(
                  status -> {
                    secondAttempting.countDown();
                    authorityLockPort.lock(secondUserId, secondWallet);
                    secondAcquired.set(true);
                    return true;
                  });
            });

    try {
      assertThat(secondAttempting.await(3, TimeUnit.SECONDS)).isTrue();
      Thread.sleep(250);
      assertThat(secondAcquired).isFalse();

      releaseFirst.countDown();
      assertThat(first.get(3, TimeUnit.SECONDS)).isTrue();
      assertThat(second.get(3, TimeUnit.SECONDS)).isTrue();
      assertThat(secondAcquired).isTrue();
    } finally {
      releaseFirst.countDown();
      executor.shutdownNow();
    }
  }

  private static void await(CountDownLatch latch, String description) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) {
        throw new AssertionError("timed out waiting for " + description);
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while waiting for " + description, exception);
    }
  }
}
