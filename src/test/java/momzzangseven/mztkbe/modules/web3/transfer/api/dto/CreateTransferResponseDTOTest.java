package momzzangseven.mztkbe.modules.web3.transfer.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferSignRequestBundle;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferExecutionMode;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferExecutionResourceType;
import org.junit.jupiter.api.Test;

class CreateTransferResponseDTOTest {

  @Test
  void from_mapsResourceIdInsteadOfExecutionIntentId() {
    TransferExecutionIntentResult result =
        new TransferExecutionIntentResult(
            TransferExecutionResourceType.TRANSFER,
            "web3:TRANSFER_SEND:7:req-1",
            TransferExecutionResourceStatus.PENDING_EXECUTION,
            "intent-1",
            TransferExecutionIntentStatus.AWAITING_SIGNATURE,
            LocalDateTime.now().plusMinutes(5),
            TransferExecutionMode.EIP1559,
            1,
            TransferSignRequestBundle.forEip1559(
                new TransferSignRequestBundle.TransactionSignRequest(
                    11155111L,
                    "0x" + "1".repeat(40),
                    "0x" + "2".repeat(40),
                    "0x0",
                    "0x1234",
                    1L,
                    "0x13880",
                    "0x77359400",
                    "0xba43b7400",
                    1L)),
            false,
            null,
            null,
            null);

    CreateTransferResponseDTO response = CreateTransferResponseDTO.from(result);

    assertThat(response.resource().id()).isEqualTo("web3:TRANSFER_SEND:7:req-1");
    assertThat(response.executionIntent().id()).isEqualTo("intent-1");
  }
}
