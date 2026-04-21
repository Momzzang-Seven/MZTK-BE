package momzzangseven.mztkbe.integration.e2e.answer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.image.application.port.out.DeleteS3ObjectPort;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionLifecycleExecutionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@TestPropertySource(
    properties = {
      "web3.chain-id=1337",
      "web3.eip712.chain-id=1337",
      "web3.eip7702.enabled=false",
      "web3.reward-token.enabled=false"
    })
@DisplayName("[E2E] GET /questions/{postId}/answers 이미지 포함 응답 테스트")
class GetAnswersWithImagesE2ETest extends E2ETestBase {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Value("${cloud.aws.s3.url-prefix}")
  private String urlPrefix;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;
  @MockitoBean private DeleteS3ObjectPort deleteS3ObjectPort;
  @MockitoBean private QuestionLifecycleExecutionPort questionLifecycleExecutionPort;

  private TestUser signupAndLoginAs(String prefix) {
    return signupAndLogin(prefix + "-" + UUID.randomUUID().toString().substring(0, 6));
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
    return objectMapper.readTree(response.getBody()).at("/data/postId").asLong();
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
    return objectMapper.readTree(response.getBody()).at("/data/answerId").asLong();
  }

  private Long insertImage(Long userId, String finalObjectKey) {
    Instant now = Instant.now();
    String tmpObjectKey = "answer-e2e/" + UUID.randomUUID() + ".jpg";
    String sql =
        "INSERT INTO images "
            + "(user_id, reference_type, reference_id, status, tmp_object_key, final_object_key, img_order, created_at, updated_at) "
            + "VALUES (?, 'COMMUNITY_ANSWER', NULL, 'COMPLETED', ?, ?, 1, ?, ?)";

    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        conn -> {
          PreparedStatement ps = conn.prepareStatement(sql, new String[] {"id"});
          ps.setLong(1, userId);
          ps.setString(2, tmpObjectKey);
          ps.setString(3, finalObjectKey);
          ps.setTimestamp(4, Timestamp.from(now));
          ps.setTimestamp(5, Timestamp.from(now));
          return ps;
        },
        keyHolder);
    return keyHolder.getKey().longValue();
  }

  private JsonNode fetchAnswers(Long postId, String accessToken) throws Exception {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/questions/" + postId + "/answers",
            HttpMethod.GET,
            new HttpEntity<>(bearerJsonHeaders(accessToken)),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return objectMapper.readTree(response.getBody());
  }

  private JsonNode findAnswer(JsonNode root, Long answerId) {
    for (JsonNode node : root.at("/data")) {
      if (node.get("answerId").asLong() == answerId) {
        return node;
      }
    }
    throw new AssertionError("answerId " + answerId + " not found in response");
  }

  @Test
  @DisplayName("[E-13] 답변 목록 응답의 images는 {imageId, imageUrl} 객체 배열이다")
  void getAnswers_returnsImagesAsObjectArray() throws Exception {
    TestUser author = signupAndLoginAs("e13-author");
    TestUser answerer = signupAndLoginAs("e13-answerer");
    Long postId = createQuestionPost(author.accessToken(), "E-13 Q", "content", 20L);

    Long image1 = insertImage(answerer.userId(), "answers/e13-1.webp");
    Long image2 = insertImage(answerer.userId(), "answers/e13-2.webp");
    Long answerWithImages =
        createAnswer(
            postId, answerer.accessToken(), "answer with 2 images", List.of(image1, image2));
    Long answerWithoutImages =
        createAnswer(postId, answerer.accessToken(), "answer with 0 images", List.of());

    JsonNode root = fetchAnswers(postId, author.accessToken());

    JsonNode withImages = findAnswer(root, answerWithImages);
    JsonNode withoutImages = findAnswer(root, answerWithoutImages);

    assertThat(withImages.has("images")).isTrue();
    assertThat(withImages.has("imageUrls")).isFalse();
    JsonNode images = withImages.get("images");
    assertThat(images.isArray()).isTrue();
    assertThat(images.size()).isEqualTo(2);
    assertThat(images.get(0).get("imageId").asLong()).isEqualTo(image1);
    assertThat(images.get(0).get("imageUrl").asText()).isEqualTo(urlPrefix + "answers/e13-1.webp");
    assertThat(images.get(1).get("imageId").asLong()).isEqualTo(image2);
    assertThat(images.get(1).get("imageUrl").asText()).isEqualTo(urlPrefix + "answers/e13-2.webp");

    assertThat(withoutImages.get("images").isArray()).isTrue();
    assertThat(withoutImages.get("images").size()).isZero();
    assertThat(withoutImages.has("imageUrls")).isFalse();

    assertThat(withImages.has("isAccepted")).isTrue();
    assertThat(withImages.has("likeCount")).isTrue();
    assertThat(withImages.has("isLiked")).isTrue();
    assertThat(withImages.has("web3Execution")).isTrue();
  }

  @Test
  @DisplayName("[E-14] 답변 응답의 images 순서는 등록 순서(img_order)를 따른다")
  void getAnswers_imageOrderMatchesInsertionOrder() throws Exception {
    TestUser author = signupAndLoginAs("e14-author");
    TestUser answerer = signupAndLoginAs("e14-answerer");
    Long postId = createQuestionPost(author.accessToken(), "E-14 Q", "content", 30L);

    Long imgA = insertImage(answerer.userId(), "answers/e14-A.webp");
    Long imgB = insertImage(answerer.userId(), "answers/e14-B.webp");
    Long imgC = insertImage(answerer.userId(), "answers/e14-C.webp");
    Long answerId =
        createAnswer(postId, answerer.accessToken(), "ordered answer", List.of(imgA, imgB, imgC));

    JsonNode answer = findAnswer(fetchAnswers(postId, author.accessToken()), answerId);
    JsonNode images = answer.get("images");

    assertThat(images.size()).isEqualTo(3);
    assertThat(images.get(0).get("imageId").asLong()).isEqualTo(imgA);
    assertThat(images.get(0).get("imageUrl").asText()).endsWith("e14-A.webp");
    assertThat(images.get(1).get("imageId").asLong()).isEqualTo(imgB);
    assertThat(images.get(1).get("imageUrl").asText()).endsWith("e14-B.webp");
    assertThat(images.get(2).get("imageId").asLong()).isEqualTo(imgC);
    assertThat(images.get(2).get("imageUrl").asText()).endsWith("e14-C.webp");
  }

  @Test
  @DisplayName("[E-15] 여러 답변의 이미지 수가 달라도 교차 오염 없이 각 답변에 매칭된다")
  void getAnswers_mixedImageCounts_noCrossContamination() throws Exception {
    TestUser author = signupAndLoginAs("e15-author");
    TestUser a1 = signupAndLoginAs("e15-a1");
    TestUser a2 = signupAndLoginAs("e15-a2");
    TestUser a3 = signupAndLoginAs("e15-a3");
    Long postId = createQuestionPost(author.accessToken(), "E-15 Q", "content", 40L);

    Long answer0 = createAnswer(postId, a1.accessToken(), "zero images", List.of());

    Long singleImage = insertImage(a2.userId(), "answers/e15-single.webp");
    Long answer1 = createAnswer(postId, a2.accessToken(), "one image", List.of(singleImage));

    List<Long> threeImages = new ArrayList<>();
    threeImages.add(insertImage(a3.userId(), "answers/e15-t1.webp"));
    threeImages.add(insertImage(a3.userId(), "answers/e15-t2.webp"));
    threeImages.add(insertImage(a3.userId(), "answers/e15-t3.webp"));
    Long answer3 = createAnswer(postId, a3.accessToken(), "three images", threeImages);

    JsonNode root = fetchAnswers(postId, author.accessToken());

    assertThat(findAnswer(root, answer0).get("images").size()).isZero();

    JsonNode one = findAnswer(root, answer1);
    assertThat(one.get("images").size()).isEqualTo(1);
    assertThat(one.get("images").get(0).get("imageId").asLong()).isEqualTo(singleImage);
    assertThat(one.get("images").get(0).get("imageUrl").asText()).endsWith("e15-single.webp");

    JsonNode three = findAnswer(root, answer3);
    assertThat(three.get("images").size()).isEqualTo(3);
    for (int i = 0; i < 3; i++) {
      assertThat(three.get("images").get(i).get("imageId").asLong()).isEqualTo(threeImages.get(i));
    }
  }
}
