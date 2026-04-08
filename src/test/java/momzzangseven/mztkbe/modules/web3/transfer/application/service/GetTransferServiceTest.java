package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.GetTransferQuery;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.LoadTransferExecutionIntentPort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.LoadTransferExecutionPort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferExecutionMode;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferTransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetTransferServiceTest {

  @Mock private LoadTransferExecutionIntentPort loadTransferExecutionIntentPort;
  @Mock private LoadTransferExecutionPort loadTransferExecutionPort;

  private GetTransferService service;

  @BeforeEach
  void setUp() {
    service = new GetTransferService(loadTransferExecutionIntentPort, loadTransferExecutionPort);
  }

  @Test
  void execute_loadsLatestTransferIntentByResourceId() {
    TransferExecutionIntentResult expected =
        new TransferExecutionIntentResult(
            TransferExecutionResourceType.TRANSFER,
            "web3:TRANSFER_SEND:7:req-1",
            TransferExecutionResourceStatus.PENDING_EXECUTION,
            "intent-latest",
            TransferExecutionIntentStatus.PENDING_ONCHAIN,
            LocalDateTime.now().plusMinutes(1),
            TransferExecutionMode.EIP1559,
            1,
            null,
            false,
            12L,
            TransferTransactionStatus.PENDING,
            "0xtx");

    when(loadTransferExecutionIntentPort.findLatestExecutionIntentId(
            7L, "web3:TRANSFER_SEND:7:req-1"))
        .thenReturn(Optional.of("intent-latest"));
    when(loadTransferExecutionPort.load(7L, "intent-latest")).thenReturn(expected);

    TransferExecutionIntentResult result =
        service.execute(new GetTransferQuery(7L, "web3:TRANSFER_SEND:7:req-1"));

    assertThat(result).isEqualTo(expected);
    verify(loadTransferExecutionIntentPort)
        .findLatestExecutionIntentId(7L, "web3:TRANSFER_SEND:7:req-1");
    verify(loadTransferExecutionPort).load(7L, "intent-latest");
  }
}
