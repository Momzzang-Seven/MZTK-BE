package momzzangseven.mztkbe.integration.e2e.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.image.infrastructure.persistence.entity.ImageEntity;
import momzzangseven.mztkbe.modules.image.infrastructure.persistence.repository.ImageJpaRepository;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.XpPolicyEntity;
import momzzangseven.mztkbe.modules.level.infrastructure.repository.XpLedgerJpaRepository;
import momzzangseven.mztkbe.modules.level.infrastructure.repository.XpPolicyJpaRepository;
import momzzangseven.mztkbe.modules.verification.application.dto.AiVerificationDecision;
import momzzangseven.mztkbe.modules.verification.application.dto.ExifMetadataInfo;
import momzzangseven.mztkbe.modules.verification.application.dto.PreparedAnalysisImage;
import momzzangseven.mztkbe.modules.verification.application.dto.PreparedOriginalImage;
import momzzangseven.mztkbe.modules.verification.application.dto.StorageObjectStream;
import momzzangseven.mztkbe.modules.verification.application.port.out.ExifMetadataPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.ObjectStoragePort;
import momzzangseven.mztkbe.modules.verification.application.port.out.PrepareAnalysisImagePort;
import momzzangseven.mztkbe.modules.verification.application.port.out.PrepareOriginalImagePort;
import momzzangseven.mztkbe.modules.verification.application.port.out.WorkoutImageAiPort;
import momzzangseven.mztkbe.modules.verification.infrastructure.persistence.entity.VerificationRequestEntity;
import momzzangseven.mztkbe.modules.verification.infrastructure.persistence.repository.VerificationRequestJpaRepository;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
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

