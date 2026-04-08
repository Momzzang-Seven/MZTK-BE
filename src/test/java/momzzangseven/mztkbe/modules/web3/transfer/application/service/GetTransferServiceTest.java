package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.UnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.GetTransferQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetTransferServiceTest {

  @Mock private ExecutionIntentPersistencePort executionIntentPersistencePort;
  @Mock private GetExecutionIntentUseCase getExecutionIntentUseCase;

  private GetTransferService service;

  @BeforeEach
  void setUp() {
    service = new GetTransferService(executionIntentPersistencePort, getExecutionIntentUseCase);
  }

  @Test
  void execute_loadsLatestTransferIntentByResourceId() {
    ExecutionIntent latestIntent = latestTransferIntent();
    GetExecutionIntentResult expected =
        new GetExecutionIntentResult(
            ExecutionResourceType.TRANSFER,
            "web3:TRANSFER_SEND:7:req-1",
            ExecutionResourceStatus.PENDING_EXECUTION,
            "intent-latest",
            latestIntent.getStatus(),
            latestIntent.getExpiresAt(),
            latestIntent.getMode(),
            1,
            null,
            12L,
            null,
            "0xtx");

    when(executionIntentPersistencePort.findLatestByRequesterAndResource(
            7L, ExecutionResourceType.TRANSFER, "web3:TRANSFER_SEND:7:req-1"))
        .thenReturn(Optional.of(latestIntent));
    when(getExecutionIntentUseCase.execute(new GetExecutionIntentQuery(7L, "intent-latest")))
        .thenReturn(expected);

    GetExecutionIntentResult result =
        service.execute(new GetTransferQuery(7L, "web3:TRANSFER_SEND:7:req-1"));

    assertThat(result).isEqualTo(expected);
    verify(executionIntentPersistencePort)
        .findLatestByRequesterAndResource(
            7L, ExecutionResourceType.TRANSFER, "web3:TRANSFER_SEND:7:req-1");
    verify(getExecutionIntentUseCase).execute(new GetExecutionIntentQuery(7L, "intent-latest"));
  }

  private ExecutionIntent latestTransferIntent() {
    return ExecutionIntent.create(
            "intent-latest",
            "root-transfer-1",
            2,
            ExecutionResourceType.TRANSFER,
            "web3:TRANSFER_SEND:7:req-1",
            ExecutionActionType.TRANSFER_SEND,
            7L,
            8L,
            ExecutionMode.EIP1559,
            "0x" + "a".repeat(64),
            "{\"payload\":true}",
            null,
            null,
            null,
            LocalDateTime.now().plusMinutes(1),
            null,
            null,
            new UnsignedTxSnapshot(
                11155111L,
                "0x" + "1".repeat(40),
                "0x" + "2".repeat(40),
                java.math.BigInteger.ZERO,
                "0x1234",
                5L,
                java.math.BigInteger.valueOf(80_000),
                java.math.BigInteger.valueOf(2_000_000_000L),
                java.math.BigInteger.valueOf(50_000_000_000L)),
            "0x" + "b".repeat(64),
            java.math.BigInteger.ZERO,
            LocalDate.of(2026, 4, 6),
            LocalDateTime.now())
        .toBuilder()
        .id(2L)
        .build()
        .markSigned(12L, LocalDateTime.of(2026, 4, 7, 12, 0));
  }
}
