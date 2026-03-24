package momzzangseven.mztkbe.integration.e2e.image;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import momzzangseven.mztkbe.modules.auth.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.auth.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.image.application.dto.LambdaCallbackCommand;
import momzzangseven.mztkbe.modules.image.application.port.in.HandleLambdaCallbackUseCase;
import momzzangseven.mztkbe.modules.image.domain.vo.LambdaCallbackStatus;
import momzzangseven.mztkbe.modules.image.infrastructure.persistence.entity.ImageEntity;
import momzzangseven.mztkbe.modules.image.infrastructure.persistence.repository.ImageJpaRepository;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 게시글 삭제/수정 시 이미지 연동 E2E 통합 테스트 (Local Server + Real PostgreSQL).
 *
 * <p>실행 조건:
 *
 * <ul>
 *   <li>로컬 PostgreSQL 서버 실행 필요 (application-integration.yml 참조)
 *   <li>./gradlew e2eTest 명령어로 실행
 * </ul>
 *
 * <p>커버 TC:
 *
 * <ul>
 *   <li>[TC-EVENT-001] FREE 게시글 삭제 → COMMUNITY_FREE 이미지 unlink
 *   <li>[TC-POST-001] 게시글 삭제 시 PostDeletedEvent 발행 → 이미지 unlink
 *   <li>[TC-POST-002] updatePost(imageIds=null) → 이미지 변경 없음
 *   <li>[TC-POST-003] updatePost(imageIds=[]) → 이미지 전체 unlink
 *   <li>[TC-POST-004] updatePost(imageIds=[id1,id2]) → 이미지 연결 및 순서 설정
 *   <li>[TC-POST-005] 소유자 아닌 사용자의 게시글 삭제 시 예외 + 이미지 unlink 없음
 * </ul>
 */
