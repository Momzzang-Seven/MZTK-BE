package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public interface LoadQnaAutoAcceptPolicyPort {

  QnaAutoAcceptPolicy loadPolicy();

  record QnaAutoAcceptPolicy(long delaySeconds, int batchSize) {

    public QnaAutoAcceptPolicy {
      if (delaySeconds <= 0) {
        throw new Web3InvalidInputException("delaySeconds must be positive");
      }
      if (batchSize <= 0) {
        throw new Web3InvalidInputException("batchSize must be positive");
      }
    }
  }
}
