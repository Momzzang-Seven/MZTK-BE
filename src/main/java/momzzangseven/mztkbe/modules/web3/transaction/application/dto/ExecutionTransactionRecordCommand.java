package momzzangseven.mztkbe.modules.web3.transaction.application.dto;

import java.math.BigInteger;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.transaction.domain.vo.TransactionReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.vo.TransactionStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.vo.TransactionType;

public record ExecutionTransactionRecordCommand(
    String idempotencyKey,
    TransactionReferenceType referenceType,
    String referenceId,
    Long fromUserId,
    Long toUserId,
    String fromAddress,
    String toAddress,
    BigInteger amountWei,
    Long nonce,
    TransactionStatus status,
    TransactionType txType,
    String authorityAddress,
    Long authorizationNonce,
    String delegateTarget,
    LocalDateTime authorizationExpiresAt) {}
