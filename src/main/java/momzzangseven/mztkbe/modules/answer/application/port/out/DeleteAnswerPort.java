package momzzangseven.mztkbe.modules.answer.application.port.out;

public interface DeleteAnswerPort {
  void deleteAnswer(Long answerId);

  void deleteAnswersByPostId(Long postId);
}
