package momzzangseven.mztkbe.modules.user.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Map;
import momzzangseven.mztkbe.modules.user.application.dto.UpdateUserRoleCommand;
import momzzangseven.mztkbe.modules.user.application.dto.UpdateUserRoleResult;
import momzzangseven.mztkbe.modules.user.application.port.in.UpdateUserRoleUseCase;
import momzzangseven.mztkbe.modules.user.application.port.in.WithdrawUserUseCase;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

@DisplayName("UserController 통합 테스트 (MockMvc + H2)")
@org.springframework.boot.test.context.SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@org.springframework.transaction.annotation.Transactional
class UserControllerIntegrationTest {

  @org.springframework.beans.factory.annotation.Autowired
  protected org.springframework.test.web.servlet.MockMvc mockMvc;

  @org.springframework.beans.factory.annotation.Autowired
  protected com.fasterxml.jackson.databind.ObjectMapper objectMapper;

  @org.springframework.boot.test.mock.mockito.MockBean
  private momzzangseven.mztkbe.modules.web3.transaction.application.port.in
          .MarkTransactionSucceededUseCase
      txMarkTransactionSucceededUseCase;

  @org.springframework.boot.test.mock.mockito.MockBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionReceiptWorker
      txTransactionReceiptWorker;

  @org.springframework.boot.test.mock.mockito.MockBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionIssuerWorker
      txTransactionIssuerWorker;

  @org.springframework.boot.test.mock.mockito.MockBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .SignedRecoveryWorker
      txSignedRecoveryWorker;

  @MockBean private UpdateUserRoleUseCase updateUserRoleUseCase;
  @MockBean private WithdrawUserUseCase withdrawUserUseCase;

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
  @DisplayName("POST /users/me/withdrawal ROLE_STEP_UP 권한이면 성공")
  void withdraw_stepUpRole_success() throws Exception {
    mockMvc
        .perform(post("/users/me/withdrawal").with(stepUpPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"));
  }

  @Test
  @DisplayName("POST /users/me/withdrawal ROLE_STEP_UP 없으면 403")
  void withdraw_withoutStepUpRole_returns403() throws Exception {
    mockMvc
        .perform(post("/users/me/withdrawal").with(userPrincipal(1L)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("POST /users/me/withdrawal 인증 없으면 401")
  void withdraw_unauthenticated_returns401() throws Exception {
    mockMvc.perform(post("/users/me/withdrawal")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("POST /users/me/withdrawal principal이 null이면 401")
  void withdraw_nullPrincipal_returns401() throws Exception {
    mockMvc
        .perform(post("/users/me/withdrawal").with(nullStepUpPrincipal()))
        .andExpect(status().isUnauthorized());
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor userPrincipal(
      Long userId) {
    return authenticatedPrincipal(userId, "ROLE_USER");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor adminPrincipal(
      Long userId) {
    return authenticatedPrincipal(userId, "ROLE_ADMIN");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor stepUpPrincipal(
      Long userId) {
    return authenticatedPrincipal(userId, "ROLE_USER", "ROLE_STEP_UP");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor nullUserPrincipal() {
    return nullPrincipalWithRoles("ROLE_USER");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor nullAdminPrincipal() {
    return nullPrincipalWithRoles("ROLE_ADMIN");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor nullStepUpPrincipal() {
    return nullPrincipalWithRoles("ROLE_USER", "ROLE_STEP_UP");
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
