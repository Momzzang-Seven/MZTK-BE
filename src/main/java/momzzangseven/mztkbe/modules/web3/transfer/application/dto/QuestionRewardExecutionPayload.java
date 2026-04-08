package momzzangseven.mztkbe.modules.web3.transfer.application.dto;

import java.math.BigInteger;

public record QuestionRewardExecutionPayload(
    Long postId,
    Long acceptedCommentId,
    Long fromUserId,
    Long toUserId,
    String authorityAddress,
    String toAddress,
    String tokenContractAddress,
    BigInteger amountWei) {}
