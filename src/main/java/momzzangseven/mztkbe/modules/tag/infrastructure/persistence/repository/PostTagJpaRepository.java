package momzzangseven.mztkbe.modules.tag.infrastructure.persistence.repository;

import java.util.Collection;
import java.util.List;
import momzzangseven.mztkbe.modules.tag.infrastructure.persistence.entity.PostTagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostTagJpaRepository extends JpaRepository<PostTagEntity, Long> {
  void deleteByPostId(Long postId);

  @Query("select pt.tagId from PostTagEntity pt where pt.postId = :postId")
  List<Long> findTagIdsByPostId(@Param("postId") Long postId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("delete from PostTagEntity pt where pt.postId = :postId and pt.tagId in :tagIds")
  void deleteByPostIdAndTagIdIn(
      @Param("postId") Long postId, @Param("tagIds") Collection<Long> tagIds);
}
