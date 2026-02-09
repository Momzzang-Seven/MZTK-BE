package momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository;

import momzzangseven.mztkbe.modules.post.domain.model.Post;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {}
