package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.external.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.CreateExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.SignRequestBundle;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionDraft;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.wallet.domain.vo.WalletApprovalExecutionActionType;
import momzzangseven.mztkbe.modules.web3.wallet.domain.vo.WalletApprovalExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.wallet.domain.vo.WalletApprovalExecutionResourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubmitWalletApprovalExecutionIntentAdapterTest {

  @Mock private CreateExecutionIntentUseCase createExecutionIntentUseCase;

  @Test
  void submit_mapsWalletDraftToSharedExecutionDraftAndMapsResultBack() {
    SubmitWalletApprovalExecutionIntentAdapter adapter =
        new SubmitWalletApprovalExecutionIntentAdapter(createExecutionIntentUseCase);
    LocalDateTime expiresAt = LocalDateTime.parse("2026-05-13T09:05:00");
    WalletApprovalExecutionDraft draft = draft(expiresAt);
    when(createExecutionIntentUseCase.execute(any()))
        .thenReturn(
            new CreateExecutionIntentResult(
                ExecutionResourceType.WALLET_REGISTRATION,
                "registration-1",
                ExecutionResourceStatus.PENDING_EXECUTION,
                "intent-1",
                ExecutionIntentStatus.AWAITING_SIGNATURE,
                expiresAt,
                1L,
                ExecutionMode.EIP7702,
                2,
                SignRequestBundle.forEip7702(
                    new SignRequestBundle.AuthorizationSignRequest(
                        11155111L, "0x" + "2".repeat(40), 12L, "0x" + "a".repeat(64)),
                    new SignRequestBundle.SubmitSignRequest("0x" + "b".repeat(64), 123L)),
                false));

    WalletApprovalExecutionIntentResult result = adapter.submit(draft);

    ArgumentCaptor<CreateExecutionIntentCommand> captor =
        ArgumentCaptor.forClass(CreateExecutionIntentCommand.class);
    verify(createExecutionIntentUseCase).execute(captor.capture());
    assertThat(captor.getValue().draft().resourceType().name()).isEqualTo("WALLET_REGISTRATION");
    assertThat(captor.getValue().draft().actionType().name()).isEqualTo("WALLET_ESCROW_APPROVE");
    assertThat(captor.getValue().draft().resourceId()).isEqualTo("registration-1");
    assertThat(captor.getValue().draft().calls()).hasSize(2);
    assertThat(captor.getValue().draft().delegateTarget()).isEqualTo("0x" + "2".repeat(40));

    assertThat(result.resource().type()).isEqualTo("WALLET_REGISTRATION");
    assertThat(result.actionType()).isEqualTo("WALLET_ESCROW_APPROVE");
    assertThat(result.executionIntent().id()).isEqualTo("intent-1");
    assertThat(result.execution().mode()).isEqualTo("EIP7702");
    assertThat(result.execution().signCount()).isEqualTo(2);
    assertThat(result.signRequest().authorization().authorityNonce()).isEqualTo(12L);
    assertThat(result.signRequest().submit().executionDigest()).isEqualTo("0x" + "b".repeat(64));
  }

  private WalletApprovalExecutionDraft draft(LocalDateTime expiresAt) {
    return new WalletApprovalExecutionDraft(
        WalletApprovalExecutionResourceType.WALLET_REGISTRATION,
        "registration-1",
        WalletApprovalExecutionResourceStatus.PENDING_EXECUTION,
        WalletApprovalExecutionActionType.WALLET_ESCROW_APPROVE,
        7L,
        null,
        "wallet-registration-approval:registration-1",
        "0x" + "1".repeat(64),
        "{}",
        List.of(
            new WalletApprovalExecutionDraftCall(
                "0x" + "1".repeat(40), BigInteger.ZERO, "0x095ea7b3" + "0".repeat(128)),
            new WalletApprovalExecutionDraftCall(
                "0x" + "1".repeat(40), BigInteger.ZERO, "0x095ea7b3" + "0".repeat(128))),
        false,
        "0x" + "a".repeat(40),
        12L,
        "0x" + "2".repeat(40),
        "0x" + "a".repeat(64),
        null,
        null,
        expiresAt);
  }
}
