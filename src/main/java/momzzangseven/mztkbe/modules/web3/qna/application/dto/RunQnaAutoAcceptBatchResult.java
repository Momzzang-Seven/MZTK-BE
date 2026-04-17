package momzzangseven.mztkbe.modules.web3.qna.application.dto;

public record RunQnaAutoAcceptBatchResult(int scheduledCount, int skippedCount, int failedCount) {

  public boolean isEmpty() {
    return scheduledCount <= 0 && skippedCount <= 0 && failedCount <= 0;
  }
}
