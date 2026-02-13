package momzzangseven.mztkbe.modules.comment.application.port.out;

import java.util.List;

public interface DeleteCommentPort {

  void deleteAllByIdInBatch(List<Long> commentIds);
}
