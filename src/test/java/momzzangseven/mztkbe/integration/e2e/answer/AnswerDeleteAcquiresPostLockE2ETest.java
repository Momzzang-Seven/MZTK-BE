package momzzangseven.mztkbe.integration.e2e.answer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionLifecycleExecutionPort;
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
 * MOM-459 answer write-path lost-update guard — backed by real PostgreSQL row locks.
 *
 * <p>Sister of {@code PostDeleteAcquiresPostLockE2ETest}. The same lock-removal commit that opened
 * the delete-path race also opened a race on the answer write paths (update / delete / recover),
 * because every {@code prepareLocal*} reads {@code post.content} / {@code post.reward} / {@code
 * post.publiclyVisible} and feeds them either into validate guards or into the escrow payload hash.
 * Without a row lock, a concurrent post mutation can slip between validate and the on-chain prepare
 * — producing FK violations or escrow-payload divergence (off-chain row evolves while the
 * already-prepared intent still hashes the stale snapshot).
 *
 * <p>This test covers the {@code DELETE /questions/{postId}/answers/{answerId}} branch; the update
 * / recover-create / recover-update branches use the same {@code loadPostForUpdate} call site and
 * therefore share this signal.
 *
 * <p>Scenario (single test — directly observable signal):
 *
 * <ol>
 *   <li>A holder thread opens a transaction, takes {@code SELECT … FOR NO KEY UPDATE} on the
 *       question post, commits {@code moderation_status = 'BLOCKED'} inside that transaction,
 *       signals the main thread, then waits.
 *   <li>The main thread submits {@code DELETE /questions/{postId}/answers/{answerId}}. With the
 *       fix, the delete path's {@code prepareLocalDelete} blocks on the holder's row lock through
 *       {@code loadPostForUpdate(answer.getPostId())}; once the holder commits, it observes the
 *       freshly-committed BLOCKED state, {@code validatePostWritable} throws, and the response is a
 *       4xx — the BLOCKED state is preserved and the answer row remains.
 *   <li>A regression to lock-free {@code loadPost} would have read the stale {@code NORMAL}
 *       snapshot, passed {@code validatePostWritable}, then proceeded to mutate answer state /
 *       prepare an escrow delete with a payload that no longer matches the off-chain post. The
 *       BLOCKED transition would still commit, but the answer-side state would silently diverge.
 *       The two outcomes are mutually exclusive — this test is decisive.
 * </ol>
 */
@TestPropertySource(
    properties = {
      "web3.chain-id=1337",
      "web3.eip712.chain-id=1337",
      "web3.eip7702.enabled=false",
      "web3.reward-token.enabled=false"
    })
@DisplayName("[E2E] AnswerService delete locks posts row (MOM-459 answer write-path guard)")
class AnswerDeleteAcquiresPostLockE2ETest extends E2ETestBase {

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PlatformTransactionManager transactionManager;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;
  @MockitoBean private QuestionLifecycleExecutionPort questionLifecycleExecutionPort;

  @Test
  @DisplayName(
      "DELETE /questions/{postId}/answers/{answerId} waits on a concurrent moderation-block transaction and rejects on fresh state")
  void deleteAnswer_blocksOnConcurrentModerationLockAndRejectsOnFreshState() throws Exception {
    TestUser questionAuthor = signupAndLogin("ans-lock-author");
    TestUser answerer = signupAndLogin("ans-lock-answerer");
    Long postId =
        createQuestionPost(
            questionAuthor.accessToken(),
            "answer-lock e2e question",
            "question content under answer-delete contention",
            100L);
    Long answerId =
        createAnswer(postId, answerer.accessToken(), "answer under contention", List.of());

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
                      // Establish a PESSIMISTIC_WRITE-equivalent on the question post row, then
                      // commit a mutation that makes the post non-writable inside the same TX.
                      jdbcTemplate.queryForMap(
                          "SELECT id FROM posts WHERE id = ? FOR NO KEY UPDATE", postId);
                      jdbcTemplate.update(
                          "UPDATE posts SET moderation_status = 'BLOCKED' WHERE id = ?", postId);
                      holderAcquiredAndUpdated.countDown();
                      // Keep the lock until the main thread releases us, ensuring the DELETE
                      // request reaches loadPostForUpdate and queues behind us before our commit.
                      awaitLatch(requestDone, "requestDone");
                    });
                return null;
              });

      Future<ResponseEntity<String>> requestFuture =
          executor.submit(
              () -> {
                awaitLatch(holderAcquiredAndUpdated, "holderAcquiredAndUpdated");
                return restTemplate.exchange(
                    baseUrl() + "/questions/" + postId + "/answers/" + answerId,
                    HttpMethod.DELETE,
                    new HttpEntity<>(bearerJsonHeaders(answerer.accessToken())),
                    String.class);
              });

      // Wait long enough for the DELETE request to start and queue on the holder's row lock.
      // 1500 ms is well above the latency budget for the HTTP round-trip to reach the
      // loadPostForUpdate SELECT (typically <100 ms on this profile) yet small enough to keep the
      // test fast.
      Thread.sleep(1500L);
      requestDone.countDown();
      holderFuture.get(10, TimeUnit.SECONDS);

      ResponseEntity<String> response = requestFuture.get(10, TimeUnit.SECONDS);

      // With the lock-and-act fix, the answer-delete path observed BLOCKED on a fresh snapshot and
      // rejected via validatePostWritable. Without the fix (lock-free loadPost), it would have
      // validated on the stale NORMAL snapshot, persisted answer mutation state and/or prepared
      // an escrow delete payload — silently diverging from the now-BLOCKED off-chain post.
      assertThat(response.getStatusCode().is4xxClientError())
          .as(
              "MOM-459 answer write-path guard: expected 4xx after fresh BLOCKED snapshot under"
                  + " lock; actual status=%s body=%s",
              response.getStatusCode(), response.getBody())
          .isTrue();

      assertThat(countAnswersById(answerId))
          .as("the answer row must still exist — delete was correctly rejected")
          .isEqualTo(1);

      Map<String, Object> postRow =
          jdbcTemplate.queryForMap("SELECT id, moderation_status FROM posts WHERE id = ?", postId);
      assertThat(postRow.get("id"))
          .as("the post row must still exist (delete-of-post not part of this scenario)")
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

  private Long createQuestionPost(String accessToken, String title, String content, long reward)
      throws Exception {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/posts/question",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("title", title, "content", content, "reward", reward),
                bearerJsonHeaders(accessToken)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    JsonNode root = objectMapper.readTree(response.getBody());
    return root.at("/data/postId").asLong();
  }

  private Long createAnswer(Long postId, String accessToken, String content, List<Long> imageIds)
      throws Exception {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/questions/" + postId + "/answers",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("content", content, "imageIds", imageIds), bearerJsonHeaders(accessToken)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    JsonNode root = objectMapper.readTree(response.getBody());
    return root.at("/data/answerId").asLong();
  }

  private int countAnswersById(Long answerId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM answers WHERE id = ?", Integer.class, answerId);
    return count == null ? 0 : count;
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
