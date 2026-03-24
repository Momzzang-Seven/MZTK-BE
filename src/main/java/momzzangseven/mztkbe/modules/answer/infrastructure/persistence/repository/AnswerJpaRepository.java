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

  void deleteAllByPostId(Long postId);
}
