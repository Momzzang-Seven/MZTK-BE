package momzzangseven.mztkbe.modules.web3.transaction.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record MarkTransactionSucceededRequestDTO(
    @NotBlank String txHash,
    @NotBlank String explorerUrl,
    @NotBlank String reason,
    @NotBlank String evidence) {}
