package momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository;

import java.util.Optional;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.PostEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PostJpaRepository extends JpaRepository<PostEntity, Long> {

  interface QuestionRewardSourceSnapshot {
    Long getPostId();

    Long getPostOwnerUserId();

    String getPostType();

    Long getReward();

    Integer getPostSolved();

    Long getAnswerWriterUserId();

    Integer getAnswerDeleted();
  }

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update PostEntity p set p.isSolved = true"
          + " where p.id = :postId and p.type = :postType and p.isSolved = false")
  int markSolvedByIdIfType(@Param("postId") Long postId, @Param("postType") PostType postType);

  @Query(
      value =
          """
          select p.id as postId,
                 p.user_id as postOwnerUserId,
                 p.type as postType,
                 p.reward as reward,
                 case when p.is_solved then 1 else 0 end as postSolved,
                 c.writer_id as answerWriterUserId,
                 case when c.is_deleted then 1 else 0 end as answerDeleted
            from comments c
            join posts p on p.id = c.post_id
           where c.id = :answerCommentId
          """,
      nativeQuery = true)
  Optional<QuestionRewardSourceSnapshot> findQuestionRewardSourceByAnswerCommentId(
      @Param("answerCommentId") Long answerCommentId);

  @Query(
      value = "select c.post_id from comments c where c.id = :answerCommentId",
      nativeQuery = true)
  Optional<Long> findPostIdByAnswerCommentId(@Param("answerCommentId") Long answerCommentId);
}
