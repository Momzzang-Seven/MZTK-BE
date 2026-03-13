package momzzangseven.mztkbe.modules.answer.application.port.out;

import java.util.Collection;
import java.util.Map;

public interface LoadAnswerWriterPort {

  Map<Long, WriterSummary> loadWritersByIds(Collection<Long> userIds);

  record WriterSummary(Long userId, String nickname, String profileImageUrl) {}
}
