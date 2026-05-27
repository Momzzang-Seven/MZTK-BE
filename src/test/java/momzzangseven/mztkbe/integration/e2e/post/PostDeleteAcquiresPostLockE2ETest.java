package momzzangseven.mztkbe.integration.e2e.post;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
 * MOM-459 delete-path lost-update guard — backed by real PostgreSQL row locks.
 *
 * <p>The lock-free escalation removal in {@code PostPersistenceAdapter.loadPost} (this PR's
 * headline change) is correct for read-only validate paths like {@code
 * CommentService.createComment}, but removing it from the {@code deletePost} branch alone would
 * have regressed the lost-update guard that develop's auto-PESSIMISTIC_WRITE silently provided.
 * This commit pinned {@code prepareLocalDelete} and {@code deletePostLocally} to explicit {@code
 * loadPostForUpdate} calls, and this test is the live-PG proof.
 *
 * <p>Scenario (single test — directly observable signal):
 *
 * <ol>
 *   <li>A holder thread opens a transaction, takes {@code SELECT … FOR NO KEY UPDATE} on the post,
 *       commits a delete-blocking state-change ({@code moderation_status = 'BLOCKED'}) inside that
 *       transaction, signals the main thread, then waits.
 *   <li>The main thread submits {@code DELETE /posts/{id}}. With the fix, the delete path's {@code
 *       loadPostForUpdate} queues behind the holder's lock; once the holder commits, the delete
 *       path observes the freshly-committed BLOCKED state, {@code validateOwnerMutationAllowed}
 *       throws, and the response is a 4xx — the BLOCKED state is preserved and the row remains.
 *   <li>A regression to lock-free {@code loadPost} would have read the stale {@code NORMAL}
 *       snapshot, validated successfully, then waited only at the SQL DELETE statement; once the
 *       holder commits, the DELETE would run, the row would vanish, and the BLOCKED transition
 *       would be lost. The two outcomes are mutually exclusive — this test is decisive.
 * </ol>
 *
 * <p>The TX-between race window (TX 1 commit → on-chain prepareQuestionDelete → TX 2 start) is not
 * covered here; it is a distributed-consistency concern tracked as a follow-up ticket (memory
 * {@code project_mom459_followup_delete_lock_and_answer_tx_boundary}).
 */
@TestPropertySource(
    properties = {
      "web3.chain-id=1337",
      "web3.eip712.chain-id=1337",
      "web3.eip7702.enabled=false",
      "web3.reward-token.enabled=false"
    })
@DisplayName("[E2E] PostProcessService.deletePost locks posts row (MOM-459 delete-path guard)")
class PostDeleteAcquiresPostLockE2ETest extends E2ETestBase {

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PlatformTransactionManager transactionManager;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  @Test
  @DisplayName(
      "DELETE /posts/{id} waits on a concurrent moderation-block transaction and rejects on fresh state")
  void deletePost_blocksOnConcurrentModerationLockAndRejectsOnFreshState() throws Exception {
    TestUser owner = signupAndLogin("delete-lock-owner");
    Long postId = createFreePost(owner.accessToken(), "post under delete-lock contention");

    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch holderAcquiredAndUpdated = new CountDownLatch(1);
    CountDownLatch requestDone = new CountDownLatch(1);
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

    try {
      Future<?> holderFuture =
          executor.submit(
              () -> {
                transactionTemplate.executeWithoutResult(
                    status -> {
                      // Establish a PESSIMISTIC_WRITE-equivalent on the posts row, then commit a
                      // delete-blocking state-change inside the same transaction.
                      jdbcTemplate.queryForMap(
                          "SELECT id FROM posts WHERE id = ? FOR NO KEY UPDATE", postId);
                      jdbcTemplate.update(
                          "UPDATE posts SET moderation_status = 'BLOCKED' WHERE id = ?", postId);
                      holderAcquiredAndUpdated.countDown();
                      // Keep the lock until the main thread releases us, ensuring the DELETE
                      // request is in-flight and queued behind us before our commit.
                      awaitLatch(requestDone, "requestDone");
                    });
                return null;
              });

      Future<ResponseEntity<String>> requestFuture =
          executor.submit(
              () -> {
                awaitLatch(holderAcquiredAndUpdated, "holderAcquiredAndUpdated");
                return restTemplate.exchange(
                    baseUrl() + "/posts/" + postId,
                    HttpMethod.DELETE,
                    new HttpEntity<>(bearerJsonHeaders(owner.accessToken())),
                    String.class);
              });

      // Wait long enough for the DELETE request to start and queue on the holder's row lock.
      // 1500 ms is well above the latency budget for the HTTP round-trip to reach the
      // loadPostForUpdate SELECT (typically <100 ms on this profile) yet small enough to keep
      // the test fast.
      Thread.sleep(1500L);
      requestDone.countDown();
      holderFuture.get(10, TimeUnit.SECONDS);

      ResponseEntity<String> response = requestFuture.get(10, TimeUnit.SECONDS);

      // With the lock-and-act fix, the delete path observed BLOCKED on a fresh snapshot and
      // rejected.
      // Without the fix (lock-free loadPost), it would have validated on the stale NORMAL snapshot
      // and SQL DELETEd the row — losing the BLOCKED transition.
      assertThat(response.getStatusCode().is4xxClientError())
          .as(
              "MOM-459 delete-path guard: expected 4xx after fresh BLOCKED snapshot under lock;"
                  + " actual status=%s body=%s",
              response.getStatusCode(), response.getBody())
          .isTrue();

      Map<String, Object> postRow =
          jdbcTemplate.queryForMap("SELECT id, moderation_status FROM posts WHERE id = ?", postId);
      assertThat(postRow.get("id"))
          .as("the post row must still exist — delete was correctly rejected")
          .isNotNull();
      assertThat(postRow.get("moderation_status"))
          .as("the concurrent moderation-block update must be preserved (no lost update)")
          .isEqualTo("BLOCKED");
    } finally {
      // Defensive: ensure holder is released even on assertion failure.
      requestDone.countDown();
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
