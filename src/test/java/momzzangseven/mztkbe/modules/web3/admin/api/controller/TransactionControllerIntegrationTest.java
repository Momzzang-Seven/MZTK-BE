package momzzangseven.mztkbe.modules.web3.admin.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarkTransactionSucceededCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarkTransactionSucceededResult;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.MarkTransactionSucceededUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "web3.reward-token.enabled=true")
@DisplayName("TransactionController 통합 테스트 (MockMvc + H2)")
@org.springframework.boot.test.context.SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@org.springframework.transaction.annotation.Transactional
class TransactionControllerIntegrationTest {

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

  @MockBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  @Test
  @DisplayName("POST /admin/web3/transactions/{txId}/mark-succeeded 성공")
  void markSucceeded_success() throws Exception {
    given(markTransactionSucceededUseCase.execute(any(MarkTransactionSucceededCommand.class)))
        .willReturn(
            new MarkTransactionSucceededResult(
                1L, "SUCCEEDED", "PENDING", "0xabc123", "https://explorer.example/tx/0xabc123"));

    mockMvc
        .perform(
            post("/admin/web3/transactions/1/mark-succeeded")
                .with(adminPrincipal(9L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "txHash", "0xabc123",
                            "explorerUrl", "https://explorer.example/tx/0xabc123",
                            "reason", "manual-confirmation",
                            "evidence", "ops-ticket-1234"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.transactionId").value(1))
        .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));

    verify(markTransactionSucceededUseCase).execute(any(MarkTransactionSucceededCommand.class));
  }

  @Test
  @DisplayName("POST /admin/web3/transactions/{txId}/mark-succeeded USER 권한이면 403")
  void markSucceeded_forbiddenForUser_returns403() throws Exception {
    mockMvc
        .perform(
            post("/admin/web3/transactions/1/mark-succeeded")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "txHash", "0xabc123",
                            "explorerUrl", "https://explorer.example/tx/0xabc123",
                            "reason", "manual-confirmation",
                            "evidence", "ops-ticket-1234"))))
        .andExpect(status().isForbidden());

    verifyNoInteractions(markTransactionSucceededUseCase);
  }

  @Test
  @DisplayName("POST /admin/web3/transactions/{txId}/mark-succeeded 인증 없으면 401")
  void markSucceeded_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(post("/admin/web3/transactions/1/mark-succeeded"))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(markTransactionSucceededUseCase);
  }

  @Test
  @DisplayName("POST /admin/web3/transactions/{txId}/mark-succeeded 필수 값 누락이면 400")
  void markSucceeded_blankTxHash_returns400() throws Exception {
    mockMvc
        .perform(
            post("/admin/web3/transactions/1/mark-succeeded")
                .with(adminPrincipal(9L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "txHash", " ",
                            "explorerUrl", "https://explorer.example/tx/0xabc123",
                            "reason", "manual-confirmation",
                            "evidence", "ops-ticket-1234"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(markTransactionSucceededUseCase);
  }

  @Test
  @DisplayName("POST /admin/web3/transactions/{txId}/mark-succeeded explorerUrl 공백이면 400")
  void markSucceeded_blankExplorerUrl_returns400() throws Exception {
    mockMvc
        .perform(
            post("/admin/web3/transactions/1/mark-succeeded")
                .with(adminPrincipal(9L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "txHash", "0xabc123",
                            "explorerUrl", " ",
                            "reason", "manual-confirmation",
                            "evidence", "ops-ticket-1234"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(markTransactionSucceededUseCase);
  }

  @Test
  @DisplayName("POST /admin/web3/transactions/{txId}/mark-succeeded reason 공백이면 400")
  void markSucceeded_blankReason_returns400() throws Exception {
    mockMvc
        .perform(
            post("/admin/web3/transactions/1/mark-succeeded")
                .with(adminPrincipal(9L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "txHash", "0xabc123",
                            "explorerUrl", "https://explorer.example/tx/0xabc123",
                            "reason", " ",
                            "evidence", "ops-ticket-1234"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(markTransactionSucceededUseCase);
  }

  @Test
  @DisplayName("POST /admin/web3/transactions/{txId}/mark-succeeded evidence 공백이면 400")
  void markSucceeded_blankEvidence_returns400() throws Exception {
    mockMvc
        .perform(
            post("/admin/web3/transactions/1/mark-succeeded")
                .with(adminPrincipal(9L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "txHash", "0xabc123",
                            "explorerUrl", "https://explorer.example/tx/0xabc123",
                            "reason", "manual-confirmation",
                            "evidence", " "))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(markTransactionSucceededUseCase);
  }

  @Test
  @DisplayName("POST /admin/web3/transactions/{txId}/mark-succeeded principal이 null이면 401")
  void markSucceeded_nullPrincipal_returns401() throws Exception {
    mockMvc
        .perform(
            post("/admin/web3/transactions/1/mark-succeeded")
                .with(nullAdminPrincipal())
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "txHash", "0xabc123",
                            "explorerUrl", "https://explorer.example/tx/0xabc123",
                            "reason", "manual-confirmation",
                            "evidence", "ops-ticket-1234"))))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(markTransactionSucceededUseCase);
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
