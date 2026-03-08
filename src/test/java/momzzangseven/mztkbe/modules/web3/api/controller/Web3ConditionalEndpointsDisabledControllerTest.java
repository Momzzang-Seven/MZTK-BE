package momzzangseven.mztkbe.modules.web3.api.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@TestPropertySource(
    properties = {
      "web3.reward-token.enabled=false",
      "web3.reward-token.treasury.provisioning.enabled=false",
      "web3.eip7702.enabled=false"
    })
@DisplayName("Web3 조건부 엔드포인트 비활성화 컨트롤러 계약 테스트 (MockMvc + H2)")
@SpringBootTest
@AutoConfigureMockMvc
class Web3ConditionalEndpointsDisabledControllerTest {

  @org.springframework.beans.factory.annotation.Autowired protected MockMvc mockMvc;

  @org.springframework.beans.factory.annotation.Autowired
  protected com.fasterxml.jackson.databind.ObjectMapper objectMapper;

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
  @DisplayName("reward-token 비활성화 시 transactions endpoint는 404")
  void transactionEndpoint_disabled_returns404() throws Exception {
    mockMvc
        .perform(
            post("/admin/web3/transactions/1/mark-succeeded")
                .with(adminPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "txHash",
                            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                            "explorerUrl",
                            "https://etherscan.io/tx/0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                            "reason",
                            "manual",
                            "evidence",
                            "ops"))))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("treasury provisioning 비활성화 시 treasury endpoint는 404")
  void treasuryEndpoint_disabled_returns404() throws Exception {
    mockMvc
        .perform(
            post("/admin/web3/treasury-keys/provision")
                .with(adminPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "treasuryPrivateKey",
                            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("reward-token 비활성화 시 token-transfers endpoint는 404")
  void tokenTransferEndpoint_disabled_returns404() throws Exception {
    mockMvc
        .perform(
            post("/users/me/token-transfers/prepare")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "domainType",
                            "QUESTION_REWARD",
                            "referenceId",
                            "77",
                            "toUserId",
                            2,
                            "amountWei",
                            "1000000000000000000"))))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("reward-token 비활성화 시 token-transfers submit endpoint는 404")
  void tokenTransferSubmitEndpoint_disabled_returns404() throws Exception {
    mockMvc
        .perform(
            post("/users/me/token-transfers/submit")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "prepareId",
                            "123e4567-e89b-12d3-a456-426614174000",
                            "authorizationSignature",
                            "0x" + "a".repeat(130),
                            "executionSignature",
                            "0x" + "b".repeat(130)))))
        .andExpect(status().isNotFound());
  }

  private RequestPostProcessor userPrincipal(Long userId) {
    return authenticatedPrincipal(userId, "ROLE_USER");
  }

  private RequestPostProcessor adminPrincipal(Long userId) {
    return authenticatedPrincipal(userId, "ROLE_ADMIN");
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
