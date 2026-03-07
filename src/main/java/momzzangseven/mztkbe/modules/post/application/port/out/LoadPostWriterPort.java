package momzzangseven.mztkbe.modules.post.application.port.out;

import java.util.Optional;

public interface LoadPostWriterPort {

  Optional<WriterSummary> loadWriterById(Long userId);

  record WriterSummary(Long userId, String nickname, String profileImageUrl) {}
}
