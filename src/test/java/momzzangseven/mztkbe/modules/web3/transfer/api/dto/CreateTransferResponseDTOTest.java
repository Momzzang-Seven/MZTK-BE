package momzzangseven.mztkbe.modules.web3.transfer.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.SignRequestBundle;
import org.junit.jupiter.api.Test;

class CreateTransferResponseDTOTest {

  @Test
  void from_mapsResourceIdInsteadOfExecutionIntentId() {
    CreateExecutionIntentResult result =
        new CreateExecutionIntentResult(
            ExecutionResourceType.TRANSFER,
            "web3:TRANSFER_SEND:7:req-1",
            "PENDING_EXECUTION",
            "intent-1",
            ExecutionIntentStatus.AWAITING_SIGNATURE,
            LocalDateTime.now().plusMinutes(5),
            ExecutionMode.EIP1559,
            1,
            SignRequestBundle.forEip1559(
                new SignRequestBundle.TransactionSignRequest(
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
            false);

    CreateTransferResponseDTO response = CreateTransferResponseDTO.from(result);

    assertThat(response.resource().id()).isEqualTo("web3:TRANSFER_SEND:7:req-1");
    assertThat(response.executionIntent().id()).isEqualTo("intent-1");
  }
}
