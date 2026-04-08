package momzzangseven.mztkbe.modules.web3.transfer.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Map;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.CreateTransferCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.GetTransferQuery;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferSignRequestBundle;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferExecutionMode;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferTransactionStatus;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.CreateTransferUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.GetTransferUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@TestPropertySource(properties = {"web3.reward-token.enabled=true", "web3.eip7702.enabled=true"})
@DisplayName("TransferController 컨트롤러 계약 테스트 (MockMvc + H2)")
@org.springframework.boot.test.context.SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class TransferControllerTest {

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

  @MockitoBean private CreateTransferUseCase createTransferUseCase;
  @MockitoBean private GetTransferUseCase getTransferUseCase;

  @Test
  @DisplayName("POST /users/me/transfers 성공")
  void create_success() throws Exception {
    org.mockito.BDDMockito.given(createTransferUseCase.execute(any(CreateTransferCommand.class)))
        .willReturn(
            new TransferExecutionIntentResult(
                TransferExecutionResourceType.TRANSFER,
                "web3:TRANSFER_SEND:1:req-77",
                TransferExecutionResourceStatus.PENDING_EXECUTION,
                "intent-1",
                TransferExecutionIntentStatus.AWAITING_SIGNATURE,
                LocalDateTime.now().plusMinutes(5),
                TransferExecutionMode.EIP7702,
                2,
                TransferSignRequestBundle.forEip7702(
                    new TransferSignRequestBundle.AuthorizationSignRequest(
                        11155111L, "0x" + "1".repeat(40), 7L, "0x" + "a".repeat(64)),
                    new TransferSignRequestBundle.SubmitSignRequest(
                        "0x" + "b".repeat(64),
                        LocalDateTime.now()
                            .plusMinutes(5)
                            .toEpochSecond(java.time.ZoneOffset.UTC))),
                false,
                null,
                null,
                null));

    mockMvc
        .perform(
            post("/users/me/transfers")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "toUserId", 2,
                            "clientRequestId", "req-77",
                            "amountWei", "1000000000000000000"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.resource.id").value("web3:TRANSFER_SEND:1:req-77"))
        .andExpect(jsonPath("$.data.executionIntent.id").value("intent-1"))
        .andExpect(jsonPath("$.data.execution.mode").value("EIP7702"));

    verify(createTransferUseCase).execute(any(CreateTransferCommand.class));
  }

  @Test
  @DisplayName("GET /users/me/transfers/{resourceId} 성공")
  void get_success() throws Exception {
    org.mockito.BDDMockito.given(getTransferUseCase.execute(any(GetTransferQuery.class)))
        .willReturn(
            new TransferExecutionIntentResult(
                TransferExecutionResourceType.TRANSFER,
                "web3:TRANSFER_SEND:1:req-77",
                TransferExecutionResourceStatus.PENDING_EXECUTION,
                "intent-1",
                TransferExecutionIntentStatus.PENDING_ONCHAIN,
                LocalDateTime.now().plusMinutes(5),
                TransferExecutionMode.EIP7702,
                2,
                null,
                false,
                10L,
                TransferTransactionStatus.PENDING,
                "0x" + "c".repeat(64)));

    mockMvc
        .perform(get("/users/me/transfers/web3:TRANSFER_SEND:1:req-77").with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.resource.id").value("web3:TRANSFER_SEND:1:req-77"))
        .andExpect(jsonPath("$.data.resource.status").value("PENDING_EXECUTION"))
        .andExpect(jsonPath("$.data.executionIntent.id").value("intent-1"));

    verify(getTransferUseCase).execute(any(GetTransferQuery.class));
  }

  @Test
  @DisplayName("POST /users/me/transfers 인증 없으면 401")
  void create_unauthenticated_returns401() throws Exception {
    mockMvc.perform(post("/users/me/transfers")).andExpect(status().isUnauthorized());
    verifyNoInteractions(createTransferUseCase);
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor userPrincipal(
      Long userId) {
    java.util.List<org.springframework.security.core.authority.SimpleGrantedAuthority>
        grantedAuthorities =
            java.util.List.of(
                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                    "ROLE_USER"));
    org.springframework.security.authentication.UsernamePasswordAuthenticationToken token =
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            userId, null, grantedAuthorities);
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.authentication(token);
  }

  private String json(Object body) throws Exception {
    return objectMapper.writeValueAsString(body);
  }
}
