package momzzangseven.mztkbe.modules.verification.api.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Arrays;
import momzzangseven.mztkbe.modules.verification.application.dto.LatestVerificationItem;
import momzzangseven.mztkbe.modules.verification.application.dto.SubmitWorkoutVerificationCommand;
import momzzangseven.mztkbe.modules.verification.application.dto.SubmitWorkoutVerificationResult;
import momzzangseven.mztkbe.modules.verification.application.dto.TodayWorkoutCompletionResult;
import momzzangseven.mztkbe.modules.verification.application.dto.VerificationDetailResult;
import momzzangseven.mztkbe.modules.verification.application.port.in.GetTodayWorkoutCompletionUseCase;
import momzzangseven.mztkbe.modules.verification.application.port.in.GetVerificationDetailUseCase;
import momzzangseven.mztkbe.modules.verification.application.port.in.SubmitWorkoutPhotoVerificationUseCase;
import momzzangseven.mztkbe.modules.verification.application.port.in.SubmitWorkoutRecordVerificationUseCase;
import momzzangseven.mztkbe.modules.verification.domain.vo.CompletedMethod;
import momzzangseven.mztkbe.modules.verification.domain.vo.CompletionStatus;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@DisplayName("WorkoutVerificationController 컨트롤러 계약 테스트 (MockMvc + H2)")
@SpringBootTest
@AutoConfigureMockMvc
class WorkoutVerificationControllerTest {

  @org.springframework.beans.factory.annotation.Autowired
  protected org.springframework.test.web.servlet.MockMvc mockMvc;

  @org.springframework.beans.factory.annotation.Autowired
  protected com.fasterxml.jackson.databind.ObjectMapper objectMapper;

  @MockBean private SubmitWorkoutPhotoVerificationUseCase submitWorkoutPhotoVerificationUseCase;
  @MockBean private SubmitWorkoutRecordVerificationUseCase submitWorkoutRecordVerificationUseCase;
  @MockBean private GetVerificationDetailUseCase getVerificationDetailUseCase;
  @MockBean private GetTodayWorkoutCompletionUseCase getTodayWorkoutCompletionUseCase;

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

