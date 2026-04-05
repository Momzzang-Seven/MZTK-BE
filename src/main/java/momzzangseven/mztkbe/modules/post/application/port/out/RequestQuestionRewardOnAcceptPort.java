package momzzangseven.mztkbe.modules.post.application.port.out;

public interface RequestQuestionRewardOnAcceptPort {

  void request(
      Long postId,
      Long acceptedAnswerId,
      Long requesterUserId,
      Long answerWriterUserId,
      Long rewardMztk);
}