@Tag("e2e")
@ActiveProfiles("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("[E2E] 게시글 이미지 연동 전체 흐름 (Local Server + Real PostgreSQL)")
class PostImageIntegrationE2ETest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ImageJpaRepository imageJpaRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private HandleLambdaCallbackUseCase handleLambdaCallbackUseCase;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  private String baseUrl;
  private String accessToken;
  private String currentUserEmail;

  private final List<String> createdTmpKeys = new ArrayList<>();
  private final List<Long> createdPostIds = new ArrayList<>();

  // ===================================================================
  // 헬퍼 메서드
  // ===================================================================

  private static String uniqueEmail() {
    return "e2e-post-img-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
  }

  private HttpHeaders authHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(accessToken);
    return headers;
  }

  private void signup(String email, String password, String nickname) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    Map<String, String> body = Map.of("email", email, "password", password, "nickname", nickname);
    restTemplate.exchange(
        baseUrl + "/auth/signup", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
  }

  private String loginAndGetToken(String email, String password) throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    Map<String, Object> body = Map.of("provider", "LOCAL", "email", email, "password", password);
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/auth/login",
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            String.class);
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    return objectMapper.readTree(response.getBody()).at("/data/accessToken").asText();
  }

  /** 이미지 presigned URL 발급 후 imageId와 tmpObjectKey 쌍을 반환. */
  private record IssuedImage(long imageId, String tmpObjectKey) {}

  private IssuedImage issuePresignedUrl(String referenceType) throws Exception {
    Map<String, Object> body =
        Map.of("referenceType", referenceType, "images", List.of("photo.jpg"));
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/images/presigned-urls",
            HttpMethod.POST,
            new HttpEntity<>(body, authHeaders()),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode item = objectMapper.readTree(response.getBody()).at("/data/items/0");
    String tmpKey = item.at("/tmpObjectKey").asText();
    long imageId = item.at("/imageId").asLong();
    createdTmpKeys.add(tmpKey);
    return new IssuedImage(imageId, tmpKey);
  }

  /** Lambda 콜백을 시뮬레이션하여 이미지를 COMPLETED 상태로 전환. */
  private void simulateCompleted(String tmpKey) {
    String finalKey = "imgs/" + UUID.randomUUID() + ".webp";
    handleLambdaCallbackUseCase.execute(
        new LambdaCallbackCommand(LambdaCallbackStatus.COMPLETED, tmpKey, finalKey, null));
  }

  /** 자유 게시글을 생성하고 postId를 반환. */
  private long createFreePost() throws Exception {
    Map<String, Object> body = Map.of("content", "E2E 테스트 게시글");
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/posts/free",
            HttpMethod.POST,
            new HttpEntity<>(body, authHeaders()),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    long postId = objectMapper.readTree(response.getBody()).at("/data/postId").asLong();
    createdPostIds.add(postId);
    return postId;
  }

  /** 게시글의 이미지를 imageIds로 업데이트. */
  private ResponseEntity<String> updatePostImages(long postId, List<Long> imageIds) {
    Map<String, Object> body = Map.of("imageIds", imageIds);
    return restTemplate.exchange(
        baseUrl + "/posts/" + postId,
        HttpMethod.PATCH,
        new HttpEntity<>(body, authHeaders()),
        String.class);
  }

  /** imageIds 없이 게시글 내용만 업데이트. */
  private ResponseEntity<String> updatePostContentOnly(long postId) {
    Map<String, Object> body = Map.of("content", "수정된 내용");
    return restTemplate.exchange(
        baseUrl + "/posts/" + postId,
        HttpMethod.PATCH,
        new HttpEntity<>(body, authHeaders()),
        String.class);
  }

  /** 게시글을 삭제. */
  private ResponseEntity<String> deletePost(long postId) {
    return restTemplate.exchange(
        baseUrl + "/posts/" + postId,
        HttpMethod.DELETE,
        new HttpEntity<>(authHeaders()),
        String.class);
  }

  private ImageEntity findImageOrFail(String tmpKey) {
    return imageJpaRepository
        .findByTmpObjectKey(tmpKey)
        .orElseThrow(() -> new AssertionError("Image not found: " + tmpKey));
  }

  // ===================================================================
  // Setup / Teardown
  // ===================================================================

  @BeforeEach
  void setUp() throws Exception {
    baseUrl = "http://localhost:" + port;
    currentUserEmail = uniqueEmail();
    signup(currentUserEmail, "Test@1234!", "게시글이미지E2E");
    accessToken = loginAndGetToken(currentUserEmail, "Test@1234!");
  }

  @AfterEach
  void cleanup() {
    // 이미지 삭제
    createdTmpKeys.forEach(
        key -> imageJpaRepository.findByTmpObjectKey(key).ifPresent(imageJpaRepository::delete));
    createdTmpKeys.clear();

    // 게시글 삭제 (테스트 도중 삭제된 게시글은 없어도 안전하게 처리)
    createdPostIds.forEach(postId -> jdbcTemplate.update("DELETE FROM posts WHERE id = ?", postId));
    createdPostIds.clear();

    // 유저 삭제
    jdbcTemplate.update(
        "DELETE FROM user_progress WHERE user_id = (SELECT id FROM users WHERE email = ?)",
        currentUserEmail);
    jdbcTemplate.update("DELETE FROM users WHERE email = ?", currentUserEmail);
  }

  // ===================================================================
  // 테스트
  // ===================================================================

  @Test
  @DisplayName("[TC-POST-004] updatePost(imageIds=[id1,id2]) → 이미지가 게시글에 연결되고 순서가 설정된다")
  void updatePost_withImageIds_linksImagesInOrder() throws Exception {
    // 준비: 이미지 2장 발급 + COMPLETED
    IssuedImage img1 = issuePresignedUrl("COMMUNITY_FREE");
    IssuedImage img2 = issuePresignedUrl("COMMUNITY_FREE");
    simulateCompleted(img1.tmpObjectKey());
    simulateCompleted(img2.tmpObjectKey());

    // 준비: 게시글 생성
    long postId = createFreePost();

    // 실행: imageIds=[id2, id1] 순서로 업데이트 (순서 확인)
    ResponseEntity<String> response =
        updatePostImages(postId, List.of(img2.imageId(), img1.imageId()));
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

    // 검증: 두 이미지 모두 게시글에 연결됨
    ImageEntity linked1 = findImageOrFail(img1.tmpObjectKey());
    ImageEntity linked2 = findImageOrFail(img2.tmpObjectKey());

    assertThat(linked1.getReferenceId()).isEqualTo(postId);
    assertThat(linked1.getReferenceType()).isEqualTo("COMMUNITY_FREE");
    assertThat(linked2.getReferenceId()).isEqualTo(postId);
    assertThat(linked2.getReferenceType()).isEqualTo("COMMUNITY_FREE");

    // 검증: imageIds 요청 순서([id2, id1])대로 imgOrder가 배정됨
    assertThat(linked2.getImgOrder()).isEqualTo(1); // id2 → order=1
    assertThat(linked1.getImgOrder()).isEqualTo(2); // id1 → order=2
  }

  @Test
  @DisplayName("[TC-POST-002] updatePost(imageIds=null) → 기존 이미지 연결 상태 변경 없음")
  void updatePost_withNullImageIds_doesNotChangeImages() throws Exception {
    // 준비: 이미지 1장 발급 후 게시글에 연결
    IssuedImage img = issuePresignedUrl("COMMUNITY_FREE");
    simulateCompleted(img.tmpObjectKey());
    long postId = createFreePost();
    updatePostImages(postId, List.of(img.imageId()));

    // 실행: imageIds 필드 없이 content만 업데이트
    ResponseEntity<String> response = updatePostContentOnly(postId);
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

    // 검증: 이미지가 여전히 게시글에 연결되어 있음
    ImageEntity entity = findImageOrFail(img.tmpObjectKey());
    assertThat(entity.getReferenceId()).isEqualTo(postId);
    assertThat(entity.getReferenceType()).isEqualTo("COMMUNITY_FREE");
  }

  @Test
  @DisplayName("[TC-POST-003] updatePost(imageIds=[]) → 연결된 이미지 전체 unlink")
  void updatePost_withEmptyImageIds_unlinksAllImages() throws Exception {
    // 준비: 이미지 2장 발급 후 게시글에 연결
    IssuedImage img1 = issuePresignedUrl("COMMUNITY_FREE");
    IssuedImage img2 = issuePresignedUrl("COMMUNITY_FREE");
    simulateCompleted(img1.tmpObjectKey());
    simulateCompleted(img2.tmpObjectKey());
    long postId = createFreePost();
    updatePostImages(postId, List.of(img1.imageId(), img2.imageId()));

    // 실행: imageIds=[] 로 업데이트 → 전체 이미지 제거
    ResponseEntity<String> response = updatePostImages(postId, List.of());
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

    // 검증: 두 이미지 모두 unlink (referenceId=null)
    assertThat(findImageOrFail(img1.tmpObjectKey()).getReferenceId()).isNull();
    assertThat(findImageOrFail(img2.tmpObjectKey()).getReferenceId()).isNull();
  }

  @Test
  @DisplayName(
      "[TC-POST-001 / TC-EVENT-001] deletePost(FREE) → AFTER_COMMIT 이벤트로 COMMUNITY_FREE 이미지 unlink")
  void deletePost_free_unlinksImagesViaEvent() throws Exception {
    // 준비: 이미지 1장 발급 → COMPLETED → 게시글에 연결
    IssuedImage img = issuePresignedUrl("COMMUNITY_FREE");
    simulateCompleted(img.tmpObjectKey());
    long postId = createFreePost();
    updatePostImages(postId, List.of(img.imageId()));

    // 게시글에 연결 확인
    assertThat(findImageOrFail(img.tmpObjectKey()).getReferenceId()).isEqualTo(postId);

    // 실행: 게시글 삭제 → PostDeletedEvent(AFTER_COMMIT) → UnlinkImagesByReferenceService
    ResponseEntity<String> deleteResp = deletePost(postId);
    assertThat(deleteResp.getStatusCode().is2xxSuccessful()).isTrue();
    createdPostIds.remove(postId); // 이미 삭제됨

    // 검증: AFTER_COMMIT 이벤트 처리 후 이미지 unlink
    ImageEntity unlinked = findImageOrFail(img.tmpObjectKey());
    assertThat(unlinked.getReferenceId()).isNull();
    assertThat(unlinked.getReferenceType()).isNull();
  }

  @Test
  @DisplayName("[TC-POST-005] 소유자가 아닌 사용자의 게시글 삭제 시 403, 이미지 상태 변경 없음")
  void deletePost_nonOwner_returnsForbiddenAndKeepsImages() throws Exception {
    // 준비: 이미지 발급 → 게시글 연결
    IssuedImage img = issuePresignedUrl("COMMUNITY_FREE");
    simulateCompleted(img.tmpObjectKey());
    long postId = createFreePost();
    updatePostImages(postId, List.of(img.imageId()));

    // 다른 사용자로 로그인
    String otherEmail = uniqueEmail();
    signup(otherEmail, "Test@1234!", "다른유저");
    String otherToken = loginAndGetToken(otherEmail, "Test@1234!");
    HttpHeaders otherHeaders = new HttpHeaders();
    otherHeaders.setContentType(MediaType.APPLICATION_JSON);
    otherHeaders.setBearerAuth(otherToken);

    // 실행: 다른 사용자로 게시글 삭제 시도
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/posts/" + postId,
            HttpMethod.DELETE,
            new HttpEntity<>(otherHeaders),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    // 검증: 이미지 상태 변경 없음 (여전히 게시글에 연결)
    assertThat(findImageOrFail(img.tmpObjectKey()).getReferenceId()).isEqualTo(postId);

    // 정리: 다른 유저 삭제
    jdbcTemplate.update(
        "DELETE FROM user_progress WHERE user_id = (SELECT id FROM users WHERE email = ?)",
        otherEmail);
    jdbcTemplate.update("DELETE FROM users WHERE email = ?", otherEmail);
  }

  @Test
  @DisplayName("[TC-POST-001 변형] 이미지 없는 게시글 삭제 → 예외 없이 정상 처리")
  void deletePost_noImages_completesNormally() throws Exception {
    // 준비: 이미지 없이 게시글만 생성
    long postId = createFreePost();

    // 실행: 이미지 없는 게시글 삭제
    ResponseEntity<String> response = deletePost(postId);
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    createdPostIds.remove(postId);
  }

  @Test
  @DisplayName("[TC-POST-004 변형] 기존 이미지 일부 교체: 제거된 이미지 unlink, 신규 이미지 연결")
  void updatePost_partialImageReplace_correctlyLinksAndUnlinks() throws Exception {
    // 준비: 이미지 3장 발급 + COMPLETED
    IssuedImage img1 = issuePresignedUrl("COMMUNITY_FREE");
    IssuedImage img2 = issuePresignedUrl("COMMUNITY_FREE");
    IssuedImage img3 = issuePresignedUrl("COMMUNITY_FREE");
    simulateCompleted(img1.tmpObjectKey());
    simulateCompleted(img2.tmpObjectKey());
    simulateCompleted(img3.tmpObjectKey());

    long postId = createFreePost();
    // 처음에 img1, img2 연결
    updatePostImages(postId, List.of(img1.imageId(), img2.imageId()));

    // 실행: img2 → img3으로 교체 (img1은 유지, img2는 제거, img3 추가)
    updatePostImages(postId, List.of(img1.imageId(), img3.imageId()));

    // 검증
    assertThat(findImageOrFail(img1.tmpObjectKey()).getReferenceId()).isEqualTo(postId);
    assertThat(findImageOrFail(img2.tmpObjectKey()).getReferenceId()).isNull(); // 제거
    assertThat(findImageOrFail(img3.tmpObjectKey()).getReferenceId()).isEqualTo(postId);
  }
}
