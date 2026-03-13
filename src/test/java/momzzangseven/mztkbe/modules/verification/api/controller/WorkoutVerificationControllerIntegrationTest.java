package momzzangseven.mztkbe.modules.verification.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
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
import momzzangseven.mztkbe.modules.verification.application.exception.AiUnavailableException;
import momzzangseven.mztkbe.modules.verification.application.port.out.ExifMetadataPort;
import momzzangseven.mztkbe.modules.verification.application.port.out.ObjectStoragePort;
import momzzangseven.mztkbe.modules.verification.application.port.out.PrepareAnalysisImagePort;
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
    org.mockito.Mockito.when(objectStoragePort.readBytes(tmpObjectKey))
        .thenReturn(new byte[] {1, 2, 3});
    org.mockito.Mockito.when(exifMetadataPort.extract(org.mockito.ArgumentMatchers.any()))
        .thenReturn(Optional.of(new ExifMetadataInfo(LocalDateTime.now(KST))));
    org.mockito.Mockito.when(
            prepareAnalysisImagePort.prepare(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("jpg"),
                org.mockito.ArgumentMatchers.eq(1024),
                org.mockito.ArgumentMatchers.eq(0.80d)))
        .thenReturn(PreparedAnalysisImage.noop(Path.of("photo-analysis.webp")));
    org.mockito.Mockito.when(
            workoutImageAiPort.analyzeWorkoutPhoto(org.mockito.ArgumentMatchers.any()))
        .thenThrow(new AiUnavailableException("temporary outage"))
        .thenReturn(AiVerificationDecision.builder().approved(true).exerciseDate(today).build());

    MvcResult first =
        submit("/users/me/workout-photo-verifications", 901L, tmpObjectKey)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.verificationStatus").value("FAILED"))
            .andReturn();
    MvcResult second =
        submit("/users/me/workout-photo-verifications", 901L, tmpObjectKey)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"))
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
    org.mockito.Mockito.when(objectStoragePort.readBytes(tmpObjectKey))
        .thenReturn(new byte[] {1, 2, 3});
    org.mockito.Mockito.when(
            prepareAnalysisImagePort.prepare(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("png"),
                org.mockito.ArgumentMatchers.eq(1536),
                org.mockito.ArgumentMatchers.eq(0.85d)))
        .thenReturn(PreparedAnalysisImage.noop(Path.of("record-analysis.webp")));
    org.mockito.Mockito.when(
            workoutImageAiPort.analyzeWorkoutRecord(org.mockito.ArgumentMatchers.any()))
        .thenThrow(new AiUnavailableException("temporary outage"))
        .thenReturn(AiVerificationDecision.builder().approved(true).exerciseDate(today).build());

    MvcResult first =
        submit("/users/me/workout-record-verifications", 902L, tmpObjectKey)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.verificationStatus").value("FAILED"))
            .andReturn();
    MvcResult second =
        submit("/users/me/workout-record-verifications", 902L, tmpObjectKey)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"))
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
  @DisplayName("GET /users/me/verifications/{verificationId}는 저장된 verification row를 조회한다")
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
            get("/users/me/verifications/" + saved.getVerificationId()).with(userPrincipal(501L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.verificationId").value(saved.getVerificationId()))
        .andExpect(jsonPath("$.data.verificationKind").value("WORKOUT_RECORD"))
        .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"));
  }

  @Test
  @DisplayName(
      "GET /users/me/workout-completion/today는 xp source_ref와 latest verification을 함께 반영한다")
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
        .perform(get("/users/me/workout-completion/today").with(userPrincipal(777L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.todayCompleted").value(true))
        .andExpect(jsonPath("$.data.completedMethod").value("WORKOUT_PHOTO"))
        .andExpect(jsonPath("$.data.rewardGrantedToday").value(true))
        .andExpect(
            jsonPath("$.data.latestVerification.verificationId").value("verification-today-1"))
        .andExpect(jsonPath("$.data.latestVerification.verificationStatus").value("VERIFIED"));
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
}
