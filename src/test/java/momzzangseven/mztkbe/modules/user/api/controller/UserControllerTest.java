package momzzangseven.mztkbe.modules.user.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Map;
import momzzangseven.mztkbe.global.error.UserNotFoundException;
import momzzangseven.mztkbe.modules.user.application.dto.GetMyProfileResult;
import momzzangseven.mztkbe.modules.user.application.dto.UpdateUserRoleCommand;
import momzzangseven.mztkbe.modules.user.application.dto.UpdateUserRoleResult;
import momzzangseven.mztkbe.modules.user.application.port.in.GetMyProfileUseCase;
import momzzangseven.mztkbe.modules.user.application.port.in.UpdateUserRoleUseCase;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import momzzangseven.mztkbe.modules.user.domain.vo.WorkoutCompletedMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("UserController 컨트롤러 계약 테스트 (MockMvc + H2)")
@org.springframework.boot.test.context.SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class UserControllerTest {

  @org.springframework.beans.factory.annotation.Autowired
  protected org.springframework.test.web.servlet.MockMvc mockMvc;

  @org.springframework.beans.factory.annotation.Autowired
  protected com.fasterxml.jackson.databind.ObjectMapper objectMapper;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.application.port.in
          .MarkTransactionSucceededUseCase
      txMarkTransactionSucceededUseCase;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionReceiptWorker
      txTransactionReceiptWorker;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionIssuerWorker
      txTransactionIssuerWorker;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .SignedRecoveryWorker
      txSignedRecoveryWorker;

  @MockitoBean private GetMyProfileUseCase getMyProfileUseCase;
  @MockitoBean private UpdateUserRoleUseCase updateUserRoleUseCase;

  @Test
  @DisplayName("PATCH /users/me/role 성공")
  void updateRole_success() throws Exception {
    given(updateUserRoleUseCase.execute(any(UpdateUserRoleCommand.class)))
        .willReturn(
            UpdateUserRoleResult.builder()
                .id(1L)
                .email("user@example.com")
                .name("tester")
                .nickname("닉네임")
                .profileImageUrl("https://example.com/profile.png")
                .role(UserRole.TRAINER)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

    mockMvc
        .perform(
            patch("/users/me/role")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("role", "TRAINER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.id").value(1))
        .andExpect(jsonPath("$.data.role").value("TRAINER"));
  }

  @Test
  @DisplayName("PATCH /users/me/role role 누락이면 400")
  void updateRole_missingRole_returns400() throws Exception {
    mockMvc
        .perform(
            patch("/users/me/role")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of())))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("PATCH /users/me/role 인증 없으면 401")
  void updateRole_unauthenticated_returns401() throws Exception {
    mockMvc.perform(patch("/users/me/role")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("PATCH /users/me/role 인증 principal이 null이면 401")
  void updateRole_nullPrincipal_returns401() throws Exception {
    mockMvc
        .perform(
            patch("/users/me/role")
                .with(nullUserPrincipal())
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("role", "TRAINER"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("PATCH /users/me/role 잘못된 enum 값이면 400")
  void updateRole_invalidEnum_returns400() throws Exception {
    mockMvc
        .perform(
            patch("/users/me/role")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content("{\"role\":\"INVALID\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));
  }

  @Test
  @DisplayName("[M-6] GET /users/me 인증된 유저 조회 성공 시 200과 올바른 JSON 구조를 반환한다")
  void getMyProfile_authenticatedUser_returns200WithCorrectJsonStructure() throws Exception {
    GetMyProfileResult mockResult =
        new GetMyProfileResult(
            "테스터",
            "test@example.com",
            "LOCAL",
            UserRole.USER,
            null,
            1,
            0,
            300,
            false,
            true,
            WorkoutCompletedMethod.WORKOUT_PHOTO,
            0);
    given(getMyProfileUseCase.execute(1L)).willReturn(mockResult);

    mockMvc
        .perform(get("/users/me").with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.nickname").value("테스터"))
        .andExpect(jsonPath("$.data.email").value("test@example.com"))
        .andExpect(jsonPath("$.data.provider").value("LOCAL"))
        .andExpect(jsonPath("$.data.role").value("USER"))
        .andExpect(jsonPath("$.data.level").isNumber())
        .andExpect(jsonPath("$.data.currentXp").isNumber())
        .andExpect(jsonPath("$.data.requiredXpForNextLevel").isNumber())
        .andExpect(jsonPath("$.data.hasAttendedToday").isBoolean())
        .andExpect(jsonPath("$.data.hasCompletedWorkoutToday").isBoolean())
        .andExpect(jsonPath("$.data.completedWorkoutMethod").value("WORKOUT_PHOTO"))
        .andExpect(jsonPath("$.data.weeklyAttendanceCount").isNumber());
  }

  @Test
  @DisplayName("[M-7] GET /users/me 인증 없이 요청 시 401을 반환한다")
  void getMyProfile_unauthenticated_returns401() throws Exception {
    mockMvc.perform(get("/users/me")).andExpect(status().isUnauthorized());

    verifyNoInteractions(getMyProfileUseCase);
  }

  @Test
  @DisplayName("[M-8] GET /users/me principal이 null이면 401을 반환한다")
  void getMyProfile_nullPrincipal_returns401() throws Exception {
    mockMvc
        .perform(get("/users/me").with(nullUserPrincipal()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value("FAIL"))
        .andExpect(jsonPath("$.code").value("AUTH_006"));

    verifyNoInteractions(getMyProfileUseCase);
  }

  @Test
  @DisplayName("[M-9] GET /users/me 유저가 존재하지 않으면 401을 반환한다 (USER_NOT_FOUND → HTTP 401)")
  void getMyProfile_userNotFound_returns401() throws Exception {
    given(getMyProfileUseCase.execute(1L)).willThrow(new UserNotFoundException(1L));

    mockMvc.perform(get("/users/me").with(userPrincipal(1L))).andExpect(status().isUnauthorized());
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor userPrincipal(
      Long userId) {
    return authenticatedPrincipal(userId, "ROLE_USER");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor adminPrincipal(
      Long userId) {
    return authenticatedPrincipal(userId, "ROLE_ADMIN");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor nullUserPrincipal() {
    return nullPrincipalWithRoles("ROLE_USER");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor nullAdminPrincipal() {
    return nullPrincipalWithRoles("ROLE_ADMIN");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor nullPrincipalWithRoles(
      String... authorities) {
    java.util.List<org.springframework.security.core.authority.SimpleGrantedAuthority>
        grantedAuthorities =
            java.util.Arrays.stream(authorities)
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
            java.util.Arrays.stream(authorities)
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
