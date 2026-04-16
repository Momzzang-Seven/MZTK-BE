package momzzangseven.mztkbe.integration.e2e.image;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("[E2E] GetImagesStatus 전체 흐름 테스트")
class GetImagesStatusE2ETest extends E2ETestBase {

  @Autowired private ImageJpaRepository imageJpaRepository;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  private String accessToken;
  private Long currentUserId;

  @BeforeEach
  void setUp() {
    TestUser user = signupAndLogin("이미지상태E2E");
    accessToken = user.accessToken();
    currentUserId = user.userId();
  }

  private Long saveImage(
      Long userId, String referenceType, Long referenceId, String status, String finalObjectKey) {
    ImageEntity image =
        ImageEntity.builder()
            .userId(userId)
            .referenceType(referenceType)
            .referenceId(referenceId)
            .status(status)
            .tmpObjectKey("e2e/status/" + UUID.randomUUID() + ".jpg")
            .finalObjectKey(finalObjectKey)
            .imgOrder(1)
            .build();
    return imageJpaRepository.save(image).getId();
  }

  private ResponseEntity<String> getStatuses(String token, List<Long> ids) {
    HttpHeaders headers = new HttpHeaders();
    if (token != null) {
      headers.setBearerAuth(token);
    }

    StringBuilder url = new StringBuilder(baseUrl()).append("/images/status?");
    for (Long id : ids) {
      url.append("ids=").append(id).append("&");
    }
    return restTemplate.exchange(
        url.toString(), HttpMethod.GET, new HttpEntity<>(headers), String.class);
  }

  @Test
  @DisplayName("미인증 요청은 401")
  void getImagesStatus_unauthenticated_returns401() {
    ResponseEntity<String> response = getStatuses(null, List.of(1L));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("ids 누락은 400 VALIDATION_002")
  void getImagesStatus_missingIds_returns400() throws Exception {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/images/status",
            HttpMethod.GET,
            new HttpEntity<>(bearerJsonHeaders(accessToken)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.at("/code").asText()).isEqualTo("VALIDATION_002");
  }

  @Test
  @DisplayName("owned image status를 반환하고 missing/foreign image는 NOT_FOUND로 마스킹한다")
  void getImagesStatus_returnsStatusesAndMasksMissingOrForeign() throws Exception {
    Long foreignUserId =
        signupAndLogin("foreign-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6))
            .userId();
    Long completedId =
        saveImage(currentUserId, "COMMUNITY_FREE", null, "COMPLETED", "public/free/a.webp");
    Long pendingId = saveImage(currentUserId, "COMMUNITY_FREE", null, "PENDING", null);
    Long failedId = saveImage(currentUserId, "COMMUNITY_FREE", null, "FAILED", null);
    Long foreignId = saveImage(foreignUserId, "COMMUNITY_FREE", null, "COMPLETED", "x.webp");

    ResponseEntity<String> response =
        getStatuses(
            accessToken, List.of(completedId, pendingId, failedId, 999999L, foreignId, pendingId));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode images = objectMapper.readTree(response.getBody()).at("/data/images");
    assertThat(images.size()).isEqualTo(6);
    assertThat(images.get(0).at("/imageId").asLong()).isEqualTo(completedId);
    assertThat(images.get(0).at("/status").asText()).isEqualTo("COMPLETED");
    assertThat(images.get(1).at("/status").asText()).isEqualTo("PENDING");
    assertThat(images.get(2).at("/status").asText()).isEqualTo("FAILED");
    assertThat(images.get(3).at("/status").asText()).isEqualTo("NOT_FOUND");
    assertThat(images.get(4).at("/status").asText()).isEqualTo("NOT_FOUND");
    assertThat(images.get(5).at("/imageId").asLong()).isEqualTo(pendingId);
    assertThat(images.get(5).at("/status").asText()).isEqualTo("PENDING");
    assertThat(images.get(0).get("finalObjectKey")).isNull();
  }

  @Test
  @DisplayName("ids 11개 초과는 400 VALIDATION_001")
  void getImagesStatus_tooManyIds_returns400() throws Exception {
    ResponseEntity<String> response =
        getStatuses(accessToken, List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.at("/code").asText()).isEqualTo("VALIDATION_001");
  }
}
