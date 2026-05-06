package momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.repository;

import java.util.Collection;
import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.entity.ClassTagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

  @Query("select ct.tagId from ClassTagEntity ct where ct.classId = :classId")
  List<Long> findTagIdsByClassId(@Param("classId") Long classId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("delete from ClassTagEntity ct where ct.classId = :classId and ct.tagId in :tagIds")
  void deleteByClassIdAndTagIdIn(
      @Param("classId") Long classId, @Param("tagIds") Collection<Long> tagIds);
}
