package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.external.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetLatestExecutionIntentSummaryQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetLatestExecutionIntentSummaryResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.SignRequestUnavailableReason;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetLatestExecutionIntentSummaryUseCase;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.SignRequestBundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletApprovalExecutionStateAdapterTest {

  private static final LocalDateTime EXPIRES_AT = LocalDateTime.parse("2026-05-13T10:05:00");

  @Mock private GetExecutionIntentUseCase getExecutionIntentUseCase;
  @Mock private GetLatestExecutionIntentSummaryUseCase getLatestExecutionIntentSummaryUseCase;

  private WalletApprovalExecutionStateAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter =
        new WalletApprovalExecutionStateAdapter(
            Optional.of(getExecutionIntentUseCase),
            Optional.of(getLatestExecutionIntentSummaryUseCase));
  }

  @Test
  void loadByExecutionIntentId_usesRequesterBoundExecutionReadAndMapsSignRequest() {
    when(getExecutionIntentUseCase.execute(any())).thenReturn(getResult());

    var result = adapter.loadByExecutionIntentId(7L, "intent-1");

    ArgumentCaptor<GetExecutionIntentQuery> captor =
        ArgumentCaptor.forClass(GetExecutionIntentQuery.class);
    verify(getExecutionIntentUseCase).execute(captor.capture());
    assertThat(captor.getValue().requesterUserId()).isEqualTo(7L);
    assertThat(result).isPresent();
    assertThat(result.get().executionIntentStatus()).isEqualTo("AWAITING_SIGNATURE");
    assertThat(result.get().signRequest().authorization().authorityNonce()).isEqualTo(12L);
    assertThat(result.get().signRequestUnavailableReason()).isNull();
  }

  @Test
  void loadByExecutionIntentId_whenExecutionReadRejects_returnsEmpty() {
    when(getExecutionIntentUseCase.execute(any()))
        .thenThrow(new Web3InvalidInputException("owner mismatch"));

    assertThat(adapter.loadByExecutionIntentId(7L, "intent-1")).isEmpty();
  }

  @Test
  void loadLatestByRegistrationId_mapsSummaryWithoutSignRequest() {
    when(getLatestExecutionIntentSummaryUseCase.execute(any()))
        .thenReturn(Optional.of(summaryResult()));

    var result = adapter.loadLatestByRegistrationId("registration-1");

    ArgumentCaptor<GetLatestExecutionIntentSummaryQuery> captor =
        ArgumentCaptor.forClass(GetLatestExecutionIntentSummaryQuery.class);
    verify(getLatestExecutionIntentSummaryUseCase).execute(captor.capture());
    assertThat(captor.getValue().resourceId()).isEqualTo("registration-1");
    assertThat(result).isPresent();
    assertThat(result.get().signRequest()).isNull();
    assertThat(result.get().transactionStatus()).isEqualTo("PENDING");
  }

  @Test
  void loadByExecutionIntentId_mapsEip1559TransactionSignRequest() {
    when(getExecutionIntentUseCase.execute(any())).thenReturn(eip1559Result());

    var result = adapter.loadByExecutionIntentId(7L, "intent-1");

    assertThat(result).isPresent();
    assertThat(result.get().signRequest().authorization()).isNull();
    assertThat(result.get().signRequest().transaction().fromAddress())
        .isEqualTo("0x" + "1".repeat(40));
  }

  @Test
  void loadByExecutionIntentId_mapsSignRequestUnavailableReason() {
    when(getExecutionIntentUseCase.execute(any())).thenReturn(unavailableResult());

    var result = adapter.loadByExecutionIntentId(7L, "intent-1");

    assertThat(result).isPresent();
    assertThat(result.get().signRequest()).isNull();
    assertThat(result.get().signRequestUnavailableReason()).isEqualTo("EIP7702_DEADLINE_TOO_CLOSE");
  }

  private static GetExecutionIntentResult getResult() {
    return new GetExecutionIntentResult(
        ExecutionResourceType.WALLET_REGISTRATION,
        "registration-1",
        ExecutionResourceStatus.PENDING_EXECUTION,
        ExecutionActionType.WALLET_ESCROW_APPROVE,
        "0x" + "1".repeat(64),
        "{}",
        "intent-1",
        ExecutionIntentStatus.AWAITING_SIGNATURE,
        EXPIRES_AT,
        123L,
        ExecutionMode.EIP7702,
        2,
        SignRequestBundle.forEip7702(
            new SignRequestBundle.AuthorizationSignRequest(
                11155111L, "0x" + "2".repeat(40), 12L, "0x" + "a".repeat(64)),
            new SignRequestBundle.SubmitSignRequest("0x" + "b".repeat(64), 123L)),
        null,
        11L,
        ExecutionTransactionStatus.SIGNED,
        "0x" + "c".repeat(64));
  }

  private static GetLatestExecutionIntentSummaryResult summaryResult() {
    return new GetLatestExecutionIntentSummaryResult(
        ExecutionResourceType.WALLET_REGISTRATION,
        "registration-1",
        ExecutionResourceStatus.PENDING_EXECUTION,
        ExecutionActionType.WALLET_ESCROW_APPROVE,
        "intent-1",
        ExecutionIntentStatus.PENDING_ONCHAIN,
        EXPIRES_AT,
        1L,
        ExecutionMode.EIP7702,
        2,
        11L,
        ExecutionTransactionStatus.PENDING,
        "0x" + "c".repeat(64));
  }

  private static GetExecutionIntentResult eip1559Result() {
    return new GetExecutionIntentResult(
        ExecutionResourceType.WALLET_REGISTRATION,
        "registration-1",
        ExecutionResourceStatus.PENDING_EXECUTION,
        ExecutionActionType.WALLET_ESCROW_APPROVE,
        "0x" + "1".repeat(64),
        "{}",
        "intent-1",
        ExecutionIntentStatus.AWAITING_SIGNATURE,
        EXPIRES_AT,
        123L,
        ExecutionMode.EIP1559,
        1,
        SignRequestBundle.forEip1559(
            new SignRequestBundle.TransactionSignRequest(
                10L,
                "0x" + "1".repeat(40),
                "0x" + "2".repeat(40),
                "0x0",
                "0x",
                1L,
                "0x5208",
                "0x1",
                "0x2",
                1L)),
        null,
        null,
        null,
        null);
  }

  private static GetExecutionIntentResult unavailableResult() {
    return new GetExecutionIntentResult(
        ExecutionResourceType.WALLET_REGISTRATION,
        "registration-1",
        ExecutionResourceStatus.PENDING_EXECUTION,
        ExecutionActionType.WALLET_ESCROW_APPROVE,
        "0x" + "1".repeat(64),
        "{}",
        "intent-1",
        ExecutionIntentStatus.AWAITING_SIGNATURE,
        EXPIRES_AT,
        123L,
        ExecutionMode.EIP7702,
        2,
        null,
        SignRequestUnavailableReason.EIP7702_DEADLINE_TOO_CLOSE,
        null,
        null,
        null);
  }
}
