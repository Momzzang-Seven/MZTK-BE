package momzzangseven.mztkbe.modules.web3.transfer.api.dto.response;

import lombok.Builder;

@Builder
public record TokenTransferSubmitResponseDTO(Long transactionId, String status, String txHash) {}
