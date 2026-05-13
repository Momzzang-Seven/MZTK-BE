package momzzangseven.mztkbe.modules.web3.wallet.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.global.error.wallet.WalletNotFoundException;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.GetWalletRegistrationStatusQuery;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RetryWalletRegistrationApprovalCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionWriteView;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationNextAction;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationStatusResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.GetWalletRegistrationStatusUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.RetryWalletRegistrationApprovalUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WalletRegistrationControllerTest {

  private static final LocalDateTime NOW = LocalDateTime.parse("2026-05-13T10:00:00");

  @Autowired private MockMvc mockMvc;

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

  @MockitoBean private GetWalletRegistrationStatusUseCase getStatusUseCase;
  @MockitoBean private RetryWalletRegistrationApprovalUseCase retryApprovalUseCase;

  @Test
  void getStatus_success() throws Exception {
    given(getStatusUseCase.execute(any(GetWalletRegistrationStatusQuery.class)))
        .willReturn(result(web3View()));

    mockMvc
        .perform(get("/web3/wallet-registrations/registration-1").with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.registrationId").value("registration-1"))
        .andExpect(jsonPath("$.data.status").value("APPROVAL_REQUIRED"))
        .andExpect(jsonPath("$.data.nextAction").value("SIGN_APPROVAL"))
        .andExpect(jsonPath("$.data.web3.executionIntent.id").value("intent-1"));

    verify(getStatusUseCase).execute(any(GetWalletRegistrationStatusQuery.class));
  }

  @Test
  void getStatus_whenSignRequestUnavailable_exposesReason() throws Exception {
    given(getStatusUseCase.execute(any(GetWalletRegistrationStatusQuery.class)))
        .willReturn(deadlineTooCloseResult());

    mockMvc
        .perform(get("/web3/wallet-registrations/registration-1").with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.nextAction").value("RETRY_APPROVAL"))
        .andExpect(
            jsonPath("$.data.signRequestUnavailableReason").value("EIP7702_DEADLINE_TOO_CLOSE"))
        .andExpect(jsonPath("$.data.web3.signRequest").doesNotExist())
        .andExpect(
            jsonPath("$.data.web3.signRequestUnavailableReason")
                .value("EIP7702_DEADLINE_TOO_CLOSE"));
  }

  @Test
  void getStatus_wrongUser_returns404WithoutRetryInteraction() throws Exception {
    given(getStatusUseCase.execute(any(GetWalletRegistrationStatusQuery.class)))
        .willThrow(new WalletNotFoundException());

    mockMvc
        .perform(get("/web3/wallet-registrations/registration-1").with(userPrincipal(2L)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value("FAIL"))
        .andExpect(jsonPath("$.code").value("WALLET_004"));

    verifyNoInteractions(retryApprovalUseCase);
  }

  @Test
  void retryApproval_success() throws Exception {
    given(retryApprovalUseCase.execute(any(RetryWalletRegistrationApprovalCommand.class)))
        .willReturn(result(web3View()));

    mockMvc
        .perform(
            post("/web3/wallet-registrations/registration-1/approval-intent")
                .with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.web3.signRequest.authorization.chainId").value(10));

    verify(retryApprovalUseCase).execute(any(RetryWalletRegistrationApprovalCommand.class));
  }

  @Test
  void getStatus_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(get("/web3/wallet-registrations/registration-1"))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(getStatusUseCase, retryApprovalUseCase);
  }

  private RequestPostProcessor userPrincipal(Long userId) {
    return authentication(
        new UsernamePasswordAuthenticationToken(
            userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
  }

  private static WalletRegistrationStatusResult result(WalletApprovalExecutionWriteView web3) {
    return new WalletRegistrationStatusResult(
        "registration-1",
        WalletRegistrationStatus.APPROVAL_REQUIRED,
        "0x" + "a".repeat(40),
        null,
        "intent-1",
        "AWAITING_SIGNATURE",
        NOW.plusMinutes(30),
        null,
        null,
        null,
        null,
        WalletRegistrationNextAction.SIGN_APPROVAL,
        web3);
  }

  private static WalletRegistrationStatusResult deadlineTooCloseResult() {
    return new WalletRegistrationStatusResult(
        "registration-1",
        WalletRegistrationStatus.APPROVAL_REQUIRED,
        "0x" + "a".repeat(40),
        null,
        "intent-1",
        "AWAITING_SIGNATURE",
        NOW.plusMinutes(30),
        null,
        null,
        null,
        "EIP7702_DEADLINE_TOO_CLOSE",
        WalletRegistrationNextAction.RETRY_APPROVAL,
        deadlineTooCloseWeb3View());
  }

  private static WalletApprovalExecutionWriteView web3View() {
    return new WalletApprovalExecutionWriteView(
        new WalletApprovalExecutionWriteView.Resource(
            "WALLET_REGISTRATION", "registration-1", "PENDING_EXECUTION"),
        "WALLET_ESCROW_APPROVE",
        new WalletApprovalExecutionWriteView.ExecutionIntent(
            "intent-1", "AWAITING_SIGNATURE", NOW.plusMinutes(5), 1L),
        new WalletApprovalExecutionWriteView.Execution("EIP7702", 2),
        new WalletApprovalExecutionWriteView.SignRequest(
            new WalletApprovalExecutionWriteView.Authorization(
                10L, "0x" + "b".repeat(40), 7L, "0x" + "c".repeat(64)),
            new WalletApprovalExecutionWriteView.Submit("0x" + "d".repeat(64), 123L),
            null),
        null,
        true);
  }

  private static WalletApprovalExecutionWriteView deadlineTooCloseWeb3View() {
    return new WalletApprovalExecutionWriteView(
        new WalletApprovalExecutionWriteView.Resource(
            "WALLET_REGISTRATION", "registration-1", "PENDING_EXECUTION"),
        "WALLET_ESCROW_APPROVE",
        new WalletApprovalExecutionWriteView.ExecutionIntent(
            "intent-1", "AWAITING_SIGNATURE", NOW.plusMinutes(5), 1L),
        new WalletApprovalExecutionWriteView.Execution("EIP7702", 2),
        null,
        "EIP7702_DEADLINE_TOO_CLOSE",
        true);
  }
}
