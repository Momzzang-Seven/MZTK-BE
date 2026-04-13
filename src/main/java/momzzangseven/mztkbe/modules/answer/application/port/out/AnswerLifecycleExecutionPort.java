package momzzangseven.mztkbe.modules.answer.application.port.out;

public interface AnswerLifecycleExecutionPort {

  void prepareAnswerCreate(
      Long postId,
      Long answerId,
      Long requesterUserId,
      Long questionWriterUserId,
      String questionContent,
      Long rewardMztk,
      String answerContent,
      int activeAnswerCount);

  void prepareAnswerUpdate(
      Long postId,
      Long answerId,
      Long requesterUserId,
      Long questionWriterUserId,
      String questionContent,
      Long rewardMztk,
      String answerContent,
      int activeAnswerCount);

  void prepareAnswerDelete(
      Long postId,
      Long answerId,
      Long requesterUserId,
      Long questionWriterUserId,
      String questionContent,
      Long rewardMztk,
      int activeAnswerCount);
}
