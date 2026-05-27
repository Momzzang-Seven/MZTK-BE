package momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce;

import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceSlotStatus;

public record SponsorNonceSlotReservation(
    long chainId,
    String fromAddress,
    long nonce,
    int attemptNo,
    Long attemptId,
    Long transactionId,
    SponsorNonceSlotStatus status) {}
