package momzzangseven.mztkbe.modules.web3.wallet.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.securityContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RegisterWalletCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RegisterWalletResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.UnlinkWalletCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionWriteView;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.RegisterWalletUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.UnlinkWalletUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("WalletController 컨트롤러 계약 테스트")
class WalletControllerTest {

  private static final LocalDateTime NOW = LocalDateTime.parse("2026-05-13T10:00:00");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

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

  @MockitoBean private RegisterWalletUseCase registerWalletUseCase;
  @MockitoBean private UnlinkWalletUseCase unlinkWalletUseCase;

  @Test
  @DisplayName("POST /web3/wallets 성공")
  void registerWallet_success() throws Exception {
    given(registerWalletUseCase.execute(any(RegisterWalletCommand.class)))
        .willReturn(RegisterWalletResult.pending(pendingSession(), web3View()));

    mockMvc
        .perform(
            post("/web3/wallets")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "walletAddress", "0x1111111111111111111111111111111111111111",
                            "signature",
                                "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                            "nonce", "nonce-1"))))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.registrationId").value("registration-1"))
        .andExpect(jsonPath("$.data.status").value("APPROVAL_REQUIRED"))
        .andExpect(jsonPath("$.data.walletId").doesNotExist())
        .andExpect(
            jsonPath("$.data.walletAddress").value("0x1111111111111111111111111111111111111111"))
        .andExpect(jsonPath("$.data.web3.executionIntent.id").value("intent-1"))
        .andExpect(jsonPath("$.data.web3.signRequest.authorization.chainId").value(10))
        .andExpect(
            jsonPath("$.data.web3.signRequest.submit.executionDigest")
                .value("0x" + "2".repeat(64)));

    verify(registerWalletUseCase).execute(any(RegisterWalletCommand.class));
  }

  @Test
  @DisplayName("POST /web3/wallets nonce 공백이면 400")
  void registerWallet_blankNonce_returns400() throws Exception {
    mockMvc
        .perform(
            post("/web3/wallets")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "walletAddress", "0x1111111111111111111111111111111111111111",
                            "signature", "sig",
                            "nonce", " "))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(registerWalletUseCase);
  }

  @Test
  @DisplayName("POST /web3/wallets walletAddress 공백이면 400")
  void registerWallet_blankWalletAddress_returns400() throws Exception {
    mockMvc
        .perform(
            post("/web3/wallets")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "walletAddress",
                            " ",
                            "signature",
                            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                                + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                            "nonce",
                            "nonce-1"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(registerWalletUseCase);
  }

  @Test
  @DisplayName("POST /web3/wallets signature 공백이면 400")
  void registerWallet_blankSignature_returns400() throws Exception {
    mockMvc
        .perform(
            post("/web3/wallets")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "walletAddress", "0x1111111111111111111111111111111111111111",
                            "signature", " ",
                            "nonce", "nonce-1"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(registerWalletUseCase);
  }

  @Test
  @DisplayName("POST /web3/wallets walletAddress 형식 오류면 400")
  void registerWallet_invalidWalletAddressFormat_returns400() throws Exception {
    given(registerWalletUseCase.execute(any(RegisterWalletCommand.class)))
        .willThrow(new IllegalArgumentException("Invalid Ethereum address format"));

    mockMvc
        .perform(
            post("/web3/wallets")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "walletAddress",
                            "0x1234",
                            "signature",
                            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                                + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                            "nonce",
                            "nonce-1"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));
  }

  @Test
  @DisplayName("POST /web3/wallets signature 형식 오류면 400")
  void registerWallet_invalidSignatureFormat_returns400() throws Exception {
    given(registerWalletUseCase.execute(any(RegisterWalletCommand.class)))
        .willThrow(new IllegalArgumentException("Invalid signature format"));

    mockMvc
        .perform(
            post("/web3/wallets")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "walletAddress", "0x1111111111111111111111111111111111111111",
                            "signature", "0x1234",
                            "nonce", "nonce-1"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));
  }

  @Test
  @DisplayName("POST /web3/wallets principal이 null이면 401")
  void registerWallet_nullPrincipal_returns401() throws Exception {
    mockMvc
        .perform(
            post("/web3/wallets")
                .with(nullUserPrincipal())
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "walletAddress",
                            "0x1111111111111111111111111111111111111111",
                            "signature",
                            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                                + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                            "nonce",
                            "nonce-1"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value("FAIL"))
        .andExpect(jsonPath("$.code").value("AUTH_006"));

    verifyNoInteractions(registerWalletUseCase);
  }

  @Test
  @DisplayName("POST /web3/wallets 인증 없으면 401")
  void registerWallet_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(
            post("/web3/wallets")
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "walletAddress", "0x1111111111111111111111111111111111111111",
                            "signature",
                                "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                            "nonce", "nonce-1"))))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(registerWalletUseCase);
  }

  @Test
  @DisplayName("DELETE /web3/wallets/{walletAddress} 성공")
  void unlinkWallet_success() throws Exception {
    mockMvc
        .perform(
            delete("/web3/wallets/0x1111111111111111111111111111111111111111")
                .with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"));

    verify(unlinkWalletUseCase).execute(any(UnlinkWalletCommand.class));
  }

  @Test
  @DisplayName("DELETE /web3/wallets/{walletAddress} principal이 null이면 401")
  void unlinkWallet_nullPrincipal_returns401() throws Exception {
    mockMvc
        .perform(
            delete("/web3/wallets/0x1111111111111111111111111111111111111111")
                .with(nullUserPrincipal()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value("FAIL"))
        .andExpect(jsonPath("$.code").value("AUTH_006"));

    verifyNoInteractions(unlinkWalletUseCase);
  }

  @Test
  @DisplayName("DELETE /web3/wallets/{walletAddress} 주소 형식이 잘못되면 400")
  void unlinkWallet_invalidAddress_returns400() throws Exception {
    doThrow(new IllegalArgumentException("Invalid Ethereum address format"))
        .when(unlinkWalletUseCase)
        .execute(any(UnlinkWalletCommand.class));

    mockMvc
        .perform(delete("/web3/wallets/not-an-address").with(userPrincipal(1L)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));
  }

  @Test
  @DisplayName("DELETE /web3/wallets/{walletAddress} 인증 없으면 401")
  void unlinkWallet_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(delete("/web3/wallets/0x1111111111111111111111111111111111111111"))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(unlinkWalletUseCase);
  }

  private RequestPostProcessor userPrincipal(Long userId) {
    return authentication(
        new UsernamePasswordAuthenticationToken(
            userId,
            null,
            List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_STEP_UP"))));
  }

  private RequestPostProcessor nullUserPrincipal() {
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        new UsernamePasswordAuthenticationToken(
            null,
            null,
            List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_STEP_UP"))));
    return securityContext(context);
  }

  private String json(Object value) throws JsonProcessingException {
    return objectMapper.writeValueAsString(value);
  }

  private static WalletRegistrationSession pendingSession() {
    return WalletRegistrationSession.create(
            "registration-1",
            1L,
            "0x1111111111111111111111111111111111111111",
            "nonce-1",
            NOW.plusMinutes(30),
            NOW)
        .attachApprovalIntent("intent-1", NOW.plusMinutes(30), NOW.plusSeconds(1));
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
                10L, "0x" + "d".repeat(40), 7L, "0x" + "1".repeat(64)),
            new WalletApprovalExecutionWriteView.Submit("0x" + "2".repeat(64), 123L),
            null),
        null,
        false);
  }
}
