package momzzangseven.mztkbe.modules.web3.transfer.api.dto;

import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record TokenTransferPrepareResponseDTO(
    String prepareId,
    String idempotencyKey,
    String txType,
    String authorityAddress,
    Long authorityNonce,
    String delegateTarget,
    LocalDateTime authExpiresAt,
    String payloadHashToSign) {}
