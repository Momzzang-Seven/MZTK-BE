package momzzangseven.mztkbe.modules.post.application.port.out;

import java.util.List;

public interface LoadPostAnswerIdsPort {

  List<Long> loadAnswerIdsByPostId(Long postId);
}
