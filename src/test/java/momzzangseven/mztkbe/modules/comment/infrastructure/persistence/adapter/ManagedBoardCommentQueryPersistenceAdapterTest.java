package momzzangseven.mztkbe.modules.comment.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.comment.application.dto.GetManagedBoardCommentsQuery;
import momzzangseven.mztkbe.modules.comment.application.dto.ManagedBoardCommentSearchView;
import momzzangseven.mztkbe.modules.comment.application.dto.ManagedBoardCommentTargetType;
import momzzangseven.mztkbe.modules.comment.domain.model.CommentTargetType;
import momzzangseven.mztkbe.modules.comment.infrastructure.persistence.entity.CommentEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@DisplayName("ManagedBoardCommentQueryPersistenceAdapter DataJpaTest")
class ManagedBoardCommentQueryPersistenceAdapterTest {

  @Autowired private TestEntityManager em;

  @Test
  @DisplayName("load는 POST/ANSWER 댓글과 soft-deleted 댓글을 기본 검색 결과에 포함한다")
  void load_includesPostAnswerAndSoftDeletedComments() {
    ManagedBoardCommentQueryPersistenceAdapter adapter = adapter();
    LocalDateTime base = LocalDateTime.parse("2025-01-01T00:00:00");
    Long postCommentId =
        persistComment(CommentTargetType.POST, 21L, null, 7L, "post comment", false, base);
    Long answerCommentId =
        persistComment(
            CommentTargetType.ANSWER, 21L, 41L, 8L, "answer comment", true, base.plusMinutes(1));

    var page =
        adapter.load(new GetManagedBoardCommentsQuery(null, null, null, null, 0, 20, "CREATED_AT"));

    assertThat(page.getTotalElements()).isEqualTo(2L);
    assertThat(page.getContent())
        .extracting(ManagedBoardCommentSearchView::commentId)
        .containsExactly(answerCommentId, postCommentId);
    assertThat(page.getContent().get(0).isDeleted()).isTrue();
  }

  @Test
  @DisplayName("blank search 는 직접 query 로 들어와도 필터 없음과 동일하게 동작한다")
  void load_blankSearchBehavesLikeNoSearchFilter() {
    ManagedBoardCommentQueryPersistenceAdapter adapter = adapter();
    LocalDateTime base = LocalDateTime.parse("2025-01-01T00:00:00");
    Long firstId =
        persistComment(CommentTargetType.POST, 21L, null, 7L, "first comment", false, base);
    Long secondId =
        persistComment(
            CommentTargetType.POST, 21L, null, 7L, "second comment", false, base.plusMinutes(1));

    var page =
        adapter.load(
            new GetManagedBoardCommentsQuery("   ", null, null, null, 0, 20, "CREATED_AT"));

    assertThat(page.getTotalElements()).isEqualTo(2L);
    assertThat(page.getContent())
        .extracting(ManagedBoardCommentSearchView::commentId)
        .containsExactly(secondId, firstId);
  }

  @Test
  @DisplayName("load는 content/commentId/userId/targetType 조건을 AND 로 적용한다")
  void load_appliesContentCommentIdUserIdAndTargetTypeAsAndConditions() {
    ManagedBoardCommentQueryPersistenceAdapter adapter = adapter();
    LocalDateTime base = LocalDateTime.parse("2025-01-02T00:00:00");
    Long targetId =
        persistComment(CommentTargetType.ANSWER, 21L, 41L, 7L, "Needle comment", true, base);
    persistComment(CommentTargetType.POST, 21L, null, 7L, "Needle comment", false, base);
    persistComment(CommentTargetType.ANSWER, 21L, 42L, 8L, "Needle comment", false, base);
    persistComment(CommentTargetType.ANSWER, 21L, 43L, 7L, "other comment", false, base);

    var page =
        adapter.load(
            new GetManagedBoardCommentsQuery(
                "needle", targetId, 7L, ManagedBoardCommentTargetType.ANSWER, 0, 20, "CREATED_AT"));

    assertThat(page.getTotalElements()).isEqualTo(1L);
    ManagedBoardCommentSearchView item = page.getContent().get(0);
    assertThat(item.commentId()).isEqualTo(targetId);
    assertThat(item.postId()).isEqualTo(21L);
    assertThat(item.answerId()).isEqualTo(41L);
    assertThat(item.targetType()).isEqualTo(ManagedBoardCommentTargetType.ANSWER);
    assertThat(item.writerId()).isEqualTo(7L);
    assertThat(item.isDeleted()).isTrue();
  }

  @Test
  @DisplayName("load는 targetType POST 필터를 적용한다")
  void load_filtersPostTargetType() {
    ManagedBoardCommentQueryPersistenceAdapter adapter = adapter();
    LocalDateTime base = LocalDateTime.parse("2025-01-03T00:00:00");
    Long postCommentId =
        persistComment(CommentTargetType.POST, 21L, null, 7L, "post comment", false, base);
    persistComment(CommentTargetType.ANSWER, 21L, 41L, 7L, "answer comment", false, base);

    var page =
        adapter.load(
            new GetManagedBoardCommentsQuery(
                null, null, null, ManagedBoardCommentTargetType.POST, 0, 20, "COMMENT_ID"));

    assertThat(page.getTotalElements()).isEqualTo(1L);
    assertThat(page.getContent())
        .extracting(ManagedBoardCommentSearchView::commentId)
        .containsExactly(postCommentId);
  }

