package momzzangseven.mztkbe.modules.user.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.Map;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import momzzangseven.mztkbe.modules.user.domain.model.UserStatus;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.entity.UserEntity;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.repository.UserJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@DisplayName("UserController 실경로 통합 테스트 (MockMvc + H2)")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerIntegrationTest {

  @org.springframework.beans.factory.annotation.Autowired protected MockMvc mockMvc;

  @org.springframework.beans.factory.annotation.Autowired
  protected com.fasterxml.jackson.databind.ObjectMapper objectMapper;

  @org.springframework.beans.factory.annotation.Autowired
  protected UserJpaRepository userJpaRepository;

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
  @DisplayName("PATCH /users/me/role 요청이 실제 DB role 변경으로 반영된다")
  void updateRole_realFlow_updatesRoleInH2() throws Exception {
    UserEntity saved =
        userJpaRepository.save(
            UserEntity.builder()
                .provider(AuthProvider.LOCAL)
                .providerUserId("local-realflow-user-1")
                .email("realflow-user-1@example.com")
                .role(UserRole.USER)
                .nickname("realflow")
                .status(UserStatus.ACTIVE)
                .build());

    mockMvc
        .perform(
            patch("/users/me/role")
                .with(userPrincipal(saved.getId()))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("role", "TRAINER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.id").value(saved.getId()))
        .andExpect(jsonPath("$.data.role").value("TRAINER"));

    UserEntity updated = userJpaRepository.findById(saved.getId()).orElseThrow();
    assertThat(updated.getRole()).isEqualTo(UserRole.TRAINER);
  }

  @Test
  @DisplayName("POST /users/me/withdrawal 요청이 실제 DB soft-delete로 반영된다")
  void withdraw_realFlow_marksUserAsDeletedInH2() throws Exception {
    UserEntity saved =
        userJpaRepository.save(
            UserEntity.builder()
                .provider(AuthProvider.LOCAL)
                .providerUserId("local-realflow-user-2")
                .email("realflow-user-2@example.com")
                .role(UserRole.USER)
                .nickname("realflow2")
                .status(UserStatus.ACTIVE)
                .build());

    mockMvc
        .perform(post("/users/me/withdrawal").with(stepUpPrincipal(saved.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"));

    UserEntity withdrawn = userJpaRepository.findById(saved.getId()).orElseThrow();
    assertThat(withdrawn.getStatus()).isEqualTo(UserStatus.DELETED);
    assertThat(withdrawn.getDeletedAt()).isNotNull();
  }

  private RequestPostProcessor userPrincipal(Long userId) {
    return authenticatedPrincipal(userId, "ROLE_USER");
  }

  private RequestPostProcessor stepUpPrincipal(Long userId) {
    return authenticatedPrincipal(userId, "ROLE_USER", "ROLE_STEP_UP");
  }

  private RequestPostProcessor authenticatedPrincipal(Long userId, String... authorities) {
    java.util.Objects.requireNonNull(userId, "userId");
    java.util.List<SimpleGrantedAuthority> grantedAuthorities =
        Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
    UsernamePasswordAuthenticationToken token =
        new UsernamePasswordAuthenticationToken(userId, null, grantedAuthorities);
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(token);
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.securityContext(context);
  }

  private String json(Object value) throws com.fasterxml.jackson.core.JsonProcessingException {
    return objectMapper.writeValueAsString(value);
  }
}
