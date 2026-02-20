package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntentStatus;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity.QuestionRewardIntentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionRewardIntentJpaRepository
    extends JpaRepository<QuestionRewardIntentEntity, Long> {

  Optional<QuestionRewardIntentEntity> findByPostId(Long postId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from QuestionRewardIntentEntity i where i.postId = :postId")
  Optional<QuestionRewardIntentEntity> findForUpdateByPostId(@Param("postId") Long postId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update QuestionRewardIntentEntity i"
          + " set i.status = :toStatus"
          + " where i.postId = :postId and i.status in :fromStatuses")
  int updateStatusIfCurrentIn(
      @Param("postId") Long postId,
      @Param("toStatus") QuestionRewardIntentStatus toStatus,
      @Param("fromStatuses") java.util.Collection<QuestionRewardIntentStatus> fromStatuses);
}
