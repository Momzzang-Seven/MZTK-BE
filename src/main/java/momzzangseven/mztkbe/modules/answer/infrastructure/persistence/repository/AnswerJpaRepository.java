package momzzangseven.mztkbe.modules.answer.infrastructure.persistence.repository;

import java.util.List;
import momzzangseven.mztkbe.modules.answer.infrastructure.persistence.entity.AnswerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AnswerJpaRepository extends JpaRepository<AnswerEntity, Long> {

  List<AnswerEntity> findByPostIdOrderByIsAcceptedDescCreatedAtAsc(Long postId);

  @Query("select a.id from AnswerEntity a where a.postId = :postId order by a.id")
  List<Long> findIdsByPostId(@Param("postId") Long postId);

  @Query(
      value =
          "SELECT a.id FROM answers a "
              + "LEFT JOIN posts p ON p.id = a.post_id "
              + "WHERE p.id IS NULL "
              + "ORDER BY a.id "
              + "LIMIT :batchSize",
      nativeQuery = true)
  List<Long> findOrphanAnswerIds(@Param("batchSize") int batchSize);

  void deleteAllByPostId(Long postId);
}
