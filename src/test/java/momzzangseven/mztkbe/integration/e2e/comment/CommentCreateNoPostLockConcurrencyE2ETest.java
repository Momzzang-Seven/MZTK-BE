package momzzangseven.mztkbe.integration.e2e.comment;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
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
      "web3.reward-token.enabled=false"
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
