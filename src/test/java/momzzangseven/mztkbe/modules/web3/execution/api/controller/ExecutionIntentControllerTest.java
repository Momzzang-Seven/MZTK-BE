package momzzangseven.mztkbe.modules.web3.execution.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecuteExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ExecuteExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.SignRequestBundle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@TestPropertySource(properties = {"web3.reward-token.enabled=true", "web3.eip7702.enabled=true"})
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("ExecutionIntentController 컨트롤러 계약 테스트 (MockMvc + H2)")
class ExecutionIntentControllerTest {

  private static final String INTENT_ID = "intent-abc-123";

  @Autowired private MockMvc mockMvc;

  @Autowired private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

  @MockitoBean private GetExecutionIntentUseCase getExecutionIntentUseCase;
  @MockitoBean private ExecuteExecutionIntentUseCase executeExecutionIntentUseCase;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.application.port.in
          .MarkTransactionSucceededUseCase
      markTransactionSucceededUseCase;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionReceiptWorker
      transactionReceiptWorker;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionIssuerWorker
      transactionIssuerWorker;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .SignedRecoveryWorker
      signedRecoveryWorker;

  // ── GET ──────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("GET /{id} — AWAITING_SIGNATURE 상태일 때 signRequest 포함 200 반환")
  void getExecutionIntent_returnsSignRequest_whenAwaitingSignature() throws Exception {
    LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);
    BDDMockito.given(getExecutionIntentUseCase.execute(any(GetExecutionIntentQuery.class)))
        .willReturn(
            new GetExecutionIntentResult(
                ExecutionResourceType.QUESTION,
                "web3:QUESTION:101",
                ExecutionResourceStatus.PENDING_EXECUTION,
                INTENT_ID,
                ExecutionIntentStatus.AWAITING_SIGNATURE,
                expiresAt,
                ExecutionMode.EIP7702,
                2,
                SignRequestBundle.forEip7702(
                    new SignRequestBundle.AuthorizationSignRequest(
                        11155420L, "0x" + "1".repeat(40), 0L, "0x" + "a".repeat(64)),
                    new SignRequestBundle.SubmitSignRequest(
                        "0x" + "b".repeat(64),
                        expiresAt.toEpochSecond(ZoneOffset.UTC))),
                null,
                null,
                null));

    mockMvc
        .perform(
            get("/users/me/web3/execution-intents/{id}", INTENT_ID).with(userPrincipal(7L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.resource.type").value("QUESTION"))
        .andExpect(jsonPath("$.data.resource.id").value("web3:QUESTION:101"))
        .andExpect(jsonPath("$.data.resource.status").value("PENDING_EXECUTION"))
        .andExpect(jsonPath("$.data.executionIntent.id").value(INTENT_ID))
        .andExpect(jsonPath("$.data.executionIntent.status").value("AWAITING_SIGNATURE"))
        .andExpect(jsonPath("$.data.execution.mode").value("EIP7702"))
        .andExpect(jsonPath("$.data.execution.signCount").value(2))
        .andExpect(jsonPath("$.data.signRequest.authorization").exists())
        .andExpect(jsonPath("$.data.signRequest.submit").exists())
        .andExpect(jsonPath("$.data.transaction").doesNotExist());

    verify(getExecutionIntentUseCase).execute(any(GetExecutionIntentQuery.class));
  }

  @Test
  @DisplayName("GET /{id} — PENDING_ONCHAIN 상태일 때 transaction 포함 200 반환")
  void getExecutionIntent_returnsTransaction_whenPendingOnchain() throws Exception {
    BDDMockito.given(getExecutionIntentUseCase.execute(any(GetExecutionIntentQuery.class)))
        .willReturn(
            new GetExecutionIntentResult(
                ExecutionResourceType.ANSWER,
                "web3:ANSWER:201",
                ExecutionResourceStatus.PENDING_EXECUTION,
                INTENT_ID,
                ExecutionIntentStatus.PENDING_ONCHAIN,
                LocalDateTime.now().plusMinutes(5),
                ExecutionMode.EIP7702,
                2,
                null,
                42L,
                ExecutionTransactionStatus.PENDING,
                "0x" + "c".repeat(64)));

    mockMvc
        .perform(
            get("/users/me/web3/execution-intents/{id}", INTENT_ID).with(userPrincipal(7L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.executionIntent.status").value("PENDING_ONCHAIN"))
        .andExpect(jsonPath("$.data.transaction.id").value(42))
        .andExpect(jsonPath("$.data.transaction.status").value("PENDING"))
        .andExpect(jsonPath("$.data.signRequest").doesNotExist());

    verify(getExecutionIntentUseCase).execute(any(GetExecutionIntentQuery.class));
  }

  @Test
  @DisplayName("GET /{id} — 인증 없으면 401 반환")
  void getExecutionIntent_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(get("/users/me/web3/execution-intents/{id}", INTENT_ID))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(getExecutionIntentUseCase);
  }

  // ── POST /execute ─────────────────────────────────────────────────────────

  @Test
  @DisplayName("POST /{id}/execute — EIP7702 서명 제출 성공 시 202 반환")
  void executeExecutionIntent_success_returns202() throws Exception {
    BDDMockito.given(executeExecutionIntentUseCase.execute(any(ExecuteExecutionIntentCommand.class)))
        .willReturn(
            new ExecuteExecutionIntentResult(
                INTENT_ID, ExecutionIntentStatus.PENDING_ONCHAIN, 42L,
                ExecutionTransactionStatus.CREATED, null));

    mockMvc
        .perform(
            post("/users/me/web3/execution-intents/{id}/execute", INTENT_ID)
                .with(userPrincipal(7L))
                .contentType(APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "authorizationSignature", "0x" + "d".repeat(130),
                            "submitSignature", "0x" + "e".repeat(130)))))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.executionIntent.id").value(INTENT_ID))
        .andExpect(jsonPath("$.data.executionIntent.status").value("PENDING_ONCHAIN"))
        .andExpect(jsonPath("$.data.transaction.id").value(42));

    verify(executeExecutionIntentUseCase).execute(any(ExecuteExecutionIntentCommand.class));
  }

  @Test
  @DisplayName("POST /{id}/execute — 인증 없으면 401 반환")
  void executeExecutionIntent_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(
            post("/users/me/web3/execution-intents/{id}/execute", INTENT_ID)
                .contentType(APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(executeExecutionIntentUseCase);
  }

  // ── 공통 헬퍼 ─────────────────────────────────────────────────────────────

  private org.springframework.test.web.servlet.request.RequestPostProcessor userPrincipal(
      Long userId) {
    var authorities =
        java.util.List.of(
            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"));
    var token =
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            userId, null, authorities);
    return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
        .authentication(token);
  }
}
