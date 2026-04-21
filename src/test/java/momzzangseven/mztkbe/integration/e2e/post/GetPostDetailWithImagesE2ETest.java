package momzzangseven.mztkbe.integration.e2e.post;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@TestPropertySource(
    properties = {
      "web3.chain-id=1337",
      "web3.eip712.chain-id=1337",
      "web3.eip7702.enabled=false",
      "web3.reward-token.enabled=false"
    })
@DisplayName("[E2E] GET /posts/{postId} 이미지 포함 응답 테스트")
class GetPostDetailWithImagesE2ETest extends E2ETestBase {

  @Autowired private ImageJpaRepository imageJpaRepository;

  @Value("${cloud.aws.s3.url-prefix}")
  private String urlPrefix;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  private String accessToken;
  private Long userId;

  private void setUpUser() {
    TestUser user = signupAndLogin("e2e-detail-user");
    this.accessToken = user.accessToken();
    this.userId = user.userId();
  }

  private Long createFreePost(String content) throws Exception {
    ResponseEntity<String> res =
        restTemplate.exchange(
            baseUrl() + "/posts/free",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("content", content, "imageIds", List.of()), bearerJsonHeaders(accessToken)),
            String.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return objectMapper.readTree(res.getBody()).at("/data/postId").asLong();
  }

  private Long attachImage(Long postId, String referenceType, String finalObjectKey, int order) {
    return imageJpaRepository
        .save(
            ImageEntity.builder()
                .userId(userId)
                .referenceType(referenceType)
                .referenceId(postId)
                .status("COMPLETED")
                .tmpObjectKey("tmp/" + UUID.randomUUID())
                .finalObjectKey(finalObjectKey)
                .imgOrder(order)
                .build())
        .getId();
  }

  private JsonNode fetchPostDetail(Long postId) throws Exception {
    ResponseEntity<String> res =
        restTemplate.exchange(
            baseUrl() + "/posts/" + postId,
            HttpMethod.GET,
            new HttpEntity<>(bearerJsonHeaders(accessToken)),
            String.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    return objectMapper.readTree(res.getBody());
  }

  @Test
  @DisplayName("[E-10] 상세 조회 응답의 images는 {imageId, imageUrl} 객체 배열이다")
  void getPostDetail_returnsImagesAsObjectArray() throws Exception {
    setUpUser();

    Long postId = createFreePost("상세 이미지 테스트");
    Long imageId1 = attachImage(postId, "COMMUNITY_FREE", "e2e/detail/" + postId + "/a.webp", 1);
    Long imageId2 = attachImage(postId, "COMMUNITY_FREE", "e2e/detail/" + postId + "/b.webp", 2);

    JsonNode root = fetchPostDetail(postId);

    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    JsonNode data = root.at("/data");
    assertThat(data.has("images")).isTrue();
    assertThat(data.has("imageUrls")).isFalse();
    assertThat(data.at("/writer").has("imageUrls")).isFalse();

    JsonNode images = data.get("images");
    assertThat(images.isArray()).isTrue();
    assertThat(images.size()).isEqualTo(2);

    assertThat(images.get(0).get("imageId").asLong()).isEqualTo(imageId1);
    assertThat(images.get(0).get("imageUrl").asText())
        .isEqualTo(urlPrefix + "e2e/detail/" + postId + "/a.webp");
    assertThat(images.get(1).get("imageId").asLong()).isEqualTo(imageId2);
    assertThat(images.get(1).get("imageUrl").asText())
        .isEqualTo(urlPrefix + "e2e/detail/" + postId + "/b.webp");
    assertThat(data.at("/question").isNull()).isTrue();
  }

  @Test
  @DisplayName("[E-11] 이미지가 없는 게시글 상세 응답의 images는 빈 배열로 내려온다")
  void getPostDetail_noImages_returnsEmptyArray() throws Exception {
    setUpUser();

    Long postId = createFreePost("이미지 없는 상세");

    JsonNode data = fetchPostDetail(postId).at("/data");

    assertThat(data.has("images")).isTrue();
    assertThat(data.has("imageUrls")).isFalse();
    assertThat(data.get("images").isArray()).isTrue();
    assertThat(data.get("images").size()).isZero();
  }

  @Test
  @DisplayName("[E-12] image 모듈이 빈 결과를 반환해도 images는 빈 배열로 내려온다")
  void getPostDetail_emptyImageResult_returnsEmptyArray() throws Exception {
    setUpUser();

    Long postId = createFreePost("null/empty 가드 테스트");

    JsonNode data = fetchPostDetail(postId).at("/data");

    assertThat(data.get("images").isArray()).isTrue();
    assertThat(data.get("images").size()).isZero();
  }
}
