package momzzangseven.mztkbe.modules.answer.infrastructure.persistence.repository;

import java.util.List;
import momzzangseven.mztkbe.modules.answer.infrastructure.persistence.entity.AnswerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnswerJpaRepository extends JpaRepository<AnswerEntity, Long> {

  List<AnswerEntity> findByPostIdOrderByIsAcceptedDescCreatedAtAsc(Long postId);

  void deleteAllByPostId(Long postId);
}
