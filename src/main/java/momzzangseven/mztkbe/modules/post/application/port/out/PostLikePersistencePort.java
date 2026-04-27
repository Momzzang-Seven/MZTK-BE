package momzzangseven.mztkbe.modules.post.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import momzzangseven.mztkbe.modules.post.domain.model.PostLike;
import momzzangseven.mztkbe.modules.post.domain.model.PostLikeTargetType;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

public interface PostLikePersistencePort {

  boolean exists(PostLikeTargetType targetType, Long targetId, Long userId);

  PostLike save(PostLike postLike);

  void insertIfAbsent(PostLike postLike);

  void delete(PostLikeTargetType targetType, Long targetId, Long userId);

  long countByTarget(PostLikeTargetType targetType, Long targetId);

  Map<Long, Long> countByTargetIds(PostLikeTargetType targetType, Collection<Long> targetIds);

  Set<Long> findLikedTargetIds(
      PostLikeTargetType targetType, Collection<Long> targetIds, Long userId);

  Optional<PostLike> find(PostLikeTargetType targetType, Long targetId, Long userId);

  void deleteByTarget(PostLikeTargetType targetType, Long targetId);

  List<LikedPostRow> findLikedPostsByCursor(
      Long userId, PostType type, String search, CursorPageRequest pageRequest);
}
