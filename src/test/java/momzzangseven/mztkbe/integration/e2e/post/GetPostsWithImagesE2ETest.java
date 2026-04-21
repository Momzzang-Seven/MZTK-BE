package momzzangseven.mztkbe.integration.e2e.post;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.image.infrastructure.persistence.entity.ImageEntity;
import momzzangseven.mztkbe.modules.image.infrastructure.persistence.repository.ImageJpaRepository;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@TestPropertySource(
    properties = {
      "web3.chain-id=1337",
      "web3.eip712.chain-id=1337",
      "web3.eip7702.enabled=false",
      "web3.reward-token.enabled=false"
    })
@DisplayName("[E2E] GET /posts 이미지 포함 응답 테스트")
class GetPostsWithImagesE2ETest extends E2ETestBase {

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ImageJpaRepository imageJpaRepository;

  @Value("${cloud.aws.s3.url-prefix}")
  private String urlPrefix;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  private String accessToken;
  private Long userId;

  private Long createFreePost(String content) throws Exception {
    ResponseEntity<String> res =
        restTemplate.exchange(
            baseUrl() + "/posts/free",
            HttpMethod.POST,
            new HttpEntity<>(
                java.util.Map.of("content", content, "imageIds", List.of()),
                bearerJsonHeaders(accessToken)),
            String.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return objectMapper.readTree(res.getBody()).at("/data/postId").asLong();
  }

  private Long createQuestionPost(String title, String content, long reward) throws Exception {
    ResponseEntity<String> res =
        restTemplate.exchange(
            baseUrl() + "/posts/question",
            HttpMethod.POST,
            new HttpEntity<>(
                java.util.Map.of("title", title, "content", content, "reward", reward),
                bearerJsonHeaders(accessToken)),
            String.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return objectMapper.readTree(res.getBody()).at("/data/postId").asLong();
  }

  private void attachImage(Long postId, String referenceType, String finalObjectKey, int order) {
    imageJpaRepository.save(
        ImageEntity.builder()
            .userId(userId)
            .referenceType(referenceType)
            .referenceId(postId)
            .status("COMPLETED")
            .tmpObjectKey("tmp/" + UUID.randomUUID())
            .finalObjectKey(finalObjectKey)
            .imgOrder(order)
            .build());
  }

  private void attachBlankKeyImage(Long postId, String referenceType, int order) {
    imageJpaRepository.save(
        ImageEntity.builder()
            .userId(userId)
            .referenceType(referenceType)
            .referenceId(postId)
            .status("PENDING")
            .tmpObjectKey("tmp/" + UUID.randomUUID())
            .finalObjectKey(null)
            .imgOrder(order)
            .build());
  }

  private JsonNode fetchPosts(String query) throws Exception {
    ResponseEntity<String> res =
        restTemplate.exchange(
            baseUrl() + "/posts" + query,
            HttpMethod.GET,
            new HttpEntity<>(bearerJsonHeaders(accessToken)),
            String.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    return objectMapper.readTree(res.getBody());
  }

  private JsonNode findPost(JsonNode root, Long postId) {
    for (JsonNode node : root.at("/data/posts")) {
      if (node.get("postId").asLong() == postId) {
        return node;
      }
    }
    throw new AssertionError("postId " + postId + " not found in response");
  }

  private void setUpUser() {
    TestUser user = signupAndLogin("e2e-image-user");
    this.accessToken = user.accessToken();
    this.userId = user.userId();
  }

  @Test
  @DisplayName("[E-1] FREE 게시글 목록 조회 시 images 필드가 정상적으로 채워진다")
  void getPosts_free_populatesImages() throws Exception {
    setUpUser();

    Long postWithTwo = createFreePost("두 장짜리");
    Long postWithOne = createFreePost("한 장짜리");
    Long postWithNone = createFreePost("이미지 없음");

    attachImage(postWithTwo, "COMMUNITY_FREE", "e2e/free/" + postWithTwo + "/main.webp", 1);
    attachImage(postWithTwo, "COMMUNITY_FREE", "e2e/free/" + postWithTwo + "/sub.webp", 2);
    attachImage(postWithOne, "COMMUNITY_FREE", "e2e/free/" + postWithOne + "/only.webp", 1);

    JsonNode root = fetchPosts("?type=FREE&page=0&size=10");

    JsonNode two = findPost(root, postWithTwo);
    JsonNode one = findPost(root, postWithOne);
    JsonNode none = findPost(root, postWithNone);

    assertThat(two.get("images").isArray()).isTrue();
    assertThat(two.get("images").size()).isEqualTo(2);
    assertThat(two.get("images").get(0).get("imageId").asLong()).isPositive();
    assertThat(two.get("images").get(0).get("imageUrl").asText()).startsWith(urlPrefix);
    assertThat(two.get("images").get(1).get("imageId").asLong()).isPositive();
    assertThat(two.get("images").get(1).get("imageUrl").asText()).startsWith(urlPrefix);

    assertThat(one.get("images").size()).isEqualTo(1);
    assertThat(one.get("images").get(0).get("imageId").asLong()).isPositive();
    assertThat(one.get("images").get(0).get("imageUrl").asText())
        .isEqualTo(urlPrefix + "e2e/free/" + postWithOne + "/only.webp");

    assertThat(none.get("images").isArray()).isTrue();
    assertThat(none.get("images").size()).isZero();
  }

  @Test
  @DisplayName("[E-2] 연결된 이미지가 없으면 images는 빈 배열로 내려온다")
  void getPosts_question_noImages_returnsEmptyArray() throws Exception {
    setUpUser();

    Long postId = createQuestionPost("질문 제목", "질문 내용", 10L);

    JsonNode root = fetchPosts("?type=QUESTION&page=0&size=10");
    JsonNode post = findPost(root, postId);

    assertThat(post.has("images")).isTrue();
    assertThat(post.get("images").isNull()).isFalse();
    assertThat(post.get("images").isArray()).isTrue();
    assertThat(post.get("images").size()).isZero();
  }

  @Test
  @DisplayName("[E-3] FREE/QUESTION 혼합 조회 시 reference_type에 맞는 이미지만 매칭된다")
  void getPosts_mixedTypes_noCrossContamination() throws Exception {
    setUpUser();

    Long free1 = createFreePost("free 1");
    Long free2 = createFreePost("free 2");
    Long q1 = createQuestionPost("q1", "q1 content", 10L);
    Long q2 = createQuestionPost("q2", "q2 content", 10L);

    attachImage(free1, "COMMUNITY_FREE", "e2e/free/" + free1 + ".webp", 1);
    attachImage(free2, "COMMUNITY_FREE", "e2e/free/" + free2 + ".webp", 1);
    attachImage(q1, "COMMUNITY_QUESTION", "e2e/q/" + q1 + ".webp", 1);
    attachImage(q2, "COMMUNITY_QUESTION", "e2e/q/" + q2 + ".webp", 1);

    JsonNode freeRoot = fetchPosts("?type=FREE&page=0&size=20");
    JsonNode qRoot = fetchPosts("?type=QUESTION&page=0&size=20");

    assertThat(findPost(freeRoot, free1).get("images").get(0).get("imageUrl").asText())
        .isEqualTo(urlPrefix + "e2e/free/" + free1 + ".webp");
    assertThat(findPost(freeRoot, free2).get("images").get(0).get("imageUrl").asText())
        .isEqualTo(urlPrefix + "e2e/free/" + free2 + ".webp");
    assertThat(findPost(qRoot, q1).get("images").get(0).get("imageUrl").asText())
        .isEqualTo(urlPrefix + "e2e/q/" + q1 + ".webp");
    assertThat(findPost(qRoot, q2).get("images").get(0).get("imageUrl").asText())
        .isEqualTo(urlPrefix + "e2e/q/" + q2 + ".webp");
  }

  @Test
  @DisplayName("[E-4] 응답의 슬롯 순서는 img_order 오름차순을 따른다")
  void getPosts_slotOrder_followsInsertionOrder() throws Exception {
    setUpUser();

    Long postId = createFreePost("순서 테스트");
    attachImage(postId, "COMMUNITY_FREE", "e2e/slot/" + postId + "/A.webp", 1);
    attachImage(postId, "COMMUNITY_FREE", "e2e/slot/" + postId + "/B.webp", 2);
    attachImage(postId, "COMMUNITY_FREE", "e2e/slot/" + postId + "/C.webp", 3);

    JsonNode root = fetchPosts("?type=FREE&page=0&size=10");
    JsonNode post = findPost(root, postId);

    assertThat(post.get("images").size()).isEqualTo(3);
    assertThat(post.get("images").get(0).get("imageUrl").asText()).endsWith("A.webp");
    assertThat(post.get("images").get(1).get("imageUrl").asText()).endsWith("B.webp");
    assertThat(post.get("images").get(2).get("imageUrl").asText()).endsWith("C.webp");
  }

  @Test
  @DisplayName("[E-5] 대량 게시글 배치 조회 시 모든 images 슬롯 수가 시드와 일치한다")
  void getPosts_largeBatch_allImagesMatchSeed() throws Exception {
    setUpUser();

    int postCount = 50;
    long[] postIds = new long[postCount];
    int[] expectedCounts = new int[postCount];

    for (int i = 0; i < postCount; i++) {
      postIds[i] = createFreePost("대량 " + i);
      int imageCount = i % 4; // 0, 1, 2, 3 반복
      expectedCounts[i] = imageCount;
      for (int j = 0; j < imageCount; j++) {
        attachImage(
            postIds[i], "COMMUNITY_FREE", "e2e/batch/" + postIds[i] + "/" + j + ".webp", j + 1);
      }
    }

    long start = System.currentTimeMillis();
    JsonNode root = fetchPosts("?type=FREE&page=0&size=50");
    long elapsed = System.currentTimeMillis() - start;

    assertThat(elapsed).as("batch request should be fast").isLessThan(5_000L);
    assertThat(root.at("/data/posts").size()).isEqualTo(postCount);

    for (int i = 0; i < postCount; i++) {
      JsonNode post = findPost(root, postIds[i]);
      assertThat(post.get("images").size())
          .as("post %d should have %d images", postIds[i], expectedCounts[i])
          .isEqualTo(expectedCounts[i]);
    }
  }

  @Test
  @DisplayName("[E-6] final_object_key가 비어있으면 응답의 해당 슬롯은 null URL로 통과된다")
  void getPosts_blankFinalKey_yieldsNullSlot() throws Exception {
    setUpUser();

    Long postId = createFreePost("빈 key 테스트");
    attachImage(postId, "COMMUNITY_FREE", "e2e/blank/" + postId + "/ok.webp", 1);
    attachBlankKeyImage(postId, "COMMUNITY_FREE", 2);

    JsonNode root = fetchPosts("?type=FREE&page=0&size=10");
    JsonNode post = findPost(root, postId);
    JsonNode images = post.get("images");

    assertThat(images.size()).isEqualTo(2);
    assertThat(images.get(0).get("imageUrl").asText()).endsWith("ok.webp");
    assertThat(images.get(1).get("imageUrl").isNull()).isTrue();
  }
}
