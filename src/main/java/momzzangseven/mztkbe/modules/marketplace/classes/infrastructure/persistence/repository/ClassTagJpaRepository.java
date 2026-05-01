package momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.repository;

import momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.entity.ClassTagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link ClassTagEntity}.
 *
 * <p>Used exclusively by {@code ClassTagAdapter} to manage class-tag associations. Mirrors the
 * {@code PostTagJpaRepository} pattern.
 */
@Repository
public interface ClassTagJpaRepository extends JpaRepository<ClassTagEntity, Long> {

  /**
   * Delete all tag associations for a given class.
   *
   * @param classId class ID
   */
  void deleteByClassId(Long classId);
}
