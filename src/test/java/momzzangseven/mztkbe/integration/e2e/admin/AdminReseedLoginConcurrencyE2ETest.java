package momzzangseven.mztkbe.integration.e2e.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import momzzangseven.mztkbe.global.error.InvalidCredentialsException;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.admin.application.dto.AuthenticateAdminLocalCommand;
import momzzangseven.mztkbe.modules.admin.application.port.in.AuthenticateAdminLocalUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Concurrency integration test verifying that pessimistic locking on admin login correctly handles
 * concurrent recovery reseed operations.
 *
 * <p>Uses {@link TransactionTemplate} and {@link CountDownLatch} to deterministically reproduce the
 * race condition between reseed (DELETE) and login (SELECT FOR UPDATE).
 */
@TestPropertySource(
    properties = {
      "mztk.admin.recovery.anchor=test-e2e-recovery-anchor",
      "mztk.admin.seed.seed-count=2"
    })
@DisplayName("[E2E] Admin Reseed-Login Concurrency — Pessimistic Lock 검증")
class AdminReseedLoginConcurrencyE2ETest extends E2ETestBase {

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private PlatformTransactionManager txManager;
  @Autowired private AuthenticateAdminLocalUseCase authenticateAdminLocalUseCase;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;

  /**
   * Verifies that when reseed holds a row-level X-lock (via DELETE) and login attempts SELECT FOR
   * UPDATE on the same row, login blocks until reseed commits, then fails because the row no longer
   * exists.
   *
   * <pre>
   * Thread 1 (Reseed)                    Thread 2 (Login)
   * ─────────────────────────────────────────────────────
   * BEGIN TX
   * DELETE FROM admin_accounts
   *   (X-lock acquired)
   * signal: deleteExecuted ──────────→  awaiting signal...
   *                                      BEGIN TX (via service call)
   *                                      SELECT ... FOR UPDATE
   *                                        → BLOCKED on X-lock 🔒
   * COMMIT (lock released) ──────────→  SELECT re-evaluates → row gone
   *                                      → InvalidCredentialsException ✅
   * </pre>
   */
  @Test
  @DisplayName("Login blocked by reseed DELETE lock — fails with InvalidCredentialsException")
  void login_blockedByReseedLock_failsAfterReseedCommit() throws Exception {
    // given: create admin account directly in DB
    String email = "e2e-concurrency-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
    String loginId = String.valueOf(10000000 + (int) (Math.random() * 90000000));
    String plaintext = "AdminP@ss" + UUID.randomUUID().toString().substring(0, 8);
    String hash = passwordEncoder.encode(plaintext);

    jdbcTemplate.update(
        "INSERT INTO users (email, role, nickname, created_at, updated_at)"
            + " VALUES (?, 'ADMIN_SEED', 'ConcurrencyTest', NOW(), NOW())",
        email);
    Long userId =
        jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);

    jdbcTemplate.update(
        "INSERT INTO admin_accounts (user_id, login_id, password_hash, created_by,"
            + " last_login_at, password_last_rotated_at, deleted_at, created_at, updated_at)"
            + " VALUES (?, ?, ?, NULL, NULL, NULL, NULL, NOW(), NOW())",
        userId,
        loginId,
        hash);

    // Synchronization primitives
    CountDownLatch deleteExecuted = new CountDownLatch(1);
    CountDownLatch loginStarted = new CountDownLatch(1);
    AtomicReference<Throwable> loginException = new AtomicReference<>();
    AtomicReference<Throwable> reseedError = new AtomicReference<>();

