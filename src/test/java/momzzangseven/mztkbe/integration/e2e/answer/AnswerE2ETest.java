package momzzangseven.mztkbe.integration.e2e.answer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.image.application.port.out.DeleteS3ObjectPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Tag("e2e")
@ActiveProfiles("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("[E2E] Answer business rules")
class AnswerE2ETest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;
  @MockitoBean private DeleteS3ObjectPort deleteS3ObjectPort;

  private final List<Long> createdPostIds = new ArrayList<>();
  private final List<Long> createdAnswerIds = new ArrayList<>();
  private final List<Long> createdImageIds = new ArrayList<>();
  private final List<String> createdUserEmails = new ArrayList<>();

  private String baseUrl() {
    return "http://localhost:" + port;
  }

  @AfterEach
  void tearDown() {
    for (Long imageId : createdImageIds) {
      try {
        jdbcTemplate.update("DELETE FROM images WHERE id = ?", imageId);
      } catch (Exception ignored) {
      }
    }
    createdImageIds.clear();

    for (Long postId : createdPostIds) {
      try {
        jdbcTemplate.update("UPDATE posts SET accepted_answer_id = NULL WHERE id = ?", postId);
      } catch (Exception ignored) {
      }
    }

    for (Long answerId : createdAnswerIds) {
      try {
        jdbcTemplate.update("DELETE FROM answers WHERE id = ?", answerId);
      } catch (Exception ignored) {
      }
    }
    createdAnswerIds.clear();

    for (Long postId : createdPostIds) {
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

    for (String email : createdUserEmails) {
      try {
        jdbcTemplate.update(
            "DELETE FROM user_progress WHERE user_id = (SELECT id FROM users WHERE email = ?)",
            email);
      } catch (Exception ignored) {
      }
      try {
        jdbcTemplate.update("DELETE FROM users WHERE email = ?", email);
      } catch (Exception ignored) {
      }
    }
    createdUserEmails.clear();
  }

  @Nested
  @DisplayName("Success cases")
  class SuccessCases {

    @Test
    @DisplayName("create answer persists DB row and links image-module rows")
    void createAnswer_success_persistsAndReturnsPayload() throws Exception {
      TestUser author = signupAndLogin("question-author");
      TestUser answerer = signupAndLogin("answer-writer");
      Long postId =
          createQuestionPost(author.accessToken(), "Question title", "Question content", 100L);
      Long firstImageId = insertImage(answerer.userId(), "COMPLETED", "answers/a-1.webp");
      Long secondImageId = insertImage(answerer.userId(), "COMPLETED", "answers/a-2.webp");

      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl() + "/questions/" + postId + "/answers",
              HttpMethod.POST,
              new HttpEntity<>(
                  Map.of(
                      "content",
                      "This is the accepted candidate",
                      "imageIds",
                      List.of(firstImageId, secondImageId)),
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
      assertAnswerImageLinked(firstImageId, answerId, 1);
      assertAnswerImageLinked(secondImageId, answerId, 2);
    }

    @Test
    @DisplayName(
        "get answers returns accepted answer first with writer fields and rebuilt imageUrls")
    void getAnswers_success_returnsAcceptedFirstAndResponseFields() throws Exception {
      TestUser author = signupAndLogin("question-owner");
      TestUser regularAnswerer = signupAndLogin("regular-answerer");
      TestUser acceptedAnswerer = signupAndLogin("accepted-answerer");
      Long postId =
          createQuestionPost(author.accessToken(), "Order question", "Need ordering", 50L);
      Long regularImageId =
          insertImage(regularAnswerer.userId(), "COMPLETED", "answers/regular.webp");
      Long pendingImageId = insertImage(regularAnswerer.userId(), "PENDING", null);

      Long regularAnswerId =
          createAnswer(
              postId,
              regularAnswerer.accessToken(),
              "regular answer",
              List.of(regularImageId, pendingImageId));
      Long acceptedAnswerId =
          createAnswer(postId, acceptedAnswerer.accessToken(), "accepted answer", List.of());
      acceptAnswer(postId, acceptedAnswerId, author.accessToken());

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
      assertThat(second.at("/imageUrls/0").asText())
          .isEqualTo(buildPublicImageUrl("answers/regular.webp"));
      assertThat(second.at("/imageUrls/1").isNull()).isTrue();
      assertThat(second.at("/isAccepted").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("update answer changes DB state and re-links image-module rows")
    void updateAnswer_success_updatesDbAndGetResponse() throws Exception {
      TestUser author = signupAndLogin("update-author");
      TestUser answerer = signupAndLogin("update-answerer");
      Long postId = createQuestionPost(author.accessToken(), "Update question", "Update me", 75L);
      Long oldImageId = insertImage(answerer.userId(), "COMPLETED", "answers/old.webp");
      Long newImageId = insertImage(answerer.userId(), "COMPLETED", "answers/new.webp");
      Long answerId =
          createAnswer(postId, answerer.accessToken(), "before update", List.of(oldImageId));

      ResponseEntity<String> updateResponse =
          restTemplate.exchange(
              baseUrl() + "/questions/" + postId + "/answers/" + answerId,
              HttpMethod.PUT,
              new HttpEntity<>(
                  Map.of("content", "after update", "imageIds", List.of(newImageId)),
                  authHeaders(answerer.accessToken())),
              String.class);

      assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(parse(updateResponse).at("/status").asText()).isEqualTo("SUCCESS");

      Map<String, Object> row =
          jdbcTemplate.queryForMap("SELECT * FROM answers WHERE id = ?", answerId);
      assertThat(row.get("content")).isEqualTo("after update");
      assertImageUnlinked(oldImageId);
      assertAnswerImageLinked(newImageId, answerId, 1);

      ResponseEntity<String> getResponse =
          restTemplate.exchange(
              baseUrl() + "/questions/" + postId + "/answers",
              HttpMethod.GET,
              new HttpEntity<>(authHeaders(author.accessToken())),
              String.class);

      JsonNode firstAnswer = parse(getResponse).at("/data/0");
      assertThat(firstAnswer.at("/answerId").asLong()).isEqualTo(answerId);
      assertThat(firstAnswer.at("/content").asText()).isEqualTo("after update");
      assertThat(firstAnswer.at("/imageUrls/0").asText())
          .isEqualTo(buildPublicImageUrl("answers/new.webp"));
    }

    @Test
    @DisplayName("delete answer removes DB row and unlinks image-module rows")
    void deleteAnswer_success_removesRows() throws Exception {
      TestUser author = signupAndLogin("delete-author");
      TestUser answerer = signupAndLogin("delete-answerer");
      Long postId = createQuestionPost(author.accessToken(), "Delete question", "Delete me", 25L);
      Long imageId = insertImage(answerer.userId(), "COMPLETED", "answers/delete.webp");
      Long answerId =
          createAnswer(postId, answerer.accessToken(), "to be deleted", List.of(imageId));

      ResponseEntity<String> deleteResponse =
          restTemplate.exchange(
              baseUrl() + "/questions/" + postId + "/answers/" + answerId,
              HttpMethod.DELETE,
              new HttpEntity<>(authHeaders(answerer.accessToken())),
              String.class);

      assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(parse(deleteResponse).at("/status").asText()).isEqualTo("SUCCESS");
      assertThat(countAnswersById(answerId)).isZero();
      assertThat(countLinkedAnswerImages(answerId)).isZero();
      assertThat(countImagesById(imageId)).isEqualTo(1);
      assertImageUnlinked(imageId);
    }

    @Test
    @DisplayName("delete question post removes answers and unlinks answer images")
    void deleteQuestionPost_success_removesAnswersAndUnlinksImages() throws Exception {
      TestUser author = signupAndLogin("post-delete-author");
      TestUser answerer = signupAndLogin("post-delete-answerer");
      Long postId = createQuestionPost(author.accessToken(), "Cascade question", "Cascade me", 35L);
      Long imageId = insertImage(answerer.userId(), "COMPLETED", "answers/post-delete.webp");
      Long answerId =
          createAnswer(postId, answerer.accessToken(), "cascade answer", List.of(imageId));

      ResponseEntity<String> deleteResponse =
          restTemplate.exchange(
              baseUrl() + "/posts/" + postId,
              HttpMethod.DELETE,
              new HttpEntity<>(authHeaders(author.accessToken())),
              String.class);

      assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(parse(deleteResponse).at("/status").asText()).isEqualTo("SUCCESS");
      assertThat(postExistsInDb(postId)).isFalse();
      assertThat(countAnswersById(answerId)).isZero();
      assertThat(countLinkedAnswerImages(answerId)).isZero();
      assertImageUnlinked(imageId);
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
    @DisplayName("linking another user's image returns 403 and rolls back answer creation")
    void createAnswer_withOtherUsersImage_returns403AndRollsBack() throws Exception {
      TestUser author = signupAndLogin("foreign-image-author");
      TestUser imageOwner = signupAndLogin("foreign-image-owner");
      TestUser answerer = signupAndLogin("foreign-image-answerer");
      Long postId =
          createQuestionPost(author.accessToken(), "Foreign image question", "Question", 45L);
      Long foreignImageId = insertImage(imageOwner.userId(), "COMPLETED", "answers/foreign.webp");

      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl() + "/questions/" + postId + "/answers",
              HttpMethod.POST,
              new HttpEntity<>(
                  Map.of("content", "should fail", "imageIds", List.of(foreignImageId)),
                  authHeaders(answerer.accessToken())),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(countAnswersByPostId(postId)).isZero();
      assertImageUnlinked(foreignImageId);
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
    @DisplayName("update with another user's image returns 403 and rolls back answer update")
    void updateAnswer_withOtherUsersImage_returns403AndRollsBack() throws Exception {
      TestUser author = signupAndLogin("foreign-update-author");
      TestUser owner = signupAndLogin("foreign-update-owner");
      TestUser imageOwner = signupAndLogin("foreign-update-image-owner");
      Long postId = createQuestionPost(author.accessToken(), "Update rollback", "Question", 55L);
      Long answerId = createAnswer(postId, owner.accessToken(), "before update", List.of());
      Long foreignImageId =
          insertImage(imageOwner.userId(), "COMPLETED", "answers/foreign-update.webp");

      ResponseEntity<String> response =
          restTemplate.exchange(
              baseUrl() + "/questions/" + postId + "/answers/" + answerId,
              HttpMethod.PUT,
              new HttpEntity<>(
                  Map.of("content", "after update", "imageIds", List.of(foreignImageId)),
                  authHeaders(owner.accessToken())),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(answerContent(answerId)).isEqualTo("before update");
      assertImageUnlinked(foreignImageId);
    }

    @Test
    @DisplayName("accepted answer cannot be updated or deleted and DB rows remain")
    void acceptedAnswer_blocksUpdateAndDelete() throws Exception {
      TestUser author = signupAndLogin("accepted-delete-author");
      TestUser answerer = signupAndLogin("accepted-delete-answerer");
      Long postId = createQuestionPost(author.accessToken(), "Accepted delete", "Question", 90L);
      Long answerId =
          createAnswer(
              postId,
              answerer.accessToken(),
              "cannot delete me",
              List.of(insertImage(answerer.userId(), "COMPLETED", "answers/keep.webp")));
      acceptAnswer(postId, answerId, author.accessToken());

      Long replacementImageId =
          insertImage(answerer.userId(), "COMPLETED", "answers/rejected-update.webp");

      ResponseEntity<String> updateResponse =
          restTemplate.exchange(
              baseUrl() + "/questions/" + postId + "/answers/" + answerId,
              HttpMethod.PUT,
              new HttpEntity<>(
                  Map.of("content", "should not update", "imageIds", List.of(replacementImageId)),
                  authHeaders(answerer.accessToken())),
              String.class);

      assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      JsonNode updateRoot = parse(updateResponse);
      assertThat(updateRoot.at("/status").asText()).isEqualTo("FAIL");
      assertThat(updateRoot.at("/code").asText()).isEqualTo("ANSWER_005");
      assertThat(answerContent(answerId)).isEqualTo("cannot delete me");
      assertImageUnlinked(replacementImageId);

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
      assertThat(countLinkedAnswerImages(answerId)).isEqualTo(1);
    }
  }

  private TestUser signupAndLogin(String nicknamePrefix) throws Exception {
    String email = uniqueEmail();
    createdUserEmails.add(email);
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

  private Long createAnswer(Long postId, String accessToken, String content, List<Long> imageIds)
      throws Exception {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/questions/" + postId + "/answers",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("content", content, "imageIds", imageIds), authHeaders(accessToken)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    Long answerId = parse(response).at("/data/answerId").asLong();
    createdAnswerIds.add(answerId);
    return answerId;
  }

  private void markPostSolved(Long postId) {
    int updated =
        jdbcTemplate.update(
            "UPDATE posts SET is_solved = true, status = 'RESOLVED' WHERE id = ?", postId);
    assertThat(updated).isEqualTo(1);
  }

  private void acceptAnswer(Long postId, Long answerId, String accessToken) throws Exception {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/posts/" + postId + "/answers/" + answerId + "/accept",
            HttpMethod.POST,
            new HttpEntity<>(authHeaders(accessToken)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = parse(response);
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
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

  private int countLinkedAnswerImages(Long answerId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM images WHERE reference_type = 'COMMUNITY_ANSWER' AND reference_id = ?",
            Integer.class,
            answerId);
    return count == null ? 0 : count;
  }

  private int countImagesById(Long imageId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM images WHERE id = ?", Integer.class, imageId);
    return count == null ? 0 : count;
  }

  private String answerContent(Long answerId) {
    return jdbcTemplate.queryForObject(
        "SELECT content FROM answers WHERE id = ?", String.class, answerId);
  }

  private Long insertImage(Long userId, String status, String finalObjectKey) {
    Instant now = Instant.now();
    String tmpObjectKey = "answer-e2e/" + UUID.randomUUID() + ".jpg";
    String sql =
        "INSERT INTO images "
            + "(user_id, reference_type, reference_id, status, tmp_object_key, final_object_key, img_order, created_at, updated_at) "
            + "VALUES (?, 'COMMUNITY_ANSWER', NULL, ?, ?, ?, 1, ?, ?)";

    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        conn -> {
          PreparedStatement ps = conn.prepareStatement(sql, new String[] {"id"});
          ps.setLong(1, userId);
          ps.setString(2, status);
          ps.setString(3, tmpObjectKey);
          ps.setString(4, finalObjectKey);
          ps.setTimestamp(5, Timestamp.from(now));
          ps.setTimestamp(6, Timestamp.from(now));
          return ps;
        },
        keyHolder);

    Number generatedKey = keyHolder.getKey();
    if (generatedKey == null) {
      throw new IllegalStateException("Failed to insert image row");
    }

    Long imageId = generatedKey.longValue();
    createdImageIds.add(imageId);
    return imageId;
  }

  private void assertAnswerImageLinked(Long imageId, Long answerId, int expectedOrder) {
    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT reference_type, reference_id, img_order FROM images WHERE id = ?", imageId);

    assertThat(row.get("reference_type")).isEqualTo("COMMUNITY_ANSWER");
    assertThat(((Number) row.get("reference_id")).longValue()).isEqualTo(answerId);
    assertThat(((Number) row.get("img_order")).intValue()).isEqualTo(expectedOrder);
  }

  private void assertImageUnlinked(Long imageId) {
    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT reference_type, reference_id FROM images WHERE id = ?", imageId);

    assertThat(row.get("reference_type")).isEqualTo("COMMUNITY_ANSWER");
    assertThat(row.get("reference_id")).isNull();
  }

  private boolean postExistsInDb(Long postId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM posts WHERE id = ?", Integer.class, postId);
    return count != null && count > 0;
  }

  private String buildPublicImageUrl(String finalObjectKey) {
    return "https://test-bucket.s3.ap-northeast-2.amazonaws.com/" + finalObjectKey;
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
