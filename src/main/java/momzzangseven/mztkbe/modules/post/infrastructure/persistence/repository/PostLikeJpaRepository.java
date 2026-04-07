package momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.post.domain.model.PostLikeTargetType;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.PostLikeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PostLikeJpaRepository
    extends JpaRepository<PostLikeEntity, Long>, PostLikeJpaRepositoryCustom {

  boolean existsByTargetTypeAndTargetIdAndUserId(
      PostLikeTargetType targetType, Long targetId, Long userId);

  Optional<PostLikeEntity> findByTargetTypeAndTargetIdAndUserId(
      PostLikeTargetType targetType, Long targetId, Long userId);

  @Modifying
  @Query(
      "delete from PostLikeEntity p "
          + "where p.targetType = :targetType and p.targetId = :targetId and p.userId = :userId")
  void deleteByTargetTypeAndTargetIdAndUserId(
      @Param("targetType") PostLikeTargetType targetType,
      @Param("targetId") Long targetId,
      @Param("userId") Long userId);

  long countByTargetTypeAndTargetId(PostLikeTargetType targetType, Long targetId);

  @Query(
      "select p.targetId as targetId, count(p) as likeCount "
          + "from PostLikeEntity p "
          + "where p.targetType = :targetType and p.targetId in :targetIds "
          + "group by p.targetId")
  List<TargetLikeCountProjection> countByTargetIds(
      @Param("targetType") PostLikeTargetType targetType,
      @Param("targetIds") Collection<Long> targetIds);

  @Query(
      "select p.targetId "
          + "from PostLikeEntity p "
          + "where p.targetType = :targetType and p.targetId in :targetIds and p.userId = :userId")
  List<Long> findLikedTargetIds(
      @Param("targetType") PostLikeTargetType targetType,
      @Param("targetIds") Collection<Long> targetIds,
      @Param("userId") Long userId);

  @Modifying
  @Query("delete from PostLikeEntity p where p.targetType = :targetType and p.targetId = :targetId")
  void deleteByTargetTypeAndTargetId(
      @Param("targetType") PostLikeTargetType targetType, @Param("targetId") Long targetId);

  interface TargetLikeCountProjection {
    Long getTargetId();

    Long getLikeCount();
  }
}
