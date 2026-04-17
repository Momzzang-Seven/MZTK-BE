package momzzangseven.mztkbe.integration.e2e.post;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
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
 * Community question/answer concurrency E2E tests backed by real PostgreSQL row locks.
 *
 * <p>The test shape is intentionally asymmetric:
 *
 * <ol>
 *   <li>Thread A acquires {@code answers FOR UPDATE} first.
 *   <li>Thread B starts the real endpoint under test.
 *   <li>While Thread B is in flight, Thread A tries to acquire {@code posts FOR UPDATE}.
 * </ol>
 *
 * <p>If the endpoint also locks {@code answer -> post}, Thread B blocks on the answer row and
 * Thread A can still lock the post row before commit. If the endpoint ever regresses to {@code post
 * -> answer}, Thread B can grab the post row first and the test either deadlocks or times out while
 * Thread A waits on the post lock.
 */
@TestPropertySource(
    properties = {
      "web3.chain-id=1337",
      "web3.eip712.chain-id=1337",
      "web3.eip7702.enabled=false",
      "web3.reward-token.enabled=false"
    })
@DisplayName("[E2E] Community question/answer lock-order concurrency")
class PostAnswerLockOrderConcurrencyE2ETest extends E2ETestBase {

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PlatformTransactionManager transactionManager;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  @Test
  @DisplayName("[E-CONC-1] accept answer blocks on answer lock before post lock and completes")
  void acceptAnswer_whenAnswerLockHeld_blocksBeforePostLockAndCompletes() throws Exception {
    Scenario scenario = createScenario("accept");

    ResponseEntity<String> response =
        assertRequestBlocksOnAnswerLockBeforePostLock(
            scenario.postId(),
            scenario.answerId(),
            () ->
                restTemplate.exchange(
                    baseUrl()
                        + "/posts/"
                        + scenario.postId()
                        + "/answers/"
                        + scenario.answerId()
                        + "/accept",
                    HttpMethod.POST,
                    new HttpEntity<>(bearerJsonHeaders(scenario.asker().accessToken())),
                    String.class));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = parse(response);
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(root.at("/data/status").asText()).isEqualTo("RESOLVED");
    assertThat(postStatus(scenario.postId())).isEqualTo("RESOLVED");
    assertThat(acceptedAnswerId(scenario.postId())).isEqualTo(scenario.answerId());
    assertThat(answerAccepted(scenario.answerId())).isTrue();
  }

  @Test
  @DisplayName("[E-CONC-2] update answer blocks on answer lock before post lock and completes")
  void updateAnswer_whenAnswerLockHeld_blocksBeforePostLockAndCompletes() throws Exception {
    Scenario scenario = createScenario("update");
    String updatedContent = "answer content updated after waiting on answer lock";

    ResponseEntity<String> response =
        assertRequestBlocksOnAnswerLockBeforePostLock(
            scenario.postId(),
            scenario.answerId(),
            () ->
                restTemplate.exchange(
                    baseUrl()
                        + "/questions/"
                        + scenario.postId()
                        + "/answers/"
                        + scenario.answerId(),
                    HttpMethod.PUT,
                    new HttpEntity<>(
                        Map.of("content", updatedContent, "imageIds", List.of()),
                        bearerJsonHeaders(scenario.answerer().accessToken())),
                    String.class));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = parse(response);
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(answerContent(scenario.answerId())).isEqualTo(updatedContent);
    assertThat(postStatus(scenario.postId())).isEqualTo("OPEN");
  }

  @Test
  @DisplayName("[E-CONC-3] delete answer blocks on answer lock before post lock and completes")
  void deleteAnswer_whenAnswerLockHeld_blocksBeforePostLockAndCompletes() throws Exception {
    Scenario scenario = createScenario("delete");

    ResponseEntity<String> response =
        assertRequestBlocksOnAnswerLockBeforePostLock(
            scenario.postId(),
            scenario.answerId(),
            () ->
                restTemplate.exchange(
                    baseUrl()
                        + "/questions/"
                        + scenario.postId()
                        + "/answers/"
                        + scenario.answerId(),
                    HttpMethod.DELETE,
                    new HttpEntity<>(bearerJsonHeaders(scenario.answerer().accessToken())),
                    String.class));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = parse(response);
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(answerExists(scenario.answerId())).isFalse();
    assertThat(postStatus(scenario.postId())).isEqualTo("OPEN");
  }