    // when
    // Thread 1: Simulates reseed — DELETE within transaction, wait for login to attempt, then
    // commit
    Thread reseedThread =
        new Thread(
            () -> {
              try {
                TransactionTemplate tx = new TransactionTemplate(txManager);
                tx.execute(
                    status -> {
                      jdbcTemplate.update("DELETE FROM admin_accounts WHERE user_id = ?", userId);
                      deleteExecuted.countDown();

                      try {
                        // Wait for login thread to start its SELECT FOR UPDATE attempt
                        assertThat(loginStarted.await(10, TimeUnit.SECONDS)).isTrue();
                        // Give login thread time to actually hit the row lock
                        Thread.sleep(500);
                      } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                      }
                      return null;
                      // COMMIT here → releases X-lock
                    });
              } catch (Exception e) {
                reseedError.set(e);
              }
            });

    // Thread 2: Login — waits for DELETE, then calls service (which does SELECT FOR UPDATE)
    Thread loginThread =
        new Thread(
            () -> {
              try {
                assertThat(deleteExecuted.await(10, TimeUnit.SECONDS)).isTrue();
                loginStarted.countDown();

                // This calls AuthenticateAdminLocalService.execute() which opens its own
                // @Transactional and does SELECT ... FOR UPDATE — it will BLOCK here
                // until reseed thread commits, then find no row → exception
                authenticateAdminLocalUseCase.execute(
                    new AuthenticateAdminLocalCommand(loginId, plaintext));
              } catch (Throwable e) {
                loginException.set(e);
              }
            });

    reseedThread.start();
    loginThread.start();
    reseedThread.join(15_000);
    loginThread.join(15_000);

    // then
    assertThat(reseedError.get()).as("Reseed thread should complete without error").isNull();

    assertThat(loginException.get())
        .as("Login should fail because row was deleted while waiting for lock")
        .isNotNull()
        .isInstanceOf(InvalidCredentialsException.class);

    // Verify the row is actually gone
    Long remainingCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM admin_accounts WHERE user_id = ?", Long.class, userId);
    assertThat(remainingCount).isZero();
  }

  /**
   * Verifies that when login acquires the row lock first (SELECT FOR UPDATE), reseed's DELETE
   * blocks until login commits, and both operations complete successfully.
   */
  @Test
  @DisplayName("Reseed blocked by login lock — both complete after login commits")
  void reseed_blockedByLoginLock_completesAfterLoginCommit() throws Exception {
    // given
    String email = "e2e-concurrency-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
    String loginId = String.valueOf(10000000 + (int) (Math.random() * 90000000));
    String plaintext = "AdminP@ss" + UUID.randomUUID().toString().substring(0, 8);
    String hash = passwordEncoder.encode(plaintext);

    jdbcTemplate.update(
        "INSERT INTO users (email, role, nickname, created_at, updated_at)"
            + " VALUES (?, 'ADMIN_SEED', 'ConcurrencyTest', NOW(), NOW())",
        email);
    Long userId =
        jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);

    jdbcTemplate.update(
        "INSERT INTO admin_accounts (user_id, login_id, password_hash, created_by,"
            + " last_login_at, password_last_rotated_at, deleted_at, created_at, updated_at)"
            + " VALUES (?, ?, ?, NULL, NULL, NULL, NULL, NOW(), NOW())",
        userId,
        loginId,
        hash);

    CountDownLatch loginLockAcquired = new CountDownLatch(1);
    CountDownLatch reseedStarted = new CountDownLatch(1);
    AtomicReference<Throwable> loginError = new AtomicReference<>();
    AtomicReference<Throwable> reseedError = new AtomicReference<>();

    // when
    // Thread 1: Login — acquires SELECT FOR UPDATE lock, signals, waits, then commits
    Thread loginThread =
        new Thread(
            () -> {
              try {
                TransactionTemplate tx = new TransactionTemplate(txManager);
                tx.execute(
                    status -> {
                      // Acquire pessimistic lock manually (simulating the service behavior)
                      jdbcTemplate.queryForMap(
                          "SELECT * FROM admin_accounts"
                              + " WHERE login_id = ? AND deleted_at IS NULL FOR UPDATE",
                          loginId);
                      loginLockAcquired.countDown();

                      try {
                        assertThat(reseedStarted.await(10, TimeUnit.SECONDS)).isTrue();
                        // Give reseed thread time to hit the lock
                        Thread.sleep(500);
                      } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                      }
                      return null;
                      // COMMIT here → releases lock, reseed DELETE can proceed
                    });
              } catch (Exception e) {
                loginError.set(e);
              }
            });

    // Thread 2: Reseed DELETE — waits for login lock, then attempts DELETE (blocked until commit)
    Thread reseedThread =
        new Thread(
            () -> {
              try {
                assertThat(loginLockAcquired.await(10, TimeUnit.SECONDS)).isTrue();
                reseedStarted.countDown();

                TransactionTemplate tx = new TransactionTemplate(txManager);
                tx.execute(
                    status -> {
                      // This DELETE will BLOCK until login thread commits
                      jdbcTemplate.update("DELETE FROM admin_accounts WHERE user_id = ?", userId);
                      return null;
                    });
              } catch (Exception e) {
                reseedError.set(e);
              }
            });

    loginThread.start();
    reseedThread.start();
    loginThread.join(15_000);
    reseedThread.join(15_000);

    // then
    assertThat(loginError.get()).as("Login thread should complete without error").isNull();
    assertThat(reseedError.get())
        .as("Reseed thread should complete without error after login releases lock")
        .isNull();

    // Verify the row is deleted after both complete
    Long remainingCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM admin_accounts WHERE user_id = ?", Long.class, userId);
    assertThat(remainingCount).isZero();
  }
}
