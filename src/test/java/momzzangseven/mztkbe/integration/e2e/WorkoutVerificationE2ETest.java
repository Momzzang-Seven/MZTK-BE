package momzzangseven.mztkbe.integration.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
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
import momzzangseven.mztkbe.modules.auth.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.auth.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.image.infrastructure.persistence.entity.ImageEntity;
import momzzangseven.mztkbe.modules.image.infrastructure.persistence.repository.ImageJpaRepository;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.XpPolicyEntity;
import momzzangseven.mztkbe.modules.level.infrastructure.repository.XpLedgerJpaRepository;
import momzzangseven.mztkbe.modules.level.infrastructure.repository.XpPolicyJpaRepository;
import momzzangseven.mztkbe.modules.verification.application.dto.AiVerificationDecision;
import momzzangseven.mztkbe.modules.verification.application.dto.ExifMetadataInfo;
import momzzangseven.mztkbe.modules.verification.application.dto.PreparedAnalysisImage;
import momzzangseven.mztkbe.modules.verification.application.port.out.ExifMetadataPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.ObjectStoragePort;
import momzzangseven.mztkbe.modules.verification.application.port.out.PrepareAnalysisImagePort;
import momzzangseven.mztkbe.modules.verification.application.port.out.WorkoutImageAiPort;
import momzzangseven.mztkbe.modules.verification.infrastructure.persistence.entity.VerificationRequestEntity;
import momzzangseven.mztkbe.modules.verification.infrastructure.persistence.repository.VerificationRequestJpaRepository;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

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

  @MockBean private KakaoAuthPort kakaoAuthPort;
  @MockBean private GoogleAuthPort googleAuthPort;
  @MockBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;
  @MockBean private ObjectStoragePort objectStoragePort;
  @MockBean private PrepareAnalysisImagePort prepareAnalysisImagePort;
  @MockBean private ExifMetadataPort exifMetadataPort;
  @MockBean private WorkoutImageAiPort workoutImageAiPort;

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
    when(objectStoragePort.readBytes(tmpObjectKey)).thenReturn(new byte[] {1, 2, 3});
    when(prepareAnalysisImagePort.prepare(
            any(), any(String.class), any(Integer.class), anyDouble()))
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
    when(objectStoragePort.readBytes(tmpObjectKey)).thenReturn(new byte[] {1, 2, 3});
    when(prepareAnalysisImagePort.prepare(
            any(), any(String.class), any(Integer.class), anyDouble()))
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
        baseUrl + "/users/me/workout-photo-verifications",
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
}
