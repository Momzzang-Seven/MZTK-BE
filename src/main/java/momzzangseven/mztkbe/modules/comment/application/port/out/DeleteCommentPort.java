package momzzangseven.mztkbe.modules.comment.application.port.out;

import java.util.List;

public interface DeleteCommentPort {
  void deleteAllByPostId(Long postId);

  void deleteAllById(List<Long> commentIds);
}
