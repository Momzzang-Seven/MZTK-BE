package momzzangseven.mztkbe.modules.tag.infrastructure.persistence.repository;

import momzzangseven.mztkbe.modules.tag.infrastructure.persistence.entity.PostTagEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostTagJpaRepository extends JpaRepository<PostTagEntity, Long> {
  void deleteByPostId(Long postId);
}
