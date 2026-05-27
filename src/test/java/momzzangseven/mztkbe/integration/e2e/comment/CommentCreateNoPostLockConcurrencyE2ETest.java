package momzzangseven.mztkbe.integration.e2e.comment;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * MOM-459 regression guard — backed by real PostgreSQL row locks.
 *
 * <p>Before MOM-459, {@code PostPersistenceAdapter.loadPost} auto-escalated to {@code
 * PESSIMISTIC_WRITE} whenever the calling transaction was read-write. That made {@code
 * CommentService.createComment} (annotated {@code @Transactional}) silently acquire {@code SELECT …
 * FOR UPDATE} on the {@code posts} row even though it never mutates the post. Under load, every
 * concurrent comment on a hot question serialized on that row lock — the bottleneck the load test
 * surfaced.
 *
 * <p>This test holds a {@code FOR NO KEY UPDATE} lock on the post row from a holder thread and
 * asserts that {@code POST /posts/{id}/comments} completes <em>before</em> the holder releases. We
 * deliberately use {@code FOR NO KEY UPDATE} (not {@code FOR UPDATE}):
 *
 * <ul>
 *   <li>{@code FOR NO KEY UPDATE} still conflicts with the old auto-{@code FOR UPDATE} — so a
 *       regression would deadlock or time out.
 *   <li>{@code FOR NO KEY UPDATE} does <em>not</em> conflict with the {@code FOR KEY SHARE} that
 *       PostgreSQL takes for the {@code comments → posts} foreign-key check — so FK enforcement
 *       does not contaminate the signal.
 * </ul>
 *
 * <p>Net result: this test will time out on a regression and pass on the fix, isolating the change
 * we care about.
 */
@TestPropertySource(
    properties = {
      "web3.chain-id=1337",
      "web3.eip712.chain-id=1337",
      "web3.eip7702.enabled=false",
      "web3.reward-token.enabled=false",
      // The integration profile pins Hikari to maximum-pool-size=4 to keep e2e light. This
      // test exercises N=5/10 concurrent commenters on the same post and must observe whether
      // they parallelise — not whether they exhaust the pool. We override the pool just for
      // this class so a pool-size constraint cannot mask the row-lock signal we are testing.
      "spring.datasource.hikari.maximum-pool-size=20",
      "spring.datasource.hikari.minimum-idle=0"
    })
