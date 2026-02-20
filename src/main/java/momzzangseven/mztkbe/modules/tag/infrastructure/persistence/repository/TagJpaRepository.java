package momzzangseven.mztkbe.modules.tag.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.tag.infrastructure.persistence.entity.TagEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagJpaRepository extends JpaRepository<TagEntity, Long> {
  List<TagEntity> findByNameIn(List<String> names);

  Optional<TagEntity> findByName(String name);
}
