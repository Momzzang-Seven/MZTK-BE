package momzzangseven.mztkbe.modules.web3.admin.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ReplayWalletRegistrationApprovalCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ReplayWalletRegistrationApprovalResult;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.ReplayWalletRegistrationApprovalUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@TestPropertySource(properties = "web3.reward-token.enabled=true")
@DisplayName("WalletRegistrationRecoveryController 컨트롤러 계약 테스트 (MockMvc + H2)")
@SpringBootTest
@AutoConfigureMockMvc
class WalletRegistrationRecoveryControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private ReplayWalletRegistrationApprovalUseCase replayUseCase;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.application.port.in
          .MarkTransactionSucceededUseCase
      txMarkTransactionSucceededUseCase;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionReceiptWorker
      txTransactionReceiptWorker;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionIssuerWorker
      txTransactionIssuerWorker;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .SignedRecoveryWorker
      txSignedRecoveryWorker;

  @Test
  @DisplayName("POST /admin/web3/wallet-registrations/replay-confirmed-approval 성공")
  void replayConfirmedApproval_success() throws Exception {
    given(replayUseCase.execute(any(ReplayWalletRegistrationApprovalCommand.class)))
        .willReturn(
            new ReplayWalletRegistrationApprovalResult(
                "REGISTERED",
                true,
                "registration-1",
                24L,
                "0x" + "a".repeat(64),
                "intent-1",
                "CONFIRMED",
                "SUCCEEDED",
                "REGISTERED",
                false,
                null,
                null));

    mockMvc
        .perform(
            post("/admin/web3/wallet-registrations/replay-confirmed-approval")
                .with(adminPrincipal(9L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "transactionId", 24L,
                            "reason", "manual-confirmation",
                            "evidence", "ops-ticket-450"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.outcome").value("REGISTERED"))
        .andExpect(jsonPath("$.data.replayInvoked").value(true))
        .andExpect(jsonPath("$.data.registrationId").value("registration-1"))
        .andExpect(jsonPath("$.data.transactionId").value(24))
        .andExpect(jsonPath("$.data.txHash").value("0x" + "a".repeat(64)))
        .andExpect(jsonPath("$.data.executionIntentId").value("intent-1"))
        .andExpect(jsonPath("$.data.walletRegistrationStatus").value("REGISTERED"))
        .andExpect(jsonPath("$.data.newerWalletRegistrationExists").value(false));

    ArgumentCaptor<ReplayWalletRegistrationApprovalCommand> captor =
        ArgumentCaptor.forClass(ReplayWalletRegistrationApprovalCommand.class);
    verify(replayUseCase).execute(captor.capture());
    ReplayWalletRegistrationApprovalCommand command = captor.getValue();
    org.assertj.core.api.Assertions.assertThat(command.operatorId()).isEqualTo(9L);
    org.assertj.core.api.Assertions.assertThat(command.transactionId()).isEqualTo(24L);
    org.assertj.core.api.Assertions.assertThat(command.reason()).isEqualTo("manual-confirmation");
    org.assertj.core.api.Assertions.assertThat(command.evidence()).isEqualTo("ops-ticket-450");
  }

  @Test
  @DisplayName("POST /admin/web3/wallet-registrations/replay-confirmed-approval USER 권한이면 403")
  void replayConfirmedApproval_forbiddenForUser_returns403() throws Exception {
    mockMvc
        .perform(
            post("/admin/web3/wallet-registrations/replay-confirmed-approval")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "transactionId", 24L,
                            "reason", "manual-confirmation",
                            "evidence", "ops-ticket-450"))))
        .andExpect(status().isForbidden());

    verifyNoInteractions(replayUseCase);
  }

  @Test
  @DisplayName("POST /admin/web3/wallet-registrations/replay-confirmed-approval 인증 없으면 401")
  void replayConfirmedApproval_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(post("/admin/web3/wallet-registrations/replay-confirmed-approval"))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(replayUseCase);
  }

  @Test
  @DisplayName("POST /admin/web3/wallet-registrations/replay-confirmed-approval reason 공백이면 400")
  void replayConfirmedApproval_blankReason_returns400() throws Exception {
    mockMvc
        .perform(
            post("/admin/web3/wallet-registrations/replay-confirmed-approval")
                .with(adminPrincipal(9L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "transactionId", 24L,
                            "reason", " ",
                            "evidence", "ops-ticket-450"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(replayUseCase);
  }

  @Test
  @DisplayName(
      "POST /admin/web3/wallet-registrations/replay-confirmed-approval evidence 과대 payload면 400")
  void replayConfirmedApproval_oversizedEvidence_returns400() throws Exception {
    mockMvc
        .perform(
            post("/admin/web3/wallet-registrations/replay-confirmed-approval")
                .with(adminPrincipal(9L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "transactionId",
                            24L,
                            "reason",
                            "manual-confirmation",
                            "evidence",
                            "x".repeat(1001)))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(replayUseCase);
  }

  @Test
  @DisplayName(
      "POST /admin/web3/wallet-registrations/replay-confirmed-approval principal이 null이면 401")
  void replayConfirmedApproval_nullPrincipal_returns401() throws Exception {
    mockMvc
        .perform(
            post("/admin/web3/wallet-registrations/replay-confirmed-approval")
                .with(nullAdminPrincipal())
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "transactionId", 24L,
                            "reason", "manual-confirmation",
                            "evidence", "ops-ticket-450"))))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(replayUseCase);
  }

  private RequestPostProcessor userPrincipal(Long userId) {
    return authenticatedPrincipal(userId, "ROLE_USER");
  }

  private RequestPostProcessor adminPrincipal(Long userId) {
    return authenticatedPrincipal(userId, "ROLE_ADMIN");
  }

  private RequestPostProcessor nullAdminPrincipal() {
    List<SimpleGrantedAuthority> grantedAuthorities =
        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
    UsernamePasswordAuthenticationToken token =
        new UsernamePasswordAuthenticationToken(null, null, grantedAuthorities);
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(token);
    return SecurityMockMvcRequestPostProcessors.securityContext(context);
  }

  private RequestPostProcessor authenticatedPrincipal(Long userId, String... authorities) {
    java.util.Objects.requireNonNull(userId, "userId");
    List<SimpleGrantedAuthority> grantedAuthorities =
        Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
    UsernamePasswordAuthenticationToken token =
        new UsernamePasswordAuthenticationToken(userId, null, grantedAuthorities);
    return SecurityMockMvcRequestPostProcessors.authentication(token);
  }

  private String json(Object value) throws JsonProcessingException {
    return objectMapper.writeValueAsString(value);
  }
}