@Tag("e2e")
@ActiveProfiles("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("[E2E] Workout Verification 동시 submit 흐름 테스트")
class WorkoutVerificationE2ETest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ImageJpaRepository imageJpaRepository;
  @Autowired private VerificationRequestJpaRepository verificationRequestJpaRepository;
  @Autowired private XpLedgerJpaRepository xpLedgerJpaRepository;
  @Autowired private XpPolicyJpaRepository xpPolicyJpaRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;
  @MockitoBean private ObjectStoragePort objectStoragePort;
  @MockitoBean private PrepareOriginalImagePort prepareOriginalImagePort;
  @MockitoBean private PrepareAnalysisImagePort prepareAnalysisImagePort;
  @MockitoBean private ExifMetadataPort exifMetadataPort;
  @MockitoBean private WorkoutImageAiPort workoutImageAiPort;

  @Test
  @DisplayName("같은 tmpObjectKey 동시 submit은 단일 verification row와 단일 XP 지급으로 수렴한다")
  void duplicateSubmitConvergesToSingleVerificationAndSingleReward() throws Exception {
    String baseUrl = "http://localhost:" + port;
    String email = uniqueEmail();
    long userId = signup(baseUrl, email, "Test@1234!", "워크아웃E2E유저");
    String accessToken = loginAndGetAccessToken(baseUrl, email, "Test@1234!");

    LocalDate today = LocalDate.now(KST);
    ensureWorkoutXpPolicy(today);

    String tmpObjectKey = "private/workout/e2e-" + UUID.randomUUID() + ".jpg";
    imageJpaRepository.save(
        ImageEntity.builder()
            .userId(userId)
            .referenceType("WORKOUT")
            .status("COMPLETED")
            .tmpObjectKey(tmpObjectKey)
            .finalObjectKey(tmpObjectKey)
            .build());

    when(objectStoragePort.exists(tmpObjectKey)).thenReturn(true);
    stubOpenStream(tmpObjectKey, "image/jpeg");
    when(prepareOriginalImagePort.prepare(tmpObjectKey, "jpg"))
        .thenReturn(PreparedOriginalImage.noop(Path.of("original.jpg")));
    when(prepareAnalysisImagePort.prepare(any(Path.class), any(Integer.class), anyDouble()))
        .thenReturn(PreparedAnalysisImage.noop(Path.of("analysis.webp")));
    when(exifMetadataPort.extract(any()))
        .thenReturn(Optional.of(new ExifMetadataInfo(LocalDateTime.now(KST))));

    AtomicBoolean firstAiCall = new AtomicBoolean(true);
    CountDownLatch firstAiEntered = new CountDownLatch(1);
    CountDownLatch releaseFirstAi = new CountDownLatch(1);
    when(workoutImageAiPort.analyzeWorkoutPhoto(any()))
        .thenAnswer(
            invocation -> {
              if (firstAiCall.compareAndSet(true, false)) {
                firstAiEntered.countDown();
                assertThat(releaseFirstAi.await(5, TimeUnit.SECONDS)).isTrue();
              }
              return AiVerificationDecision.builder().approved(true).exerciseDate(today).build();
            });

    CyclicBarrier startBarrier = new CyclicBarrier(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<ResponseEntity<String>> first =
          executor.submit(() -> submitPhoto(baseUrl, accessToken, tmpObjectKey, startBarrier));
      Future<ResponseEntity<String>> second =
          executor.submit(() -> submitPhoto(baseUrl, accessToken, tmpObjectKey, startBarrier));

      assertThat(firstAiEntered.await(5, TimeUnit.SECONDS)).isTrue();
      releaseFirstAi.countDown();

      ResponseEntity<String> firstResponse = first.get(10, TimeUnit.SECONDS);
      ResponseEntity<String> secondResponse = second.get(10, TimeUnit.SECONDS);

      assertThat(firstResponse.getStatusCode().is2xxSuccessful()).isTrue();
      assertThat(secondResponse.getStatusCode().is2xxSuccessful()).isTrue();
      assertThat(
              objectMapper
                  .readTree(firstResponse.getBody())
                  .at("/data/exerciseDate")
                  .isMissingNode())
          .isTrue();
      assertThat(
              objectMapper
                  .readTree(secondResponse.getBody())
                  .at("/data/exerciseDate")
                  .isMissingNode())
          .isTrue();

      String firstVerificationId =
          objectMapper.readTree(firstResponse.getBody()).at("/data/verificationId").asText();
      String secondVerificationId =
          objectMapper.readTree(secondResponse.getBody()).at("/data/verificationId").asText();

      assertThat(firstVerificationId).isEqualTo(secondVerificationId);
      assertThat(
              verificationRequestJpaRepository.findAll().stream()
                  .filter(it -> tmpObjectKey.equals(it.getTmpObjectKey()))
                  .count())
          .isEqualTo(1);
      assertThat(
              xpLedgerJpaRepository.findAll().stream()
                  .filter(it -> it.getUserId().equals(userId))
                  .filter(it -> it.getType() == XpType.WORKOUT)
                  .count())
          .isEqualTo(1);
      assertThat(
              xpLedgerJpaRepository.findAll().stream()
                  .filter(it -> it.getUserId().equals(userId))
                  .findFirst()
                  .orElseThrow()
                  .getSourceRef())
          .isEqualTo("workout-photo-verification:" + firstVerificationId);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  @DisplayName("today FAILED 재시도 동시 submit은 row lock 이후 단일 AI 호출과 단일 XP 지급으로 수렴한다")
  void failedTodayRetryConvergesAfterRowLock() throws Exception {
    String baseUrl = "http://localhost:" + port;
    String email = uniqueEmail();
    long userId = signup(baseUrl, email, "Test@1234!", "워크아웃재시도E2E");
    String accessToken = loginAndGetAccessToken(baseUrl, email, "Test@1234!");

    LocalDate today = LocalDate.now(KST);
    ensureWorkoutXpPolicy(today);

    String tmpObjectKey = "private/workout/e2e-retry-" + UUID.randomUUID() + ".jpg";
    imageJpaRepository.save(
        ImageEntity.builder()
            .userId(userId)
            .referenceType("WORKOUT")
            .status("COMPLETED")
            .tmpObjectKey(tmpObjectKey)
            .finalObjectKey(tmpObjectKey)
            .build());

    VerificationRequestEntity failed =
        verificationRequestJpaRepository.save(
            VerificationRequestEntity.builder()
                .verificationId(UUID.randomUUID().toString())
                .userId(userId)
                .verificationKind("WORKOUT_PHOTO")
                .status("FAILED")
                .tmpObjectKey(tmpObjectKey)
                .failureCode("EXTERNAL_AI_UNAVAILABLE")
                .createdAt(Instant.now().minusSeconds(120))
                .updatedAt(Instant.now().minusSeconds(120))
                .build());
    Instant beforeRetryUpdatedAt = failed.getUpdatedAt();

    when(objectStoragePort.exists(tmpObjectKey)).thenReturn(true);
    stubOpenStream(tmpObjectKey, "image/jpeg");
    when(prepareOriginalImagePort.prepare(tmpObjectKey, "jpg"))
        .thenReturn(PreparedOriginalImage.noop(Path.of("original.jpg")));
    when(prepareAnalysisImagePort.prepare(any(Path.class), any(Integer.class), anyDouble()))
        .thenReturn(PreparedAnalysisImage.noop(Path.of("analysis.webp")));
    when(exifMetadataPort.extract(any()))
        .thenReturn(Optional.of(new ExifMetadataInfo(LocalDateTime.now(KST))));

    java.util.concurrent.atomic.AtomicInteger aiCalls =
        new java.util.concurrent.atomic.AtomicInteger();
    AtomicBoolean firstAiCall = new AtomicBoolean(true);
    CountDownLatch firstAiEntered = new CountDownLatch(1);
    CountDownLatch releaseFirstAi = new CountDownLatch(1);
    when(workoutImageAiPort.analyzeWorkoutPhoto(any()))
        .thenAnswer(
            invocation -> {
              aiCalls.incrementAndGet();
              if (firstAiCall.compareAndSet(true, false)) {
                firstAiEntered.countDown();
                assertThat(releaseFirstAi.await(5, TimeUnit.SECONDS)).isTrue();
              }
              return AiVerificationDecision.builder().approved(true).exerciseDate(today).build();
            });

    CyclicBarrier startBarrier = new CyclicBarrier(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<ResponseEntity<String>> first =
          executor.submit(() -> submitPhoto(baseUrl, accessToken, tmpObjectKey, startBarrier));
      Future<ResponseEntity<String>> second =
          executor.submit(() -> submitPhoto(baseUrl, accessToken, tmpObjectKey, startBarrier));

      assertThat(firstAiEntered.await(5, TimeUnit.SECONDS)).isTrue();
      releaseFirstAi.countDown();

      ResponseEntity<String> firstResponse = first.get(10, TimeUnit.SECONDS);
      ResponseEntity<String> secondResponse = second.get(10, TimeUnit.SECONDS);

      assertThat(firstResponse.getStatusCode().is2xxSuccessful()).isTrue();
      assertThat(secondResponse.getStatusCode().is2xxSuccessful()).isTrue();
      assertThat(
              objectMapper
                  .readTree(firstResponse.getBody())
                  .at("/data/exerciseDate")
                  .isMissingNode())
          .isTrue();
      assertThat(
              objectMapper
                  .readTree(secondResponse.getBody())
                  .at("/data/exerciseDate")
                  .isMissingNode())
          .isTrue();

      String firstVerificationId =
          objectMapper.readTree(firstResponse.getBody()).at("/data/verificationId").asText();
      String secondVerificationId =
          objectMapper.readTree(secondResponse.getBody()).at("/data/verificationId").asText();

      assertThat(firstVerificationId).isEqualTo(failed.getVerificationId());
      assertThat(secondVerificationId).isEqualTo(failed.getVerificationId());
      assertThat(aiCalls).hasValue(1);
      assertThat(
              xpLedgerJpaRepository.findAll().stream()
                  .filter(it -> it.getUserId().equals(userId))
                  .filter(it -> it.getType() == XpType.WORKOUT)
                  .count())
          .isEqualTo(1);
      VerificationRequestEntity refreshed =
          verificationRequestJpaRepository
              .findByVerificationId(failed.getVerificationId())
              .orElseThrow();
      assertThat(refreshed.getStatus()).isEqualTo("VERIFIED");
      assertThat(refreshed.getUpdatedAt()).isAfter(beforeRetryUpdatedAt);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  @DisplayName("기존 verification kind가 다르면 submit은 409(VERIFICATION_005)를 반환한다")
  void submitPhoto_withExistingDifferentKind_returnsConflict() throws Exception {
    String baseUrl = "http://localhost:" + port;
    String email = uniqueEmail();
    long userId = signup(baseUrl, email, "Test@1234!", "워크아웃종류충돌E2E");
    String accessToken = loginAndGetAccessToken(baseUrl, email, "Test@1234!");

    String tmpObjectKey = "private/workout/e2e-kind-mismatch-" + UUID.randomUUID() + ".jpg";
    imageJpaRepository.save(
        ImageEntity.builder()
            .userId(userId)
            .referenceType("WORKOUT")
            .status("COMPLETED")
            .tmpObjectKey(tmpObjectKey)
            .finalObjectKey(tmpObjectKey)
            .build());
    verificationRequestJpaRepository.save(
        VerificationRequestEntity.builder()
            .verificationId(UUID.randomUUID().toString())
            .userId(userId)
            .verificationKind("WORKOUT_RECORD")
            .status("REJECTED")
            .tmpObjectKey(tmpObjectKey)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build());

    ResponseEntity<String> response = submitPhoto(baseUrl, accessToken, tmpObjectKey);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(objectMapper.readTree(response.getBody()).at("/status").asText()).isEqualTo("FAIL");
    assertThat(objectMapper.readTree(response.getBody()).at("/code").asText())
        .isEqualTo("VERIFICATION_005");
    assertThat(
            verificationRequestJpaRepository.findAll().stream()
                .filter(v -> tmpObjectKey.equals(v.getTmpObjectKey()))
                .count())
        .isEqualTo(1);
  }

  @Test
  @DisplayName("어제 생성된 FAILED row는 재시도 없이 기존 FAILED를 그대로 반환한다")
  void submitPhoto_withOldFailedRow_returnsExistingFailedWithoutRetry() throws Exception {
    String baseUrl = "http://localhost:" + port;
    String email = uniqueEmail();
    long userId = signup(baseUrl, email, "Test@1234!", "워크아웃실패재사용E2E");
    String accessToken = loginAndGetAccessToken(baseUrl, email, "Test@1234!");

    String tmpObjectKey = "private/workout/e2e-old-failed-" + UUID.randomUUID() + ".jpg";
    Instant createdAt = Instant.now().minusSeconds(60L * 60 * 24 * 2);
    VerificationRequestEntity failed =
        verificationRequestJpaRepository.save(
            VerificationRequestEntity.builder()
                .verificationId(UUID.randomUUID().toString())
                .userId(userId)
                .verificationKind("WORKOUT_PHOTO")
                .status("FAILED")
                .tmpObjectKey(tmpObjectKey)
                .failureCode("EXTERNAL_AI_UNAVAILABLE")
                .updatedAt(Instant.now())
                .build());
    jdbcTemplate.update(
        "update verification_requests set created_at = ? where verification_id = ?",
        Timestamp.from(createdAt),
        failed.getVerificationId());

    ResponseEntity<String> response = submitPhoto(baseUrl, accessToken, tmpObjectKey);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(objectMapper.readTree(response.getBody()).at("/data/verificationStatus").asText())
        .isEqualTo("FAILED");
    assertThat(objectMapper.readTree(response.getBody()).at("/data/failureCode").asText())
        .isEqualTo("EXTERNAL_AI_UNAVAILABLE");
    assertThat(
            objectMapper.readTree(response.getBody()).at("/data/completedMethod").isMissingNode())
        .isTrue();
    assertThat(
            xpLedgerJpaRepository.findAll().stream()
                .filter(it -> it.getUserId().equals(userId))
                .filter(it -> it.getType() == XpType.WORKOUT)
                .count())
        .isZero();
  }

  @Test
  @DisplayName("오늘 workout 보상이 이미 있으면 submit은 409(VERIFICATION_006)으로 선차단된다")
  void submitPhoto_whenTodayRewardExists_returnsConflictPrecheck() throws Exception {
    String baseUrl = "http://localhost:" + port;
    String email = uniqueEmail();
    long userId = signup(baseUrl, email, "Test@1234!", "워크아웃선차단E2E");
    String accessToken = loginAndGetAccessToken(baseUrl, email, "Test@1234!");

    LocalDate today = LocalDate.now(KST);
    ensureWorkoutXpPolicy(today);
    String tmpObjectKey = "private/workout/e2e-precheck-" + UUID.randomUUID() + ".jpg";
    imageJpaRepository.save(
        ImageEntity.builder()
            .userId(userId)
            .referenceType("WORKOUT")
            .status("COMPLETED")
            .tmpObjectKey(tmpObjectKey)
            .finalObjectKey(tmpObjectKey)
            .build());
    xpLedgerJpaRepository.save(
        momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.XpLedgerEntity
            .builder()
            .userId(userId)
            .type(XpType.WORKOUT)
            .xpAmount(100)
            .earnedOn(today)
            .occurredAt(LocalDateTime.now(KST))
            .idempotencyKey("workout:photo-verification:precheck")
            .sourceRef("workout-photo-verification:precheck")
            .createdAt(LocalDateTime.now(KST))
            .build());

    ResponseEntity<String> response = submitPhoto(baseUrl, accessToken, tmpObjectKey);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(objectMapper.readTree(response.getBody()).at("/status").asText()).isEqualTo("FAIL");
    assertThat(objectMapper.readTree(response.getBody()).at("/code").asText())
        .isEqualTo("VERIFICATION_006");
  }

  private static String uniqueEmail() {
    return "e2e-workout-"
        + UUID.randomUUID().toString().replace("-", "").substring(0, 10)
        + "@example.com";
  }

  private long signup(String baseUrl, String email, String password, String nickname)
      throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/auth/signup",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("email", email, "password", password, "nickname", nickname), headers),
            String.class);
    return objectMapper.readTree(response.getBody()).at("/data/userId").asLong();
  }

  private String loginAndGetAccessToken(String baseUrl, String email, String password)
      throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    Map<String, Object> body = Map.of("provider", "LOCAL", "email", email, "password", password);
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl + "/auth/login",
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            String.class);
    return objectMapper.readTree(response.getBody()).at("/data/accessToken").asText();
  }

  private ResponseEntity<String> submitPhoto(
      String baseUrl, String accessToken, String tmpObjectKey, CyclicBarrier startBarrier)
      throws Exception {
    startBarrier.await(5, TimeUnit.SECONDS);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(accessToken);
    return restTemplate.exchange(
        baseUrl + "/verification/photo",
        HttpMethod.POST,
        new HttpEntity<>(Map.of("tmpObjectKey", tmpObjectKey), headers),
        String.class);
  }

  private ResponseEntity<String> submitPhoto(
      String baseUrl, String accessToken, String tmpObjectKey) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(accessToken);
    return restTemplate.exchange(
        baseUrl + "/verification/photo",
        HttpMethod.POST,
        new HttpEntity<>(Map.of("tmpObjectKey", tmpObjectKey), headers),
        String.class);
  }

  private void ensureWorkoutXpPolicy(LocalDate today) {
    LocalDateTime effectiveFrom = today.atStartOfDay();
    if (!xpPolicyJpaRepository
        .findActiveByType(
            XpType.WORKOUT,
            LocalDateTime.now(KST),
            org.springframework.data.domain.PageRequest.of(0, 1))
        .isEmpty()) {
      return;
    }
    xpPolicyJpaRepository.save(
        XpPolicyEntity.builder()
            .type(XpType.WORKOUT)
            .xpAmount(100)
            .dailyCap(1)
            .effectiveFrom(effectiveFrom.minusDays(1))
            .enabled(true)
            .build());
  }

  private void stubOpenStream(String objectKey, String contentType) throws IOException {
    when(objectStoragePort.openStream(objectKey))
        .thenAnswer(
            invocation ->
                new StorageObjectStream(
                    new ByteArrayInputStream(new byte[] {1, 2, 3}), 3L, contentType));
  }
}
