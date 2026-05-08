package momzzangseven.mztkbe.modules.comment.application.port.out;

import java.util.List;

public interface DeleteCommentPort {
  void softDeleteAllByRootPostId(Long postId);

  void deleteAllByAnswerId(Long answerId);

  int softDeleteActiveOrphanAnswerComments(int batchSize);

  void deleteAllById(List<Long> commentIds);
}
