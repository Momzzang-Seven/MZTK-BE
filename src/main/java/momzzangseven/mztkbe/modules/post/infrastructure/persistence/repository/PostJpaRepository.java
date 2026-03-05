package momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository;

import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.PostEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PostJpaRepository extends JpaRepository<PostEntity, Long> {

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update PostEntity p set p.isSolved = true"
          + " where p.id = :postId and p.type = :postType and p.isSolved = false")
  int markSolvedByIdIfType(@Param("postId") Long postId, @Param("postType") PostType postType);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = "DELETE FROM post_images WHERE post_id = :postId", nativeQuery = true)
  void deleteImagesByPostId(@Param("postId") Long postId);
}
