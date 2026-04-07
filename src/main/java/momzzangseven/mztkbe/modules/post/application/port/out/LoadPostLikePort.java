package momzzangseven.mztkbe.modules.post.application.port.out;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import momzzangseven.mztkbe.modules.post.domain.model.PostLikeTargetType;

public interface LoadPostLikePort {

  Map<Long, Long> countByTargetIds(PostLikeTargetType targetType, Collection<Long> targetIds);

  Set<Long> findLikedTargetIds(
      PostLikeTargetType targetType, Collection<Long> targetIds, Long userId);
}
