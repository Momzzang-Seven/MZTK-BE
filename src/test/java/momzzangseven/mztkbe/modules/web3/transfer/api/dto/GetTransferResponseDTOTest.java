package momzzangseven.mztkbe.modules.web3.transfer.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferExecutionMode;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferExecutionResourceType;
import org.junit.jupiter.api.Test;

class GetTransferResponseDTOTest {

  @Test
  void from_mapsSignRequestUnavailableReason() {
    TransferExecutionIntentResult result =
        new TransferExecutionIntentResult(
            TransferExecutionResourceType.TRANSFER,
            "web3:TRANSFER_SEND:7:req-1",
            TransferExecutionResourceStatus.PENDING_EXECUTION,
            "intent-1",
            TransferExecutionIntentStatus.AWAITING_SIGNATURE,
            LocalDateTime.of(2026, 5, 13, 10, 5),
            1_778_646_300L,
            TransferExecutionMode.EIP7702,
            2,
            null,
            "EIP7702_DEADLINE_TOO_CLOSE",
            false,
            null,
            null,
            null);

    GetTransferResponseDTO response = GetTransferResponseDTO.from(result);

    assertThat(response.signRequest()).isNull();
    assertThat(response.signRequestUnavailableReason()).isEqualTo("EIP7702_DEADLINE_TOO_CLOSE");
  }
}
