package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionIntentIdempotencyMismatchPolicy;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.CreateExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.SignRequestBundle;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceAdminExecutionDraftSubmitResult;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionDraft;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceUnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionActionType;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionResourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubmitMarketplaceAdminExecutionIntentAdapter")
class SubmitMarketplaceAdminExecutionIntentAdapterTest {

  @Mock private CreateExecutionIntentUseCase createExecutionIntentUseCase;
  @InjectMocks private SubmitMarketplaceAdminExecutionIntentAdapter adapter;

  @Test
  @DisplayName("admin submit은 REJECT_ON_MISMATCH 정책으로 caller transaction에 참여한다")
  void submit_usesRejectOnMismatchPolicy() {
    given(createExecutionIntentUseCase.execute(any()))
        .willReturn(
            new CreateExecutionIntentResult(
                ExecutionResourceType.ORDER,
                "123",
                ExecutionResourceStatus.PENDING_EXECUTION,
                "intent-1",
                ExecutionIntentStatus.AWAITING_SIGNATURE,
                LocalDateTime.parse("2026-05-20T10:02:00"),
                1_000L,
                ExecutionMode.EIP1559,
                1,
                SignRequestBundle.forEip1559(
                    new SignRequestBundle.TransactionSignRequest(
                        11155111L,
                        "0x5555555555555555555555555555555555555555",
                        "0x4444444444444444444444444444444444444444",
                        "0x0",
                        "0xadmin",
                        0L,
                        "0x13880",
                        "0x77359400",
                        "0xba43b7400",
                        0L)),
                false,
                "{\"payloadVersion\":2}"));

    MarketplaceAdminExecutionDraftSubmitResult result = adapter.submit(draft());

    ArgumentCaptor<CreateExecutionIntentCommand> captor =
        ArgumentCaptor.forClass(CreateExecutionIntentCommand.class);
    verify(createExecutionIntentUseCase).execute(captor.capture());
    assertThat(captor.getValue().mismatchPolicy())
        .isEqualTo(ExecutionIntentIdempotencyMismatchPolicy.REJECT_ON_MISMATCH);
    assertThat(captor.getValue().draft().actionType().name()).isEqualTo("MARKETPLACE_ADMIN_REFUND");
    assertThat(captor.getValue().draft().unsignedTxSnapshot()).isNotNull();
    assertThat(result.executionIntentId()).isEqualTo("intent-1");
    assertThat(result.payloadSnapshotJson()).isEqualTo("{\"payloadVersion\":2}");
  }

  private MarketplaceExecutionDraft draft() {
    return new MarketplaceExecutionDraft(
        MarketplaceExecutionResourceType.ORDER,
        "123",
        MarketplaceExecutionResourceStatus.PENDING_EXECUTION,
        MarketplaceExecutionActionType.MARKETPLACE_ADMIN_REFUND,
        10L,
        20L,
        "123e4567-e89b-12d3-a456-426614174000",
        "0x00000000000000000000000000000000123e4567e89b12d3a456426614174000",
        "root",
        "0x" + "a".repeat(64),
        "{\"payloadVersion\":2}",
        List.of(
            new MarketplaceExecutionDraftCall(
                "0x4444444444444444444444444444444444444444", BigInteger.ZERO, "0xadmin")),
        false,
        null,
        null,
        null,
        null,
        new MarketplaceUnsignedTxSnapshot(
            11155111L,
            "0x5555555555555555555555555555555555555555",
            "0x4444444444444444444444444444444444444444",
            BigInteger.ZERO,
            "0xadmin",
            0L,
            BigInteger.valueOf(80_000),
            BigInteger.valueOf(2_000_000_000L),
            BigInteger.valueOf(50_000_000_000L)),
        "0x" + "b".repeat(64),
        null,
        null,
        LocalDateTime.parse("2026-05-20T10:02:00"));
  }
}
