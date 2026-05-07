package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

public interface QnaQuestionUpdateConfirmationSyncPort {

  boolean syncConfirmedQuestionUpdate(String executionIntentPublicId);
}