  @Test
  @DisplayName("load는 content search 의 LIKE wildcard 문자를 literal 로 처리한다")
  void load_escapesLikeWildcardsInContentSearch() {
    ManagedBoardCommentQueryPersistenceAdapter adapter = adapter();
    LocalDateTime base = LocalDateTime.parse("2025-01-04T00:00:00");
    Long percentId =
        persistComment(CommentTargetType.POST, 21L, null, 7L, "literal 100% done", false, base);
    persistComment(CommentTargetType.POST, 21L, null, 7L, "literal 1000 done", false, base);
    Long underscoreId =
        persistComment(CommentTargetType.POST, 21L, null, 7L, "code a_b literal", false, base);
    persistComment(CommentTargetType.POST, 21L, null, 7L, "code axb literal", false, base);
    Long bangId =
        persistComment(CommentTargetType.POST, 21L, null, 7L, "wow! literal", false, base);
    persistComment(CommentTargetType.POST, 21L, null, 7L, "wow literal", false, base);

    assertThat(
            adapter
                .load(
                    new GetManagedBoardCommentsQuery("100%", null, null, null, 0, 20, "CREATED_AT"))
                .getContent())
        .extracting(ManagedBoardCommentSearchView::commentId)
        .containsExactly(percentId);
    assertThat(
            adapter
                .load(
                    new GetManagedBoardCommentsQuery("a_b", null, null, null, 0, 20, "CREATED_AT"))
                .getContent())
        .extracting(ManagedBoardCommentSearchView::commentId)
        .containsExactly(underscoreId);
    assertThat(
            adapter
                .load(
                    new GetManagedBoardCommentsQuery("wow!", null, null, null, 0, 20, "CREATED_AT"))
                .getContent())
        .extracting(ManagedBoardCommentSearchView::commentId)
        .containsExactly(bangId);
  }

  @Test
  @DisplayName("load는 createdAt 이 같으면 commentId DESC 로 안정 정렬한다")
  void load_createdAtSortUsesCommentIdDescTieBreaker() {
    ManagedBoardCommentQueryPersistenceAdapter adapter = adapter();
    LocalDateTime createdAt = LocalDateTime.parse("2025-01-05T00:00:00");
    Long firstId =
        persistComment(CommentTargetType.POST, 21L, null, 7L, "first comment", false, createdAt);
    Long secondId =
        persistComment(CommentTargetType.POST, 21L, null, 7L, "second comment", false, createdAt);

    var page =
        adapter.load(new GetManagedBoardCommentsQuery(null, null, null, null, 0, 20, "CREATED_AT"));

    assertThat(page.getContent())
        .extracting(ManagedBoardCommentSearchView::commentId)
        .containsExactly(secondId, firstId);
  }

  @Test
  @DisplayName("load는 root 댓글 parentId null 과 대댓글 parentId 를 반환한다")
  void load_returnsNullableParentId() {
    ManagedBoardCommentQueryPersistenceAdapter adapter = adapter();
    LocalDateTime createdAt = LocalDateTime.parse("2025-01-06T00:00:00");
    Long rootId =
        persistComment(CommentTargetType.POST, 21L, null, 7L, "root comment", false, createdAt);
    Long replyId =
        persistComment(
            CommentTargetType.POST,
            21L,
            null,
            8L,
            "reply comment",
            false,
            createdAt.plusMinutes(1),
            rootId);

    var page =
        adapter.load(new GetManagedBoardCommentsQuery(null, null, null, null, 0, 20, "COMMENT_ID"));

    ManagedBoardCommentSearchView root =
        page.getContent().stream()
            .filter(comment -> comment.commentId().equals(rootId))
            .findFirst()
            .orElseThrow();
    ManagedBoardCommentSearchView reply =
        page.getContent().stream()
            .filter(comment -> comment.commentId().equals(replyId))
            .findFirst()
            .orElseThrow();
    assertThat(root.parentId()).isNull();
    assertThat(reply.parentId()).isEqualTo(rootId);
  }

  private ManagedBoardCommentQueryPersistenceAdapter adapter() {
    return new ManagedBoardCommentQueryPersistenceAdapter(
        new JPAQueryFactory(em.getEntityManager()));
  }

  private Long persistComment(
      CommentTargetType targetType,
      Long postId,
      Long answerId,
      Long writerId,
      String content,
      boolean isDeleted,
      LocalDateTime createdAt) {
    return persistComment(
        targetType, postId, answerId, writerId, content, isDeleted, createdAt, null);
  }

  private Long persistComment(
      CommentTargetType targetType,
      Long postId,
      Long answerId,
      Long writerId,
      String content,
      boolean isDeleted,
      LocalDateTime createdAt,
      Long parentId) {
    CommentEntity entity =
        CommentEntity.builder()
            .targetType(targetType)
            .postId(postId)
            .answerId(answerId)
            .writerId(writerId)
            .content(content)
            .isDeleted(isDeleted)
            .parent(parentId == null ? null : em.find(CommentEntity.class, parentId))
            .createdAt(createdAt)
            .updatedAt(createdAt.plusMinutes(10))
            .build();
    return em.persistFlushFind(entity).getId();
  }
}
