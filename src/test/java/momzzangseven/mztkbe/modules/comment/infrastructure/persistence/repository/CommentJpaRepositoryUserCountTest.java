package momzzangseven.mztkbe.modules.comment.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;
import momzzangseven.mztkbe.modules.comment.infrastructure.persistence.entity.CommentEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@DisplayName("CommentJpaRepository user-count DataJpaTest")
class CommentJpaRepositoryUserCountTest {

  @Autowired private TestEntityManager em;

  @Autowired private CommentJpaRepository commentJpaRepository;

  @Test
  @DisplayName("countCommentsByUserIds() 는 soft deleted comment 를 제외한다")
  void countCommentsByUserIds_excludesDeletedComments() {
    LocalDateTime now = LocalDateTime.of(2026, 5, 2, 12, 0);
    em.persist(comment(100L, 10L, false, now));
    em.persist(comment(100L, 10L, true, now.plusMinutes(1)));
    em.persist(comment(101L, 11L, false, now.plusMinutes(2)));
    em.persist(comment(102L, 11L, false, now.plusMinutes(3)));
    em.flush();

    Map<Long, Long> counts =
        commentJpaRepository.countCommentsByUserIds(java.util.List.of(10L, 11L)).stream()
            .collect(
                Collectors.toMap(
                    CommentJpaRepository.UserCommentCount::getUserId,
                    CommentJpaRepository.UserCommentCount::getCommentCount));

    assertThat(counts).containsEntry(10L, 1L).containsEntry(11L, 2L);
  }

  private CommentEntity comment(
      Long postId, Long writerId, boolean deleted, LocalDateTime createdAt) {
    return CommentEntity.builder()
        .postId(postId)
        .writerId(writerId)
        .content("comment-" + writerId)
        .isDeleted(deleted)
        .createdAt(createdAt)
        .updatedAt(createdAt)
        .build();
  }
}
