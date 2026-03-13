package momzzangseven.mztkbe.modules.verification.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Optional;
import momzzangseven.mztkbe.modules.image.infrastructure.persistence.entity.ImageEntity;
import momzzangseven.mztkbe.modules.image.infrastructure.persistence.repository.ImageJpaRepository;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.XpLedgerEntity;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.XpPolicyEntity;
import momzzangseven.mztkbe.modules.level.infrastructure.repository.XpLedgerJpaRepository;
import momzzangseven.mztkbe.modules.level.infrastructure.repository.XpPolicyJpaRepository;
import momzzangseven.mztkbe.modules.verification.application.dto.AiVerificationDecision;
import momzzangseven.mztkbe.modules.verification.application.dto.ExifMetadataInfo;
import momzzangseven.mztkbe.modules.verification.application.dto.PreparedAnalysisImage;
import momzzangseven.mztkbe.modules.verification.application.dto.PreparedOriginalImage;
import momzzangseven.mztkbe.modules.verification.application.dto.StorageObjectStream;
import momzzangseven.mztkbe.modules.verification.application.exception.AiUnavailableException;
import momzzangseven.mztkbe.modules.verification.application.port.out.ExifMetadataPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.ObjectStoragePort;
import momzzangseven.mztkbe.modules.verification.application.port.out.PrepareAnalysisImagePort;
import momzzangseven.mztkbe.modules.verification.application.port.out.PrepareOriginalImagePort;
import momzzangseven.mztkbe.modules.verification.application.port.out.WorkoutImageAiPort;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;
import momzzangseven.mztkbe.modules.verification.infrastructure.persistence.entity.VerificationRequestEntity;
import momzzangseven.mztkbe.modules.verification.infrastructure.persistence.repository.VerificationRequestJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@DisplayName("WorkoutVerificationController 실경로 통합 테스트 (MockMvc + H2)")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WorkoutVerificationControllerIntegrationTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  @org.springframework.beans.factory.annotation.Autowired protected MockMvc mockMvc;

  @org.springframework.beans.factory.annotation.Autowired protected ObjectMapper objectMapper;

  @org.springframework.beans.factory.annotation.Autowired
  protected VerificationRequestJpaRepository verificationRequestJpaRepository;

  @org.springframework.beans.factory.annotation.Autowired
  protected XpLedgerJpaRepository xpLedgerJpaRepository;

  @org.springframework.beans.factory.annotation.Autowired
  protected ImageJpaRepository imageJpaRepository;

  @org.springframework.beans.factory.annotation.Autowired
  protected XpPolicyJpaRepository xpPolicyJpaRepository;

  @org.springframework.beans.factory.annotation.Autowired protected JdbcTemplate jdbcTemplate;
  @org.springframework.beans.factory.annotation.Autowired protected EntityManager entityManager;

  @MockBean
  private momzzangseven.mztkbe.modules.web3.transaction.application.port.in
          .MarkTransactionSucceededUseCase
      txMarkTransactionSucceededUseCase;

  @MockBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionReceiptWorker
      txTransactionReceiptWorker;

  @MockBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionIssuerWorker
      txTransactionIssuerWorker;

  @MockBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .SignedRecoveryWorker
      txSignedRecoveryWorker;

  @MockBean private ObjectStoragePort objectStoragePort;
  @MockBean private PrepareOriginalImagePort prepareOriginalImagePort;
  @MockBean private PrepareAnalysisImagePort prepareAnalysisImagePort;
  @MockBean private ExifMetadataPort exifMetadataPort;
  @MockBean private WorkoutImageAiPort workoutImageAiPort;

  @Test
  @DisplayName("운동 사진 failed-today 재시도는 같은 verificationId로 XP ledger를 1회만 적재한다")
  void submitWorkoutPhoto_retryReusesVerificationIdAndStoresLedgerFields() throws Exception {
    LocalDate today = LocalDate.now(KST);
    String tmpObjectKey = "private/workout/photo-retry.jpg";
    seedWorkoutImage(901L, tmpObjectKey);
    ensureWorkoutXpPolicy(today);

    org.mockito.Mockito.when(objectStoragePort.exists(tmpObjectKey)).thenReturn(true);
    stubOpenStream(tmpObjectKey, "image/jpeg");
    org.mockito.Mockito.when(prepareOriginalImagePort.prepare(tmpObjectKey, "jpg"))
        .thenReturn(PreparedOriginalImage.noop(Path.of("photo-original.jpg")));
    org.mockito.Mockito.when(exifMetadataPort.extract(org.mockito.ArgumentMatchers.any()))
        .thenReturn(Optional.of(new ExifMetadataInfo(LocalDateTime.now(KST))));
    org.mockito.Mockito.when(
            prepareAnalysisImagePort.prepare(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(1024),
                org.mockito.ArgumentMatchers.eq(0.80d)))
        .thenReturn(PreparedAnalysisImage.noop(Path.of("photo-analysis.webp")));
    org.mockito.Mockito.when(
            workoutImageAiPort.analyzeWorkoutPhoto(org.mockito.ArgumentMatchers.any()))
        .thenThrow(new AiUnavailableException("temporary outage"))
        .thenReturn(AiVerificationDecision.builder().approved(true).exerciseDate(today).build());

    MvcResult first =
        submit("/verification/photo", 901L, tmpObjectKey)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.verificationStatus").value("FAILED"))
            .andExpect(jsonPath("$.data.exerciseDate").doesNotExist())
            .andExpect(jsonPath("$.data.completedMethod").doesNotExist())
            .andReturn();
    MvcResult second =
        submit("/verification/photo", 901L, tmpObjectKey)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"))
            .andExpect(jsonPath("$.data.exerciseDate").doesNotExist())
            .andExpect(jsonPath("$.data.completedMethod").value("WORKOUT_PHOTO"))
            .andReturn();

    String firstVerificationId =
        objectMapper
            .readTree(first.getResponse().getContentAsString())
            .at("/data/verificationId")
            .asText();
    String secondVerificationId =
        objectMapper
            .readTree(second.getResponse().getContentAsString())
            .at("/data/verificationId")
            .asText();

    assertThat(secondVerificationId).isEqualTo(firstVerificationId);
    assertThat(
            xpLedgerJpaRepository.findAll().stream()
                .filter(entry -> entry.getUserId().equals(901L))
                .filter(entry -> entry.getType() == XpType.WORKOUT)
                .count())
        .isEqualTo(1);

    XpLedgerEntity ledger =
        xpLedgerJpaRepository.findAll().stream()
            .filter(entry -> entry.getUserId().equals(901L))
            .findFirst()
            .orElseThrow();
    assertThat(ledger.getIdempotencyKey())
        .isEqualTo("workout:photo-verification:" + secondVerificationId);
    assertThat(ledger.getSourceRef())
        .isEqualTo("workout-photo-verification:" + secondVerificationId);
  }

  @Test
  @DisplayName("운동 기록 failed-today 재시도는 같은 verificationId로 XP ledger를 1회만 적재한다")
  void submitWorkoutRecord_retryReusesVerificationIdAndStoresLedgerFields() throws Exception {
    LocalDate today = LocalDate.now(KST);
    String tmpObjectKey = "private/workout/record-retry.png";
    seedWorkoutImage(902L, tmpObjectKey);
    ensureWorkoutXpPolicy(today);

    org.mockito.Mockito.when(objectStoragePort.exists(tmpObjectKey)).thenReturn(true);
    org.mockito.Mockito.when(prepareOriginalImagePort.prepare(tmpObjectKey, "png"))
        .thenReturn(PreparedOriginalImage.noop(Path.of("record-original.png")));
    org.mockito.Mockito.when(
            prepareAnalysisImagePort.prepare(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(1536),
                org.mockito.ArgumentMatchers.eq(0.85d)))
        .thenReturn(PreparedAnalysisImage.noop(Path.of("record-analysis.webp")));
    org.mockito.Mockito.when(
            workoutImageAiPort.analyzeWorkoutRecord(org.mockito.ArgumentMatchers.any()))
        .thenThrow(new AiUnavailableException("temporary outage"))
        .thenReturn(AiVerificationDecision.builder().approved(true).exerciseDate(today).build());

    MvcResult first =
        submit("/verification/record", 902L, tmpObjectKey)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.verificationStatus").value("FAILED"))
            .andExpect(jsonPath("$.data.completedMethod").doesNotExist())
            .andReturn();
    MvcResult second =
        submit("/verification/record", 902L, tmpObjectKey)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"))
            .andExpect(jsonPath("$.data.exerciseDate").value(today.toString()))
            .andExpect(jsonPath("$.data.completedMethod").value("WORKOUT_RECORD"))
            .andReturn();

    String firstVerificationId =
        objectMapper
            .readTree(first.getResponse().getContentAsString())
            .at("/data/verificationId")
            .asText();
    String secondVerificationId =
        objectMapper
            .readTree(second.getResponse().getContentAsString())
            .at("/data/verificationId")
            .asText();

    assertThat(secondVerificationId).isEqualTo(firstVerificationId);
    assertThat(
            xpLedgerJpaRepository.findAll().stream()
                .filter(entry -> entry.getUserId().equals(902L))
                .filter(entry -> entry.getType() == XpType.WORKOUT)
                .count())
        .isEqualTo(1);

    XpLedgerEntity ledger =
        xpLedgerJpaRepository.findAll().stream()
            .filter(entry -> entry.getUserId().equals(902L))
            .findFirst()
            .orElseThrow();
    assertThat(ledger.getIdempotencyKey())
        .isEqualTo("workout:record-verification:" + secondVerificationId);
    assertThat(ledger.getSourceRef())
        .isEqualTo("workout-record-verification:" + secondVerificationId);
  }

  @Test
  @DisplayName("GET /verification/{verificationId}는 저장된 verification row를 조회한다")
  void getVerificationDetail_realFlow_returnsSeededRow() throws Exception {
    VerificationRequestEntity saved =
        verificationRequestJpaRepository.save(
            VerificationRequestEntity.builder()
                .verificationId("verification-detail-1")
                .userId(501L)
                .verificationKind(VerificationKind.WORKOUT_RECORD.name())
                .status(VerificationStatus.VERIFIED.name())
                .exerciseDate(LocalDate.now(KST))
                .tmpObjectKey("private/workout/detail.png")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

    mockMvc
        .perform(
            get("/verification/" + saved.getVerificationId()).with(userPrincipal(501L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.verificationId").value(saved.getVerificationId()))
        .andExpect(jsonPath("$.data.verificationKind").value("WORKOUT_RECORD"))
        .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"));
  }

  @Test
  @DisplayName(
      "GET /verification/today-completion은 xp source_ref와 latest verification을 함께 반영한다")
  void getTodayCompletion_realFlow_derivesCompletedMethodAndLatestVerification() throws Exception {
    LocalDate today = LocalDate.now(KST);
    verificationRequestJpaRepository.save(
        VerificationRequestEntity.builder()
            .verificationId("verification-today-1")
            .userId(777L)
            .verificationKind(VerificationKind.WORKOUT_PHOTO.name())
            .status(VerificationStatus.VERIFIED.name())
            .exerciseDate(today)
            .tmpObjectKey("private/workout/today.jpg")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build());
    xpLedgerJpaRepository.save(
        XpLedgerEntity.builder()
            .userId(777L)
            .type(XpType.WORKOUT)
            .xpAmount(100)
            .earnedOn(today)
            .occurredAt(LocalDateTime.now(KST))
            .idempotencyKey("workout:photo-verification:verification-today-1")
            .sourceRef("workout-photo-verification:verification-today-1")
            .createdAt(LocalDateTime.now(KST))
            .build());

    mockMvc
        .perform(get("/verification/today-completion").with(userPrincipal(777L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.todayCompleted").value(true))
        .andExpect(jsonPath("$.data.completedMethod").value("WORKOUT_PHOTO"))
        .andExpect(jsonPath("$.data.rewardGrantedToday").value(true))
        .andExpect(
            jsonPath("$.data.latestVerification.verificationId").value("verification-today-1"))
        .andExpect(jsonPath("$.data.latestVerification.verificationStatus").value("VERIFIED"));
  }

  @Test
  @DisplayName("기존 verification kind가 다르면 submit은 409(VERIFICATION_005)로 실패한다")
  void submit_whenExistingKindDiffers_returnsConflict() throws Exception {
    String tmpObjectKey = "private/workout/kind-mismatch.jpg";
    verificationRequestJpaRepository.save(
        VerificationRequestEntity.builder()
            .verificationId("kind-mismatch-existing")
            .userId(903L)
            .verificationKind(VerificationKind.WORKOUT_RECORD.name())
            .status(VerificationStatus.REJECTED.name())
            .tmpObjectKey(tmpObjectKey)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build());

    submit("/verification/photo", 903L, tmpObjectKey)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value("FAIL"))
        .andExpect(jsonPath("$.code").value("VERIFICATION_005"));
  }

  @Test
  @DisplayName("기존 verification row 소유자가 다르면 submit은 403(VERIFICATION_004)로 실패한다")
  void submit_whenExistingRowBelongsToOtherUser_returnsForbidden() throws Exception {
    String tmpObjectKey = "private/workout/forbidden-existing.jpg";
    verificationRequestJpaRepository.save(
        VerificationRequestEntity.builder()
            .verificationId("forbidden-existing")
            .userId(1904L)
            .verificationKind(VerificationKind.WORKOUT_PHOTO.name())
            .status(VerificationStatus.REJECTED.name())
            .tmpObjectKey(tmpObjectKey)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build());

    submit("/verification/photo", 904L, tmpObjectKey)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value("FAIL"))
        .andExpect(jsonPath("$.code").value("VERIFICATION_004"));
  }

  @Test
  @DisplayName("어제 생성된 FAILED row는 재시도하지 않고 기존 FAILED 결과를 반환한다")
  void submit_whenFailedRowCreatedYesterday_returnsExistingFailedWithoutRetry() throws Exception {
    LocalDate today = LocalDate.now(KST);
    String tmpObjectKey = "private/workout/old-failed-no-retry.jpg";
    seedWorkoutImage(905L, tmpObjectKey);
    ensureWorkoutXpPolicy(today);
    VerificationRequestEntity failed =
        verificationRequestJpaRepository.save(
            VerificationRequestEntity.builder()
                .verificationId("old-failed-id")
                .userId(905L)
                .verificationKind(VerificationKind.WORKOUT_PHOTO.name())
                .status(VerificationStatus.FAILED.name())
                .tmpObjectKey(tmpObjectKey)
                .failureCode("EXTERNAL_AI_UNAVAILABLE")
                .updatedAt(Instant.now())
                .build());
    Instant oldCreatedAt = Instant.now().minusSeconds(60L * 60 * 24 * 2);
    jdbcTemplate.update(
        "update verification_requests set created_at = ? where verification_id = ?",
        Timestamp.from(oldCreatedAt),
        failed.getVerificationId());
    entityManager.flush();
    entityManager.clear();

    submit("/verification/photo", 905L, tmpObjectKey)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.verificationId").value("old-failed-id"))
        .andExpect(jsonPath("$.data.verificationStatus").value("FAILED"))
        .andExpect(jsonPath("$.data.failureCode").value("EXTERNAL_AI_UNAVAILABLE"))
        .andExpect(jsonPath("$.data.completedMethod").doesNotExist());

    VerificationRequestEntity refreshed =
        verificationRequestJpaRepository.findByVerificationId("old-failed-id").orElseThrow();
    assertThat(refreshed.getStatus()).isEqualTo(VerificationStatus.FAILED.name());
    assertThat(refreshed.getUpdatedAt()).isEqualTo(failed.getUpdatedAt());
    assertThat(refreshed.getCreatedAt()).isBefore(Instant.now().minusSeconds(60L * 60 * 24));
  }

  @Test
  @DisplayName("오늘 workout XP가 이미 존재하면 submit은 선차단 409(VERIFICATION_006)이다")
  void submit_whenTodayRewardAlreadyGranted_returnsConflictPrecheck() throws Exception {
    LocalDate today = LocalDate.now(KST);
    String tmpObjectKey = "private/workout/precheck-blocked.jpg";
    seedWorkoutImage(906L, tmpObjectKey);
    ensureWorkoutXpPolicy(today);
    xpLedgerJpaRepository.save(
        XpLedgerEntity.builder()
            .userId(906L)
            .type(XpType.WORKOUT)
            .xpAmount(100)
            .earnedOn(today)
            .occurredAt(LocalDateTime.now(KST))
            .idempotencyKey("workout:photo-verification:already-rewarded")
            .sourceRef("workout-photo-verification:already-rewarded")
            .createdAt(LocalDateTime.now(KST))
            .build());

    submit("/verification/photo", 906L, tmpObjectKey)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value("FAIL"))
        .andExpect(jsonPath("$.code").value("VERIFICATION_006"))
        .andExpect(jsonPath("$.data.completedMethod").value("WORKOUT_PHOTO"))
        .andExpect(jsonPath("$.data.earnedDate").value(today.toString()));
  }

  @Test
  @DisplayName("tmpObjectKey가 image 테이블에 없으면 submit은 404(VERIFICATION_003)이다")
  void submit_whenUploadRowIsMissing_returnsUploadNotFound() throws Exception {
    submit("/verification/photo", 907L, "private/workout/no-image-row.jpg")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value("FAIL"))
        .andExpect(jsonPath("$.code").value("VERIFICATION_003"));
  }

  @Test
  @DisplayName("기존 VERIFIED row가 있으면 image/object 여부와 무관하게 기존 결과를 즉시 반환한다")
  void submit_whenExistingVerifiedRowPresent_returnsExistingWithoutUploadLookup() throws Exception {
    String tmpObjectKey = "private/workout/existing-only.jpg";
    verificationRequestJpaRepository.save(
        VerificationRequestEntity.builder()
            .verificationId("existing-verified-id")
            .userId(908L)
            .verificationKind(VerificationKind.WORKOUT_PHOTO.name())
            .status(VerificationStatus.VERIFIED.name())
            .tmpObjectKey(tmpObjectKey)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build());

    submit("/verification/photo", 908L, tmpObjectKey)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.verificationId").value("existing-verified-id"))
        .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"));
  }

  private RequestPostProcessor userPrincipal(Long userId) {
    java.util.Objects.requireNonNull(userId, "userId");
    java.util.List<org.springframework.security.core.authority.SimpleGrantedAuthority>
        grantedAuthorities =
            Arrays.stream(new String[] {"ROLE_USER"})
                .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                .toList();
    org.springframework.security.authentication.UsernamePasswordAuthenticationToken token =
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            userId, null, grantedAuthorities);
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.authentication(token);
  }

  private org.springframework.test.web.servlet.ResultActions submit(
      String path, Long userId, String tmpObjectKey) throws Exception {
    return mockMvc.perform(
        post(path)
            .with(userPrincipal(userId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                objectMapper.writeValueAsString(java.util.Map.of("tmpObjectKey", tmpObjectKey))));
  }

  private void seedWorkoutImage(Long userId, String tmpObjectKey) {
    imageJpaRepository.save(
        ImageEntity.builder()
            .userId(userId)
            .referenceType("WORKOUT")
            .status("COMPLETED")
            .tmpObjectKey(tmpObjectKey)
            .finalObjectKey(tmpObjectKey)
            .build());
  }

  private void ensureWorkoutXpPolicy(LocalDate today) {
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
            .effectiveFrom(today.atStartOfDay().minusDays(1))
            .enabled(true)
            .build());
  }

  private void stubOpenStream(String objectKey, String contentType) throws IOException {
    org.mockito.Mockito.when(objectStoragePort.openStream(objectKey))
        .thenAnswer(
            invocation ->
                new StorageObjectStream(
                    new ByteArrayInputStream(new byte[] {1, 2, 3}), 3L, contentType));
  }
}
