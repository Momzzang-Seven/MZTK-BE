package momzzangseven.mztkbe.modules.web3.transfer.api.dto;

import lombok.Builder;

@Builder
public record TokenTransferSubmitResponseDTO(Long transactionId, String status, String txHash) {}
