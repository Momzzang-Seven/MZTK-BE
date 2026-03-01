package momzzangseven.mztkbe.modules.web3.transfer.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Map;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.PrepareTokenTransferCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.PrepareTokenTransferResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.SubmitTokenTransferCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.SubmitTokenTransferResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.PrepareTokenTransferUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.SubmitTokenTransferUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {"web3.reward-token.enabled=true", "web3.eip7702.enabled=true"})
@DisplayName("TokenTransferController 통합 테스트 (MockMvc + H2)")
@org.springframework.boot.test.context.SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@org.springframework.transaction.annotation.Transactional
class TokenTransferControllerIntegrationTest {

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

  @MockBean private PrepareTokenTransferUseCase prepareTokenTransferUseCase;
  @MockBean private SubmitTokenTransferUseCase submitTokenTransferUseCase;

  @Test
  @DisplayName("POST /users/me/token-transfers/prepare 성공")
  void prepare_success() throws Exception {
    given(prepareTokenTransferUseCase.execute(any(PrepareTokenTransferCommand.class)))
        .willReturn(
            PrepareTokenTransferResult.builder()
                .prepareId("p-1")
                .idempotencyKey("domain:QUESTION_REWARD:77:1")
                .txType("EIP7702")
                .authorityAddress("0x1111111111111111111111111111111111111111")
                .authorityNonce(7L)
                .delegateTarget("0x2222222222222222222222222222222222222222")
                .authExpiresAt(LocalDateTime.now().plusMinutes(5))
                .payloadHashToSign(
                    "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .build());

    mockMvc
        .perform(
            post("/users/me/token-transfers/prepare")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "domainType", "QUESTION_REWARD",
                            "referenceId", "77",
                            "toUserId", 2,
                            "amountWei", "1000000000000000000"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.prepareId").value("p-1"));

    verify(prepareTokenTransferUseCase).execute(any(PrepareTokenTransferCommand.class));
  }

  @Test
  @DisplayName("POST /users/me/token-transfers/prepare 인증 없으면 401")
  void prepare_unauthenticated_returns401() throws Exception {
    mockMvc.perform(post("/users/me/token-transfers/prepare")).andExpect(status().isUnauthorized());

    verifyNoInteractions(prepareTokenTransferUseCase);
  }

  @Test
  @DisplayName("POST /users/me/token-transfers/prepare principal이 null이면 401")
  void prepare_nullPrincipal_returns401() throws Exception {
    mockMvc
        .perform(
            post("/users/me/token-transfers/prepare")
                .with(nullUserPrincipal())
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "domainType", "QUESTION_REWARD",
                            "referenceId", "77",
                            "toUserId", 2,
                            "amountWei", "1000000000000000000"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("POST /users/me/token-transfers/prepare enum 값이 잘못되면 400")
  void prepare_invalidEnum_returns400() throws Exception {
    mockMvc
        .perform(
            post("/users/me/token-transfers/prepare")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "domainType", "INVALID",
                            "referenceId", "77",
                            "toUserId", 2,
                            "amountWei", "1000000000000000000"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(prepareTokenTransferUseCase);
  }

  @Test
  @DisplayName("POST /users/me/token-transfers/prepare 필수 값 누락이면 400")
  void prepare_missingRequiredField_returns400() throws Exception {
    mockMvc
        .perform(
            post("/users/me/token-transfers/prepare")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("domainType", "QUESTION_REWARD", "referenceId", "77"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(prepareTokenTransferUseCase);
  }

  @Test
  @DisplayName("POST /users/me/token-transfers/prepare toUserId가 0이면 400")
  void prepare_nonPositiveToUserId_returns400() throws Exception {
    mockMvc
        .perform(
            post("/users/me/token-transfers/prepare")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "domainType", "QUESTION_REWARD",
                            "referenceId", "77",
                            "toUserId", 0,
                            "amountWei", "1000000000000000000"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(prepareTokenTransferUseCase);
  }

  @Test
  @DisplayName("POST /users/me/token-transfers/prepare referenceId 공백이면 400")
  void prepare_blankReferenceId_returns400() throws Exception {
    mockMvc
        .perform(
            post("/users/me/token-transfers/prepare")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "domainType", "QUESTION_REWARD",
                            "referenceId", " ",
                            "toUserId", 2,
                            "amountWei", "1000000000000000000"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(prepareTokenTransferUseCase);
  }

  @Test
  @DisplayName("POST /users/me/token-transfers/prepare referenceId가 100자 초과면 400")
  void prepare_referenceIdTooLong_returns400() throws Exception {
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
                            "r".repeat(101),
                            "toUserId",
                            2,
                            "amountWei",
                            "1000000000000000000"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(prepareTokenTransferUseCase);
  }

  @Test
  @DisplayName("POST /users/me/token-transfers/prepare amountWei 형식이 잘못되면 400")
  void prepare_invalidAmountWeiFormat_returns400() throws Exception {
    mockMvc
        .perform(
            post("/users/me/token-transfers/prepare")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "domainType", "QUESTION_REWARD",
                            "referenceId", "77",
                            "toUserId", 2,
                            "amountWei", "1.5"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(prepareTokenTransferUseCase);
  }

  @Test
  @DisplayName("POST /users/me/token-transfers/submit 성공")
  void submit_success() throws Exception {
    given(submitTokenTransferUseCase.execute(any(SubmitTokenTransferCommand.class)))
        .willReturn(
            SubmitTokenTransferResult.builder()
                .transactionId(1L)
                .status("SIGNED")
                .txHash("0xabc")
                .build());

    mockMvc
        .perform(
            post("/users/me/token-transfers/submit")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "prepareId", "123e4567-e89b-12d3-a456-426614174000",
                            "authorizationSignature", signature("a"),
                            "executionSignature", signature("b")))))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.transactionId").value(1))
        .andExpect(jsonPath("$.data.status").value("SIGNED"));

    verify(submitTokenTransferUseCase).execute(any(SubmitTokenTransferCommand.class));
  }

  @Test
  @DisplayName("POST /users/me/token-transfers/submit 서명 형식이 틀리면 400")
  void submit_invalidSignature_returns400() throws Exception {
    mockMvc
        .perform(
            post("/users/me/token-transfers/submit")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "prepareId", "123e4567-e89b-12d3-a456-426614174000",
                            "authorizationSignature", "0x1234",
                            "executionSignature", signature("b")))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(submitTokenTransferUseCase);
  }

  @Test
  @DisplayName("POST /users/me/token-transfers/submit executionSignature 형식이 틀리면 400")
  void submit_invalidExecutionSignature_returns400() throws Exception {
    mockMvc
        .perform(
            post("/users/me/token-transfers/submit")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "prepareId", "123e4567-e89b-12d3-a456-426614174000",
                            "authorizationSignature", signature("a"),
                            "executionSignature", "0x1234"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(submitTokenTransferUseCase);
  }

  @Test
  @DisplayName("POST /users/me/token-transfers/submit executionSignature 공백이면 400")
  void submit_blankExecutionSignature_returns400() throws Exception {
    mockMvc
        .perform(
            post("/users/me/token-transfers/submit")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "prepareId", "123e4567-e89b-12d3-a456-426614174000",
                            "authorizationSignature", signature("a"),
                            "executionSignature", " "))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(submitTokenTransferUseCase);
  }

  @Test
  @DisplayName("POST /users/me/token-transfers/submit 인증 없으면 401")
  void submit_unauthenticated_returns401() throws Exception {
    mockMvc.perform(post("/users/me/token-transfers/submit")).andExpect(status().isUnauthorized());

    verifyNoInteractions(submitTokenTransferUseCase);
  }

  @Test
  @DisplayName("POST /users/me/token-transfers/submit prepareId 형식이 잘못되면 400")
  void submit_invalidPrepareId_returns400() throws Exception {
    mockMvc
        .perform(
            post("/users/me/token-transfers/submit")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "prepareId", "not-uuid",
                            "authorizationSignature", signature("a"),
                            "executionSignature", signature("b")))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(submitTokenTransferUseCase);
  }

  private String signature(String hexChar) {
    return "0x" + hexChar.repeat(130);
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