  private ResponseEntity<String> assertRequestBlocksOnAnswerLockBeforePostLock(
      Long postId, Long answerId, Callable<ResponseEntity<String>> requestAction) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch answerLockHeld = new CountDownLatch(1);
    CountDownLatch requestStarted = new CountDownLatch(1);
    CountDownLatch postLockAcquired = new CountDownLatch(1);
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

    try {
      Future<?> holderFuture =
          executor.submit(
              () -> {
                transactionTemplate.executeWithoutResult(
                    status -> {
                      jdbcTemplate.queryForMap(
                          "SELECT id FROM answers WHERE id = ? FOR UPDATE", answerId);
                      answerLockHeld.countDown();
                      awaitLatch(requestStarted, "requestStarted");
                      pause(300L);
                      jdbcTemplate.queryForMap(
                          "SELECT id FROM posts WHERE id = ? FOR UPDATE", postId);
                      postLockAcquired.countDown();
                      pause(200L);
                    });
                return null;
              });

      Future<ResponseEntity<String>> requestFuture =
          executor.submit(
              () -> {
                awaitLatch(answerLockHeld, "answerLockHeld");
                requestStarted.countDown();
                return requestAction.call();
              });

      assertThat(postLockAcquired.await(5, TimeUnit.SECONDS))
          .as("Thread A should still acquire the post lock before the request can lock it")
          .isTrue();

      ResponseEntity<String> response = requestFuture.get(10, TimeUnit.SECONDS);
      holderFuture.get(10, TimeUnit.SECONDS);
      return response;
    } finally {
      executor.shutdownNow();
      executor.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  private Scenario createScenario(String prefix) throws Exception {
    TestUser asker = signupAndLogin(prefix + "-asker");
    TestUser answerer = signupAndLogin(prefix + "-answerer");
    Long postId =
        createQuestionPost(
            asker.accessToken(), prefix + " title", prefix + " question content", 30L);
    Long answerId = createAnswer(postId, answerer.accessToken(), prefix + " answer content");
    return new Scenario(asker, answerer, postId, answerId);
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
    return parse(response).at("/data/postId").asLong();
  }

  private Long createAnswer(Long postId, String accessToken, String content) throws Exception {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/questions/" + postId + "/answers",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("content", content, "imageIds", List.of()), bearerJsonHeaders(accessToken)),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return parse(response).at("/data/answerId").asLong();
  }

  private JsonNode parse(ResponseEntity<String> response) throws Exception {
    return objectMapper.readTree(response.getBody());
  }

  private String postStatus(Long postId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM posts WHERE id = ?", String.class, postId);
  }

  private Long acceptedAnswerId(Long postId) {
    return jdbcTemplate.query(
        "SELECT accepted_answer_id FROM posts WHERE id = ?",
        rs -> rs.next() ? rs.getObject(1, Long.class) : null,
        postId);
  }

  private boolean answerAccepted(Long answerId) {
    Boolean accepted =
        jdbcTemplate.queryForObject(
            "SELECT is_accepted FROM answers WHERE id = ?", Boolean.class, answerId);
    return Boolean.TRUE.equals(accepted);
  }

  private String answerContent(Long answerId) {
    return jdbcTemplate.queryForObject(
        "SELECT content FROM answers WHERE id = ?", String.class, answerId);
  }

  private boolean answerExists(Long answerId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM answers WHERE id = ?", Integer.class, answerId);
    return count != null && count > 0;
  }

  private void awaitLatch(CountDownLatch latch, String label) {
    try {
      assertThat(latch.await(5, TimeUnit.SECONDS)).as(label + " should be released").isTrue();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while waiting for " + label, e);
    }
  }

  private void pause(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while pausing", e);
    }
  }

  private record Scenario(TestUser asker, TestUser answerer, Long postId, Long answerId) {}
}
