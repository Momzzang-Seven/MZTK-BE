package momzzangseven.mztkbe.modules.web3.transaction.application.dto;

import java.math.BigInteger;
import java.time.LocalDateTime;

public record ExecutionTransactionRecordCommand(
    String idempotencyKey,
    String referenceType,
    String referenceId,
    Long fromUserId,
    Long toUserId,
    String fromAddress,
    String toAddress,
    BigInteger amountWei,
    Long nonce,
    String status,
    String txType,
    String authorityAddress,
    Long authorizationNonce,
    String delegateTarget,
    LocalDateTime authorizationExpiresAt) {}
