package momzzangseven.mztkbe.modules.web3.transfer.application.result;

import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record PrepareTokenTransferResult(
    String prepareId,
    String idempotencyKey,
    String txType,
    String authorityAddress,
    Long authorityNonce,
    String delegateTarget,
    LocalDateTime authExpiresAt,
    String payloadHashToSign) {}
