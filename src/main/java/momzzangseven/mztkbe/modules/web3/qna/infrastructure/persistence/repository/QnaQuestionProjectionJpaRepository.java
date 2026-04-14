package momzzangseven.mztkbe.modules.web3.qna.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.persistence.entity.QnaQuestionProjectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface QnaQuestionProjectionJpaRepository
    extends JpaRepository<QnaQuestionProjectionEntity, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select q from QnaQuestionProjectionEntity q where q.postId = :postId")
  Optional<QnaQuestionProjectionEntity> findByPostIdForUpdate(@Param("postId") Long postId);
}
