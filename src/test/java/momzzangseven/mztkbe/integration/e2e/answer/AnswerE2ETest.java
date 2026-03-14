package momzzangseven.mztkbe.integration.e2e.answer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import momzzangseven.mztkbe.modules.auth.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.auth.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@Tag("e2e")
@ActiveProfiles("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("[E2E] Answer business rules")
class AnswerE2ETest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  @MockBean private KakaoAuthPort kakaoAuthPort;
  @MockBean private GoogleAuthPort googleAuthPort;
  @MockBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  private final List<Long> createdPostIds = new ArrayList<>();
  private final List<Long> createdAnswerIds = new ArrayList<>();

  private String baseUrl() {
    return "http://localhost:" + port;
  }

  @AfterEach
  void tearDown() {
    for (Long answerId : createdAnswerIds) {
      try {
        jdbcTemplate.update("DELETE FROM answer_images WHERE answer_id = ?", answerId);
      } catch (Exception ignored) {
      }
      try {
        jdbcTemplate.update("DELETE FROM answers WHERE id = ?", answerId);
      } catch (Exception ignored) {
      }
    }
    createdAnswerIds.clear();

    for (Long postId : createdPostIds) {
      try {
        jdbcTemplate.update(
            "DELETE FROM answer_images WHERE answer_id IN (SELECT id FROM answers WHERE post_id = ?)",
            postId);
      } catch (Exception ignored) {
      }
      try {
        jdbcTemplate.update("DELETE FROM answers WHERE post_id = ?", postId);
      } catch (Exception ignored) {
      }
      try {
        jdbcTemplate.update("DELETE FROM post_tags WHERE post_id = ?", postId);
      } catch (Exception ignored) {
      }
      try {
        jdbcTemplate.update("DELETE FROM posts WHERE id = ?", postId);
      } catch (Exception ignored) {
      }
    }
    createdPostIds.clear();
  }

  @Nested
  @DisplayName("Success cases")
  class SuccessCases {

    @Test
    @DisplayName("create answer persists DB row and returns response payload")
    void createAnswer_success_persistsAndReturnsPayload() throws Exception {
      TestUser author = signupAndLogin("question-author");
      TestUser answerer = signupAndLogin("answer-writer");
      Long postId =
          createQuestionPost(author.accessToken(), "Question title", "Question content", 100L);

      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl() + "/questions/" + postId + "/answers",
              HttpMethod.POST,
              new HttpEntity<>(
                  Map.of(
                      "content",
                      "This is the accepted candidate",
                      "imageUrls",
                      List.of("https://example.com/a-1.png", "https://example.com/a-2.png")),
                  authHeaders(answerer.accessToken())),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

      JsonNode root = parse(response);
      Long answerId = root.at("/data/answerId").asLong();
      createdAnswerIds.add(answerId);

      assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
      assertThat(answerId).isPositive();

      Map<String, Object> row =
          jdbcTemplate.queryForMap("SELECT * FROM answers WHERE id = ?", answerId);
      assertThat(((Number) row.get("post_id")).longValue()).isEqualTo(postId);
      assertThat(((Number) row.get("user_id")).longValue()).isEqualTo(answerer.userId());
      assertThat(row.get("content")).isEqualTo("This is the accepted candidate");
      assertThat(row.get("is_accepted")).isEqualTo(false);

      List<String> savedImages =
          jdbcTemplate.queryForList(
              "SELECT image_url FROM answer_images WHERE answer_id = ? ORDER BY image_url",
              String.class,
              answerId);
      assertThat(savedImages)
          .containsExactly("https://example.com/a-1.png", "https://example.com/a-2.png");
    }

    @Test
    @DisplayName("get answers returns accepted answer first with writer fields")
    void getAnswers_success_returnsAcceptedFirstAndResponseFields() throws Exception {
      TestUser author = signupAndLogin("question-owner");
      TestUser regularAnswerer = signupAndLogin("regular-answerer");
      TestUser acceptedAnswerer = signupAndLogin("accepted-answerer");
      Long postId =
          createQuestionPost(author.accessToken(), "Order question", "Need ordering", 50L);

      Long regularAnswerId =
          createAnswer(
              postId,
              regularAnswerer.accessToken(),
              "regular answer",
              List.of("https://example.com/regular.png"));
      Long acceptedAnswerId =
          createAnswer(postId, acceptedAnswerer.accessToken(), "accepted answer", List.of());
      markAnswerAccepted(acceptedAnswerId);

      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl() + "/questions/" + postId + "/answers",
              HttpMethod.GET,
              new HttpEntity<>(authHeaders(author.accessToken())),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

      JsonNode root = parse(response);
      assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
      assertThat(root.at("/data").isArray()).isTrue();
      assertThat(root.at("/data").size()).isEqualTo(2);

      JsonNode first = root.at("/data/0");
      JsonNode second = root.at("/data/1");

      assertThat(first.at("/answerId").asLong()).isEqualTo(acceptedAnswerId);
      assertThat(first.at("/userId").asLong()).isEqualTo(acceptedAnswerer.userId());
      assertThat(first.at("/nickname").asText()).isEqualTo(acceptedAnswerer.nickname());
      assertThat(first.at("/content").asText()).isEqualTo("accepted answer");
      assertThat(first.at("/isAccepted").asBoolean()).isTrue();

      assertThat(second.at("/answerId").asLong()).isEqualTo(regularAnswerId);
      assertThat(second.at("/userId").asLong()).isEqualTo(regularAnswerer.userId());
      assertThat(second.at("/nickname").asText()).isEqualTo(regularAnswerer.nickname());
      assertThat(second.at("/content").asText()).isEqualTo("regular answer");
      assertThat(second.at("/imageUrls/0").asText()).isEqualTo("https://example.com/regular.png");
      assertThat(second.at("/isAccepted").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("update answer changes DB state and subsequent GET reflects it")
    void updateAnswer_success_updatesDbAndGetResponse() throws Exception {
      TestUser author = signupAndLogin("update-author");
      TestUser answerer = signupAndLogin("update-answerer");
      Long postId = createQuestionPost(author.accessToken(), "Update question", "Update me", 75L);
      Long answerId =
          createAnswer(
              postId,
              answerer.accessToken(),
              "before update",
              List.of("https://example.com/old.png"));

      ResponseEntity<String> updateResponse =
          restTemplate.exchange(
              baseUrl() + "/questions/" + postId + "/answers/" + answerId,
              HttpMethod.PUT,
              new HttpEntity<>(
                  Map.of(
                      "content",
                      "after update",
                      "imageUrls",
                      List.of("https://example.com/new.png")),
                  authHeaders(answerer.accessToken())),
              String.class);

      assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(parse(updateResponse).at("/status").asText()).isEqualTo("SUCCESS");

      Map<String, Object> row =
          jdbcTemplate.queryForMap("SELECT * FROM answers WHERE id = ?", answerId);
      assertThat(row.get("content")).isEqualTo("after update");

      List<String> savedImages =
          jdbcTemplate.queryForList(
              "SELECT image_url FROM answer_images WHERE answer_id = ?", String.class, answerId);
      assertThat(savedImages).containsExactly("https://example.com/new.png");

      ResponseEntity<String> getResponse =
          restTemplate.exchange(
              baseUrl() + "/questions/" + postId + "/answers",
              HttpMethod.GET,
              new HttpEntity<>(authHeaders(author.accessToken())),
              String.class);

      JsonNode firstAnswer = parse(getResponse).at("/data/0");
      assertThat(firstAnswer.at("/answerId").asLong()).isEqualTo(answerId);
      assertThat(firstAnswer.at("/content").asText()).isEqualTo("after update");
      assertThat(firstAnswer.at("/imageUrls/0").asText()).isEqualTo("https://example.com/new.png");
    }

    @Test
    @DisplayName("delete answer removes DB row and image rows")
    void deleteAnswer_success_removesRows() throws Exception {
      TestUser author = signupAndLogin("delete-author");
      TestUser answerer = signupAndLogin("delete-answerer");
      Long postId = createQuestionPost(author.accessToken(), "Delete question", "Delete me", 25L);
      Long answerId =
          createAnswer(
              postId,
              answerer.accessToken(),
              "to be deleted",
              List.of("https://example.com/delete.png"));

      ResponseEntity<String> deleteResponse =
          restTemplate.exchange(
              baseUrl() + "/questions/" + postId + "/answers/" + answerId,
              HttpMethod.DELETE,
              new HttpEntity<>(authHeaders(answerer.accessToken())),
              String.class);

      assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(parse(deleteResponse).at("/status").asText()).isEqualTo("SUCCESS");
      assertThat(countAnswersById(answerId)).isZero();
      assertThat(countAnswerImages(answerId)).isZero();
    }
  }

  @Nested
  @DisplayName("Failure cases")
  class FailureCases {

    @Test
    @DisplayName("answering own question returns 400 and does not insert rows")
    void createAnswer_onOwnQuestion_returns400AndDoesNotPersist() throws Exception {
      TestUser author = signupAndLogin("self-answer-author");
      Long postId = createQuestionPost(author.accessToken(), "Self question", "Own question", 40L);

      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl() + "/questions/" + postId + "/answers",
              HttpMethod.POST,
              new HttpEntity<>(
                  Map.of("content", "I answer myself"), authHeaders(author.accessToken())),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      JsonNode root = parse(response);
      assertThat(root.at("/status").asText()).isEqualTo("FAIL");
      assertThat(root.at("/code").asText()).isEqualTo("ANSWER_003");
      assertThat(countAnswersByPostId(postId)).isZero();
    }

    @Test
    @DisplayName("answering solved question returns 400 and does not insert rows")
    void createAnswer_onSolvedQuestion_returns400AndDoesNotPersist() throws Exception {
      TestUser author = signupAndLogin("solved-author");
      TestUser answerer = signupAndLogin("solved-answerer");
      Long postId =
          createQuestionPost(author.accessToken(), "Solved question", "Closed already", 60L);
      markPostSolved(postId);

      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl() + "/questions/" + postId + "/answers",
              HttpMethod.POST,
              new HttpEntity<>(
                  Map.of("content", "late answer"), authHeaders(answerer.accessToken())),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      JsonNode root = parse(response);
      assertThat(root.at("/status").asText()).isEqualTo("FAIL");
      assertThat(root.at("/code").asText()).isEqualTo("ANSWER_004");
      assertThat(countAnswersByPostId(postId)).isZero();
    }

    @Test
    @DisplayName("other user cannot update answer and DB remains unchanged")
    void updateAnswer_byOtherUser_returns403AndKeepsOriginalData() throws Exception {
      TestUser author = signupAndLogin("unauthorized-update-author");
      TestUser owner = signupAndLogin("answer-owner");
      TestUser intruder = signupAndLogin("answer-intruder");
      Long postId =
          createQuestionPost(author.accessToken(), "Unauthorized update", "Question", 30L);
      Long answerId = createAnswer(postId, owner.accessToken(), "original content", List.of());

      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl() + "/questions/" + postId + "/answers/" + answerId,
              HttpMethod.PUT,
              new HttpEntity<>(Map.of("content", "hacked"), authHeaders(intruder.accessToken())),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
      JsonNode root = parse(response);
      assertThat(root.at("/status").asText()).isEqualTo("FAIL");
      assertThat(root.at("/code").asText()).isEqualTo("ANSWER_002");
      assertThat(answerContent(answerId)).isEqualTo("original content");
    }

    @Test
    @DisplayName("accepted answer cannot be deleted and DB rows remain")
    void deleteAcceptedAnswer_returns400AndKeepsRows() throws Exception {
      TestUser author = signupAndLogin("accepted-delete-author");
      TestUser answerer = signupAndLogin("accepted-delete-answerer");
      Long postId = createQuestionPost(author.accessToken(), "Accepted delete", "Question", 90L);
      Long answerId =
          createAnswer(
              postId,
              answerer.accessToken(),
              "cannot delete me",
              List.of("https://example.com/keep.png"));
      markAnswerAccepted(answerId);

      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl() + "/questions/" + postId + "/answers/" + answerId,
              HttpMethod.DELETE,
              new HttpEntity<>(authHeaders(answerer.accessToken())),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      JsonNode root = parse(response);
      assertThat(root.at("/status").asText()).isEqualTo("FAIL");
      assertThat(root.at("/code").asText()).isEqualTo("ANSWER_006");
      assertThat(countAnswersById(answerId)).isEqualTo(1);
      assertThat(countAnswerImages(answerId)).isEqualTo(1);
    }
  }

  private TestUser signupAndLogin(String nicknamePrefix) throws Exception {
    String email = uniqueEmail();
    String nickname = nicknamePrefix + "-" + UUID.randomUUID().toString().substring(0, 6);
    String password = "Test@1234!";

    ResponseEntity<String> signupResponse =
        restTemplate.exchange(
            baseUrl() + "/auth/signup",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("email", email, "password", password, "nickname", nickname), jsonHeaders()),
            String.class);

    assertThat(signupResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<String> loginResponse =
        restTemplate.exchange(
            baseUrl() + "/auth/login",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("provider", "LOCAL", "email", email, "password", password), jsonHeaders()),
            String.class);

    assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = parse(loginResponse);
    return new TestUser(
        root.at("/data/accessToken").asText(),
        root.at("/data/userInfo/userId").asLong(),
        root.at("/data/userInfo/nickname").asText());
  }

  private Long createQuestionPost(String accessToken, String title, String content, long reward)
      throws Exception {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/posts/question",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("title", title, "content", content, "reward", reward),
                authHeaders(accessToken)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    Long postId = parse(response).at("/data/postId").asLong();
    createdPostIds.add(postId);
    return postId;
  }

  private Long createAnswer(Long postId, String accessToken, String content, List<String> imageUrls)
      throws Exception {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/questions/" + postId + "/answers",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("content", content, "imageUrls", imageUrls), authHeaders(accessToken)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    Long answerId = parse(response).at("/data/answerId").asLong();
    createdAnswerIds.add(answerId);
    return answerId;
  }

  private void markPostSolved(Long postId) {
    int updated = jdbcTemplate.update("UPDATE posts SET is_solved = true WHERE id = ?", postId);
    assertThat(updated).isEqualTo(1);
  }

  private void markAnswerAccepted(Long answerId) {
    int updated =
        jdbcTemplate.update("UPDATE answers SET is_accepted = true WHERE id = ?", answerId);
    assertThat(updated).isEqualTo(1);
  }

  private int countAnswersByPostId(Long postId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM answers WHERE post_id = ?", Integer.class, postId);
    return count == null ? 0 : count;
  }

  private int countAnswersById(Long answerId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM answers WHERE id = ?", Integer.class, answerId);
    return count == null ? 0 : count;
  }

  private int countAnswerImages(Long answerId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM answer_images WHERE answer_id = ?", Integer.class, answerId);
    return count == null ? 0 : count;
  }

  private String answerContent(Long answerId) {
    return jdbcTemplate.queryForObject(
        "SELECT content FROM answers WHERE id = ?", String.class, answerId);
  }

  private HttpHeaders authHeaders(String accessToken) {
    HttpHeaders headers = jsonHeaders();
    headers.setBearerAuth(accessToken);
    return headers;
  }

  private HttpHeaders jsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private JsonNode parse(ResponseEntity<String> response) throws Exception {
    return objectMapper.readTree(response.getBody());
  }

  private static String uniqueEmail() {
    return "answer-e2e-"
        + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
        + "@test.com";
  }

  private record TestUser(String accessToken, Long userId, String nickname) {}
}
