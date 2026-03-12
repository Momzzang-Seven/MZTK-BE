package momzzangseven.mztkbe.modules.post.application.port.out;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface LoadPostWriterPort {

  Optional<WriterSummary> loadWriterById(Long userId);

  Map<Long, WriterSummary> loadWritersByIds(Collection<Long> userIds);

  record WriterSummary(Long userId, String nickname, String profileImageUrl) {}
}
