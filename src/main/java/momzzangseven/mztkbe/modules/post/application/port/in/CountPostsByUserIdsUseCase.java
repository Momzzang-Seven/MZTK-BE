package momzzangseven.mztkbe.modules.post.application.port.in;

import java.util.List;
import java.util.Map;

public interface CountPostsByUserIdsUseCase {

  Map<Long, Long> execute(List<Long> userIds);
}
