package momzzangseven.mztkbe.modules.web3.qna.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.persistence.entity.QnaAnswerProjectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface QnaAnswerProjectionJpaRepository
    extends JpaRepository<QnaAnswerProjectionEntity, Long> {

  interface QnaAutoAcceptCandidateRow {

    Long getPostId();

    Long getAnswerId();

    Long getAskerUserId();

    Long getResponderUserId();

    LocalDateTime getAnswerCreatedAt();
  }

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from QnaAnswerProjectionEntity a where a.answerId = :answerId")
  Optional<QnaAnswerProjectionEntity> findByAnswerIdForUpdate(@Param("answerId") Long answerId);

  @Query(
      value =
          """
          select
              a.post_id as postId,
              a.answer_id as answerId,
              q.asker_user_id as askerUserId,
              a.responder_user_id as responderUserId,
              a.created_at as answerCreatedAt
          from web3_qna_answers a
          join web3_qna_questions q on q.post_id = a.post_id
          join posts p on p.id = a.post_id
          join answers ans on ans.id = a.answer_id and ans.post_id = a.post_id
          where q.state = :answeredState
            and p.status = 'OPEN'
            and p.content is not null
            and btrim(p.content) <> ''
            and ans.is_accepted = false
            and ans.content is not null
            and btrim(ans.content) <> ''
            and a.accepted = false
            and a.created_at <= :cutoff
            and not exists (
                select 1
                from web3_execution_intents e_question
                where e_question.resource_type = 'QUESTION'
                  and e_question.resource_id = cast(a.post_id as varchar)
                  and e_question.status in ('AWAITING_SIGNATURE', 'SIGNED', 'PENDING_ONCHAIN')
            )
            and not exists (
                select 1
                from web3_execution_intents e_answer
                where e_answer.resource_type = 'ANSWER'
                  and e_answer.resource_id = cast(a.answer_id as varchar)
                  and e_answer.status in ('AWAITING_SIGNATURE', 'SIGNED', 'PENDING_ONCHAIN')
            )
            and a.created_at = (
                select min(a2.created_at)
                from web3_qna_answers a2
                where a2.post_id = a.post_id
                  and a2.accepted = false
            )
            and a.answer_id = (
                select min(a3.answer_id)
                from web3_qna_answers a3
                where a3.post_id = a.post_id
                  and a3.accepted = false
                  and a3.created_at = a.created_at
            )
          order by a.created_at asc, a.post_id asc
          limit 1
          for update skip locked
          """,
      nativeQuery = true)
  Optional<QnaAutoAcceptCandidateRow> claimNextAutoAcceptCandidate(
      @Param("cutoff") LocalDateTime cutoff, @Param("answeredState") int answeredState);
}