@DisplayName("[E2E] CommentService.createComment acquires no posts row lock (MOM-459)")
class CommentCreateNoPostLockConcurrencyE2ETest extends E2ETestBase {

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PlatformTransactionManager transactionManager;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  @Test
  @DisplayName("createComment completes while a holder keeps FOR NO KEY UPDATE on the post row")
  void createComment_doesNotWaitOnPostsRowLock() throws Exception {
    TestUser postAuthor = signupAndLogin("comment-lock-author");
    TestUser commenter = signupAndLogin("comment-lock-commenter");
    Long postId = createFreePost(postAuthor.accessToken(), "post under lock contention test");

    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch holderAcquired = new CountDownLatch(1);
    CountDownLatch requestDone = new CountDownLatch(1);
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

    try {
      Future<?> holderFuture =
          executor.submit(
              () -> {
                transactionTemplate.executeWithoutResult(
                    status -> {
                      jdbcTemplate.queryForMap(
                          "SELECT id FROM posts WHERE id = ? FOR NO KEY UPDATE", postId);
                      holderAcquired.countDown();
                      awaitLatch(requestDone, "requestDone");
                    });
                return null;
              });

      Future<ResponseEntity<String>> requestFuture =
          executor.submit(
              () -> {
                awaitLatch(holderAcquired, "holderAcquired");
                return restTemplate.exchange(
                    baseUrl() + "/posts/" + postId + "/comments",
                    HttpMethod.POST,
                    new HttpEntity<>(
                        Map.of("content", "comment under contention"),
                        bearerJsonHeaders(commenter.accessToken())),
                    String.class);
              });

      ResponseEntity<String> response = requestFuture.get(5, TimeUnit.SECONDS);
      requestDone.countDown();
      holderFuture.get(5, TimeUnit.SECONDS);

      assertThat(response.getStatusCode())
          .as("createComment should return 2xx — body=%s", response.getBody())
          .isEqualTo(HttpStatus.OK);
      JsonNode root = objectMapper.readTree(response.getBody());
      assertThat(root.at("/data/commentId").asLong()).isPositive();

      Long postLockCountDuringRequest =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM comments WHERE post_id = ?", Long.class, postId);
      assertThat(postLockCountDuringRequest)
          .as("the comment was persisted while the holder still held FOR NO KEY UPDATE on posts")
          .isEqualTo(1L);
    } finally {
      requestDone.countDown();
      executor.shutdownNow();
      executor.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  /**
   * Scenario A — direct lock-absence proof via {@code FOR UPDATE NOWAIT} probe.
   *
   * <p>The original Tier 3 recommendation in {@code
   * docs.local/analysis/post-comment-write-lock-bottleneck.md} was to assert that {@code pg_locks}
   * carries no {@code posts}-row lock while concurrent {@code createComment} are in flight.
   * PostgreSQL stores row-lock holders in tuple xmax — not in {@code pg_locks} — so a direct {@code
   * pg_locks} query cannot observe the holder. We use the equivalent: a separate transaction that
   * repeatedly attempts {@code SELECT … FOR NO KEY UPDATE NOWAIT} on the post row. If any commenter
   * is holding a conflicting row lock, NOWAIT raises {@link CannotAcquireLockException} ({@code
   * SQLState 55P03}). With MOM-459's lock-free {@code loadPost}, the probe must succeed every
   * iteration.
   *
   * <p>We deliberately probe with {@code FOR NO KEY UPDATE} (not {@code FOR UPDATE}) — same
   * reasoning as the single-holder scenario above: it still conflicts with the old auto-{@code FOR
   * UPDATE} (so a regression makes NOWAIT fail) but does <em>not</em> conflict with the {@code FOR
   * KEY SHARE} that PostgreSQL takes for {@code comments → posts} FK enforcement (so the INSERT
   * itself does not contaminate the signal).
   */
  @Test
  @DisplayName(
      "concurrent createComment x N — posts row lock-free proven by FOR NO KEY UPDATE NOWAIT"
          + " probe")
  void concurrentCreateCommentLeavesPostsRowLockFreeProvenByNowaitProbe() throws Exception {
    int concurrency = 5;
    TestUser postAuthor = signupAndLogin("comment-nowait-author");
    Long postId = createFreePost(postAuthor.accessToken(), "post under NOWAIT probe");

    List<TestUser> commenters = new ArrayList<>(concurrency);
    for (int i = 0; i < concurrency; i++) {
      commenters.add(signupAndLogin("comment-nowait-commenter-" + i));
    }

    ExecutorService executor = Executors.newFixedThreadPool(concurrency + 1);
    CountDownLatch start = new CountDownLatch(1);
    AtomicBoolean shouldStop = new AtomicBoolean(false);
    AtomicInteger probeSuccesses = new AtomicInteger(0);
    AtomicInteger probeFailures = new AtomicInteger(0);
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

    try {
      List<Future<ResponseEntity<String>>> commentFutures = new ArrayList<>(concurrency);
      for (TestUser commenter : commenters) {
        commentFutures.add(
            executor.submit(
                () -> {
                  awaitLatch(start, "start");
                  return restTemplate.exchange(
                      baseUrl() + "/posts/" + postId + "/comments",
                      HttpMethod.POST,
                      new HttpEntity<>(
                          Map.of("content", "concurrent comment"),
                          bearerJsonHeaders(commenter.accessToken())),
                      String.class);
                }));
      }

      Future<?> probeFuture =
          executor.submit(
              () -> {
                awaitLatch(start, "start");
                while (!shouldStop.get()) {
                  try {
                    transactionTemplate.executeWithoutResult(
                        status ->
                            jdbcTemplate.queryForMap(
                                "SELECT id FROM posts WHERE id = ? FOR NO KEY UPDATE NOWAIT",
                                postId));
                    probeSuccesses.incrementAndGet();
                  } catch (CannotAcquireLockException ignored) {
                    probeFailures.incrementAndGet();
                  }
                  try {
                    Thread.sleep(5);
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                  }
                }
                return null;
              });

      start.countDown();

      for (Future<ResponseEntity<String>> future : commentFutures) {
        ResponseEntity<String> response = future.get(10, TimeUnit.SECONDS);
        assertThat(response.getStatusCode())
            .as("concurrent createComment should return 2xx — body=%s", response.getBody())
            .isEqualTo(HttpStatus.OK);
      }

      shouldStop.set(true);
      probeFuture.get(5, TimeUnit.SECONDS);

      assertThat(probeSuccesses.get())
          .as("NOWAIT probe should run at least once while commenters were in flight")
          .isGreaterThanOrEqualTo(1);
      assertThat(probeFailures.get())
          .as(
              "MOM-459 contract — posts row must stay lock-free while concurrent createComment are"
                  + " in flight. NOWAIT failures (55P03) prove a conflicting row lock was held"
                  + " (regression). successes=%d, failures=%d",
              probeSuccesses.get(), probeFailures.get())
          .isZero();

      Long commentCount =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM comments WHERE post_id = ?", Long.class, postId);
      assertThat(commentCount).isEqualTo((long) concurrency);
    } finally {
      shouldStop.set(true);
      executor.shutdownNow();
      executor.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  /**
   * Scenario B — observable consequence of the lock-free contract: N concurrent {@code
   * createComment} on the same post must not serialize.
   *
   * <p>Under the old auto-{@code PESSIMISTIC_WRITE} path the {@code posts} row lock was held until
   * outer commit, so N concurrent requests on the same post serialized — wall-time grew ≈ {@code N
   * × single}. With MOM-459's lock-free {@code loadPost} the requests run in parallel and wall-time
   * stays ≈ {@code single}.
   *
   * <p>We assert {@code parallel ≤ max(baseline × 5, 1500 ms)} — the {@code × 5} margin decisively
   * rejects the old {@code × 10} serialization while tolerating JIT / connection-pool / scheduling
   * noise; the {@code 1500 ms} floor protects against an artificially small baseline.
   *
   * <p><b>Known fragility — full e2e suite runs:</b> the wall-time measurement is sensitive to
   * JVM-shared environment (multiple SpringBootTest contexts cached in the same JVM, GC pause,
   * background worker polling). When this test is part of the full {@code ./gradlew e2eTest} run, a
   * single GC pause or a noisy neighbour context can push {@code parallelMillis} past the floor and
   * fail this assertion even when the lock-removal contract still holds. If this test fails, RERUN
   * IT ALONE to confirm the contract is intact:
   *
   * <pre>
   * ./gradlew e2eTest --tests "*CommentCreateNoPostLockConcurrencyE2ETest.concurrentCreateCommentDoesNotSerializeOnPostsRowLock"
   * </pre>
   *
   * <p>The primary lock-free regression signal is the sibling NOWAIT-probe test ({@link
   * #concurrentCreateCommentLeavesPostsRowLockFreeProvenByNowaitProbe}), which is environment-noise
   * insensitive — if that test passes and only this one fails, the regression is in the test
   * harness, not in production code.
   */
  @Test
  @DisplayName("concurrent createComment x N on the same post do not serialize on a row lock")
  void concurrentCreateCommentDoesNotSerializeOnPostsRowLock() throws Exception {
    int concurrency = 10;
    TestUser postAuthor = signupAndLogin("comment-parallel-author");
    Long postId = createFreePost(postAuthor.accessToken(), "post under parallel commenters");

    TestUser baselineCommenter = signupAndLogin("comment-parallel-baseline");
    long baselineStart = System.nanoTime();
    ResponseEntity<String> baseline =
        restTemplate.exchange(
            baseUrl() + "/posts/" + postId + "/comments",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("content", "baseline"), bearerJsonHeaders(baselineCommenter.accessToken())),
            String.class);
    long baselineNanos = System.nanoTime() - baselineStart;
    assertThat(baseline.getStatusCode()).isEqualTo(HttpStatus.OK);

    List<TestUser> commenters = new ArrayList<>(concurrency);
    for (int i = 0; i < concurrency; i++) {
      commenters.add(signupAndLogin("comment-parallel-commenter-" + i));
    }

    ExecutorService executor = Executors.newFixedThreadPool(concurrency);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<ResponseEntity<String>>> futures = new ArrayList<>(concurrency);

    try {
      for (TestUser commenter : commenters) {
        futures.add(
            executor.submit(
                () -> {
                  awaitLatch(start, "start");
                  return restTemplate.exchange(
                      baseUrl() + "/posts/" + postId + "/comments",
                      HttpMethod.POST,
                      new HttpEntity<>(
                          Map.of("content", "parallel comment"),
                          bearerJsonHeaders(commenter.accessToken())),
                      String.class);
                }));
      }

      long parallelStart = System.nanoTime();
      start.countDown();
      for (Future<ResponseEntity<String>> future : futures) {
        ResponseEntity<String> response = future.get(15, TimeUnit.SECONDS);
        assertThat(response.getStatusCode())
            .as("parallel createComment should return 2xx — body=%s", response.getBody())
            .isEqualTo(HttpStatus.OK);
      }
      long parallelNanos = System.nanoTime() - parallelStart;

      long baselineMillis = baselineNanos / 1_000_000;
      long parallelMillis = parallelNanos / 1_000_000;
      long upperBoundMillis = Math.max(baselineMillis * 5, 1500L);
      assertThat(parallelMillis)
          .as(
              "MOM-459 — concurrent createComment must not serialize on posts row lock."
                  + " baseline=%d ms, parallel(N=%d)=%d ms, upper bound=%d ms."
                  + " Serialized (old code) would be ≈ N × baseline = %d ms."
                  + "%n"
                  + "%n>>> KNOWN FRAGILITY: this wall-time measurement is sensitive to JVM-shared"
                  + "%n>>> environment (multiple SpringBootTest contexts cached in the same JVM,"
                  + "%n>>> GC pause, background worker polling). If this fails inside a full"
                  + "%n>>> ./gradlew e2eTest suite run, RERUN IT ALONE to confirm the contract:"
                  + "%n>>>"
                  + "%n>>>   ./gradlew e2eTest --tests"
                  + " \"*CommentCreateNoPostLockConcurrencyE2ETest.concurrentCreateCommentDoesNotSerializeOnPostsRowLock\""
                  + "%n>>>"
                  + "%n>>> Primary lock-free regression signal is the sibling NOWAIT-probe test"
                  + "%n>>> (concurrentCreateCommentLeavesPostsRowLockFreeProvenByNowaitProbe),"
                  + "%n>>> which is environment-noise insensitive. If THAT test passes and only"
                  + "%n>>> this one fails, the regression is in the test harness, not in"
                  + "%n>>> production code.",
              baselineMillis,
              concurrency,
              parallelMillis,
              upperBoundMillis,
              concurrency * baselineMillis)
          .isLessThanOrEqualTo(upperBoundMillis);

      Long commentCount =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM comments WHERE post_id = ?", Long.class, postId);
      assertThat(commentCount).isEqualTo((long) (concurrency + 1));
    } finally {
      executor.shutdownNow();
      executor.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  private Long createFreePost(String accessToken, String content) throws Exception {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/posts/free",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("content", content, "imageIds", java.util.List.of()),
                bearerJsonHeaders(accessToken)),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return objectMapper.readTree(response.getBody()).at("/data/postId").asLong();
  }

  private void awaitLatch(CountDownLatch latch, String label) {
    try {
      assertThat(latch.await(5, TimeUnit.SECONDS)).as(label + " should be released").isTrue();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while waiting for " + label, e);
    }
  }
}
