package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

import java.math.BigInteger;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;

public interface BuildQnaEscrowCallDataPort {

  String encode(
      QnaExecutionActionType actionType,
      String questionId,
      String answerId,
      String tokenAddress,
      BigInteger rewardAmountWei,
      String questionHash,
      String contentHash);
}