  @Test
  @DisplayName("POST /users/me/workout-photo-verifications 성공")
  void submitWorkoutPhoto_success() throws Exception {
    given(
            submitWorkoutPhotoVerificationUseCase.execute(
                new SubmitWorkoutVerificationCommand(
                    1L, "private/workout/photo.jpg", VerificationKind.WORKOUT_PHOTO)))
        .willReturn(
            SubmitWorkoutVerificationResult.builder()
                .verificationId("photo-1")
                .verificationKind(VerificationKind.WORKOUT_PHOTO)
                .verificationStatus(VerificationStatus.VERIFIED)
                .exerciseDate(LocalDate.of(2026, 3, 13))
                .completionStatus(CompletionStatus.COMPLETED)
                .grantedXp(100)
                .completedMethod(CompletedMethod.WORKOUT_PHOTO)
                .build());

    mockMvc
        .perform(
            post("/users/me/workout-photo-verifications")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(json(java.util.Map.of("tmpObjectKey", "private/workout/photo.jpg"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.verificationId").value("photo-1"))
        .andExpect(jsonPath("$.data.verificationKind").value("WORKOUT_PHOTO"))
        .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"))
        .andExpect(jsonPath("$.data.completedMethod").value("WORKOUT_PHOTO"));

    verify(submitWorkoutPhotoVerificationUseCase)
        .execute(
            new SubmitWorkoutVerificationCommand(
                1L, "private/workout/photo.jpg", VerificationKind.WORKOUT_PHOTO));
  }

  @Test
  @DisplayName("POST /users/me/workout-record-verifications 성공")
  void submitWorkoutRecord_success() throws Exception {
    given(
            submitWorkoutRecordVerificationUseCase.execute(
                new SubmitWorkoutVerificationCommand(
                    1L, "private/workout/record.png", VerificationKind.WORKOUT_RECORD)))
        .willReturn(
            SubmitWorkoutVerificationResult.builder()
                .verificationId("record-1")
                .verificationKind(VerificationKind.WORKOUT_RECORD)
                .verificationStatus(VerificationStatus.REJECTED)
                .completionStatus(CompletionStatus.NOT_COMPLETED)
                .completedMethod(null)
                .grantedXp(0)
                .build());

    mockMvc
        .perform(
            post("/users/me/workout-record-verifications")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(json(java.util.Map.of("tmpObjectKey", "private/workout/record.png"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.verificationId").value("record-1"))
        .andExpect(jsonPath("$.data.verificationKind").value("WORKOUT_RECORD"))
        .andExpect(jsonPath("$.data.verificationStatus").value("REJECTED"));

    verify(submitWorkoutRecordVerificationUseCase)
        .execute(
            new SubmitWorkoutVerificationCommand(
                1L, "private/workout/record.png", VerificationKind.WORKOUT_RECORD));
  }

  @Test
  @DisplayName("GET /users/me/verifications/{verificationId} 성공")
  void getVerificationDetail_success() throws Exception {
    given(getVerificationDetailUseCase.execute(1L, "verification-1"))
        .willReturn(
            VerificationDetailResult.builder()
                .verificationId("verification-1")
                .verificationKind(VerificationKind.WORKOUT_PHOTO)
                .verificationStatus(VerificationStatus.FAILED)
                .build());

    mockMvc
        .perform(get("/users/me/verifications/verification-1").with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.verificationId").value("verification-1"))
        .andExpect(jsonPath("$.data.verificationKind").value("WORKOUT_PHOTO"))
        .andExpect(jsonPath("$.data.verificationStatus").value("FAILED"));
  }

  @Test
  @DisplayName("GET /users/me/workout-completion/today 성공")
  void returnsTodayCompletion() throws Exception {
    given(getTodayWorkoutCompletionUseCase.execute(1L))
        .willReturn(
            TodayWorkoutCompletionResult.builder()
                .todayCompleted(true)
                .completedMethod(CompletedMethod.WORKOUT_PHOTO)
                .rewardGrantedToday(true)
                .grantedXp(100)
                .earnedDate(LocalDate.of(2026, 3, 13))
                .latestVerification(
                    LatestVerificationItem.builder()
                        .verificationId("verification-2")
                        .verificationKind(VerificationKind.WORKOUT_PHOTO)
                        .verificationStatus(VerificationStatus.VERIFIED)
                        .build())
                .build());

    mockMvc
        .perform(get("/users/me/workout-completion/today").with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.todayCompleted").value(true))
        .andExpect(jsonPath("$.data.completedMethod").value("WORKOUT_PHOTO"))
        .andExpect(jsonPath("$.data.latestVerification.verificationId").value("verification-2"));
  }

  @Test
  @DisplayName("submit 요청에서 tmpObjectKey가 blank면 400")
  void submitWorkoutPhoto_blankTmpObjectKey_returns400() throws Exception {
    mockMvc
        .perform(
            post("/users/me/workout-photo-verifications")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(json(java.util.Map.of("tmpObjectKey", ""))))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("submit 요청에 인증이 없으면 401")
  void submitWorkoutPhoto_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(
            post("/users/me/workout-photo-verifications")
                .contentType(APPLICATION_JSON)
                .content(json(java.util.Map.of("tmpObjectKey", "private/workout/photo.jpg"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("principal이 null이면 401")
  void getTodayCompletion_nullPrincipal_returns401() throws Exception {
    mockMvc
        .perform(get("/users/me/workout-completion/today").with(nullUserPrincipal()))
        .andExpect(status().isUnauthorized());
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor userPrincipal(
      Long userId) {
    return authenticatedPrincipal(userId, "ROLE_USER");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor nullUserPrincipal() {
    return nullPrincipalWithRoles("ROLE_USER");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor nullPrincipalWithRoles(
      String... authorities) {
    java.util.List<org.springframework.security.core.authority.SimpleGrantedAuthority>
        grantedAuthorities =
            Arrays.stream(authorities)
                .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                .toList();
    org.springframework.security.authentication.UsernamePasswordAuthenticationToken token =
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            null, null, grantedAuthorities);
    org.springframework.security.core.context.SecurityContext context =
        org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
    context.setAuthentication(token);
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.securityContext(context);
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor authenticatedPrincipal(
      Long userId, String... authorities) {
    java.util.Objects.requireNonNull(userId, "userId");
    java.util.List<org.springframework.security.core.authority.SimpleGrantedAuthority>
        grantedAuthorities =
            Arrays.stream(authorities)
                .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                .toList();
    org.springframework.security.authentication.UsernamePasswordAuthenticationToken token =
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            userId, null, grantedAuthorities);
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.authentication(token);
  }

  private String json(Object value) throws com.fasterxml.jackson.core.JsonProcessingException {
    return objectMapper.writeValueAsString(value);
  }
}
