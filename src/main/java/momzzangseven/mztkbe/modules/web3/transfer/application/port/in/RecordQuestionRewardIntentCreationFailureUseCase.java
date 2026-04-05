package momzzangseven.mztkbe.modules.web3.transfer.application.port.in;

public interface RecordQuestionRewardIntentCreationFailureUseCase {

  void execute(Long postId, String errorCode, String errorReason);
}
