package momzzangseven.mztkbe.modules.comment.application.port.out;

import java.util.Collection;
import java.util.Map;

public interface LoadCommentWriterPort {

  Map<Long, WriterSummary> loadWritersByIds(Collection<Long> userIds);

  record WriterSummary(Long userId, String nickname, String profileImageUrl) {}
}
