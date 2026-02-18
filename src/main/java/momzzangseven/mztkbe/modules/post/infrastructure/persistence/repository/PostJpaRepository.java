package momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository;

import momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.PostEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PostJpaRepository extends JpaRepository<PostEntity, Long> {}
