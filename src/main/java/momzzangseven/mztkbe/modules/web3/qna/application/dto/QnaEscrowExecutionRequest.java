package momzzangseven.mztkbe.modules.web3.qna.application.dto;

import java.math.BigInteger;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType;

public record QnaEscrowExecutionRequest(
    QnaExecutionResourceType resourceType,
    String resourceId,
    QnaExecutionActionType actionType,
    Long requesterUserId,
    Long counterpartyUserId,
    Long postId,
    Long answerId,
    String tokenAddress,
    BigInteger rewardAmountWei,
    String questionHash,
    String contentHash) {}
