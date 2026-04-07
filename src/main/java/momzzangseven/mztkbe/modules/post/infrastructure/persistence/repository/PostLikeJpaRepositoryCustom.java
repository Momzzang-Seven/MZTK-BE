package momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository;

import java.util.Optional;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.PostLikeEntity;

public interface PostLikeJpaRepositoryCustom {

  Optional<PostLikeEntity> insertIfAbsentReturning(String targetType, Long targetId, Long userId);
}
