package momzzangseven.mztkbe.modules.web3.wallet.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import momzzangseven.mztkbe.modules.account.application.service.ExternalDisconnectCleanupService;
import momzzangseven.mztkbe.modules.account.application.service.ExternalDisconnectRetryService;
import momzzangseven.mztkbe.modules.account.application.service.WithdrawalHardDeleteService;
import momzzangseven.mztkbe.modules.web3.execution.infrastructure.config.ExecutionEip7702Properties;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RegisterWalletCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RegisterWalletResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.UnlinkWalletCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.RegisterWalletUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.UnlinkWalletUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.service.WalletHardDeleteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("WalletController 컨트롤러 계약 테스트 (MockMvc + H2)")
@org.springframework.boot.test.context.SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "spring.main.lazy-initialization=true",
      "web3.eip7702.enabled=false",
      "web3.reward-token.enabled=false"
    })
class WalletControllerTest {

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

  @MockitoBean private ExecutionEip7702Properties executionEip7702Properties;
  @MockitoBean private ExternalDisconnectRetryService externalDisconnectRetryService;
  @MockitoBean private ExternalDisconnectCleanupService externalDisconnectCleanupService;
  @MockitoBean private WithdrawalHardDeleteService withdrawalHardDeleteService;
  @MockitoBean private WalletHardDeleteService walletHardDeleteService;

  @MockitoBean private RegisterWalletUseCase registerWalletUseCase;
  @MockitoBean private UnlinkWalletUseCase unlinkWalletUseCase;

  @Test
  @DisplayName("POST /web3/wallets 성공")
  void registerWallet_success() throws Exception {
    given(registerWalletUseCase.execute(any(RegisterWalletCommand.class)))
        .willReturn(
            new RegisterWalletResult(
                1L, "0x1111111111111111111111111111111111111111", Instant.now()));

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
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.id").value(1))
        .andExpect(
            jsonPath("$.data.walletAddress").value("0x1111111111111111111111111111111111111111"));

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
    mockMvc.perform(post("/web3/wallets")).andExpect(status().isUnauthorized());

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
