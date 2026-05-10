package momzzangseven.mztkbe.modules.post.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.post.application.dto.GetManagedBoardPostsPageQuery;
import momzzangseven.mztkbe.modules.post.application.dto.GetManagedBoardPostsQuery;
import momzzangseven.mztkbe.modules.post.application.dto.ManagedBoardPostView;
import momzzangseven.mztkbe.modules.post.domain.model.PostModerationStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostPublicationStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.PostEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@DisplayName("ManagedBoardPostQueryPersistenceAdapter DataJpaTest")
class ManagedBoardPostQueryPersistenceAdapterTest {

  @Autowired private TestEntityManager em;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("loadPage는 createdAt 정렬, offset, limit을 DB query로 적용한다")
  void loadPage_appliesCreatedAtSortLimitAndOffset() {
    ManagedBoardPostQueryPersistenceAdapter adapter = adapter();
    persistPost(
        1L, PostType.FREE, "old", "old content", PostStatus.OPEN, at("2025-01-01T00:00:00"));
    Long secondId =
        persistPost(
            2L,
            PostType.FREE,
            "second",
            "second content",
            PostStatus.OPEN,
            at("2025-01-02T00:00:00"));
    Long thirdId =
        persistPost(
            3L,
            PostType.QUESTION,
            "third",
            "third content",
            PostStatus.RESOLVED,
            at("2025-01-03T00:00:00"));

    var page =
        adapter.loadPage(
            new GetManagedBoardPostsPageQuery(null, null, null, null, null, 0, 2, "CREATED_AT"));

    assertThat(page.getTotalElements()).isEqualTo(3L);
    assertThat(page.getContent())
        .extracting(ManagedBoardPostView::postId)
        .containsExactly(thirdId, secondId);
  }

  @Test
  @DisplayName("loadPage는 status/search 필터와 type sort를 함께 DB query로 적용한다")
  void loadPage_appliesStatusSearchFilterAndTypeSort() {
    ManagedBoardPostQueryPersistenceAdapter adapter = adapter();
    Long freeId =
        persistPost(
            1L,
            PostType.FREE,
            null,
            "target keyword free",
            PostStatus.OPEN,
            at("2025-01-01T00:00:00"));
    Long questionId =
        persistPost(
            2L,
            PostType.QUESTION,
            "target question",
            "target keyword question",
            PostStatus.OPEN,
            at("2025-01-02T00:00:00"));
    persistPost(
        3L,
        PostType.FREE,
        null,
        "target keyword resolved",
        PostStatus.RESOLVED,
        at("2025-01-03T00:00:00"));
    persistPost(
        4L,
        PostType.QUESTION,
        "other",
        "other content",
        PostStatus.OPEN,
        at("2025-01-04T00:00:00"));

    var page =
        adapter.loadPage(
            new GetManagedBoardPostsPageQuery(
                "target", PostStatus.OPEN, null, null, null, 0, 10, "TYPE"));

    assertThat(page.getTotalElements()).isEqualTo(2L);
    assertThat(page.getContent())
        .extracting(ManagedBoardPostView::postId)
        .containsExactly(freeId, questionId);
  }

  @Test
  @DisplayName("loadPage는 postId sort를 DB query로 적용한다")
  void loadPage_appliesPostIdSort() {
    ManagedBoardPostQueryPersistenceAdapter adapter = adapter();
    Long firstId =
        persistPost(
            1L,
            PostType.FREE,
            "first",
            "first content",
            PostStatus.OPEN,
            at("2025-01-01T00:00:00"));
    Long secondId =
        persistPost(
            2L,
            PostType.FREE,
            "second",
            "second content",
            PostStatus.OPEN,
            at("2025-01-02T00:00:00"));

    var page =
        adapter.loadPage(
            new GetManagedBoardPostsPageQuery(null, null, null, null, null, 0, 2, "POST_ID"));

    assertThat(page.getContent())
        .extracting(ManagedBoardPostView::postId)
        .containsExactly(secondId, firstId);
  }

  @Test
  @DisplayName("loadPage는 type 필터를 적용한다")
  void loadPage_appliesTypeFilter() {
    ManagedBoardPostQueryPersistenceAdapter adapter = adapter();
    persistPost(
        1L, PostType.FREE, "free", "free content", PostStatus.OPEN, at("2025-01-01T00:00:00"));
    Long questionId =
        persistPost(
            2L,
            PostType.QUESTION,
            "question",
            "question content",
            PostStatus.OPEN,
            at("2025-01-02T00:00:00"));

    var page =
        adapter.loadPage(
            new GetManagedBoardPostsPageQuery(
                null, null, PostType.QUESTION, null, null, 0, 10, "CREATED_AT"));

    assertThat(page.getTotalElements()).isEqualTo(1L);
    assertThat(page.getContent())
        .extracting(ManagedBoardPostView::postId)
        .containsExactly(questionId);
  }

  @Test
  @DisplayName("loadPage는 publicationStatus 필터를 적용한다")
  void loadPage_appliesPublicationStatusFilter() {
    ManagedBoardPostQueryPersistenceAdapter adapter = adapter();
    persistPost(
        1L,
        PostType.QUESTION,
        "pending",
        "pending content",
        PostStatus.OPEN,
        PostPublicationStatus.PENDING,
        PostModerationStatus.NORMAL,
        at("2025-01-01T00:00:00"));
    Long failedId =
        persistPost(
            2L,
            PostType.QUESTION,
            "failed",
            "failed content",
            PostStatus.OPEN,
            PostPublicationStatus.FAILED,
            PostModerationStatus.NORMAL,
            at("2025-01-02T00:00:00"));
    persistPost(
        3L,
        PostType.QUESTION,
        "visible",
        "visible content",
        PostStatus.OPEN,
        PostPublicationStatus.VISIBLE,
        PostModerationStatus.NORMAL,
        at("2025-01-03T00:00:00"));

    var page =
        adapter.loadPage(
            new GetManagedBoardPostsPageQuery(
                null, null, null, PostPublicationStatus.FAILED, null, 0, 10, "CREATED_AT"));

    assertThat(page.getTotalElements()).isEqualTo(1L);
    assertThat(page.getContent())
        .extracting(ManagedBoardPostView::postId)
        .containsExactly(failedId);
  }

  @Test
  @DisplayName("loadPage는 moderationStatus 필터를 적용한다")
  void loadPage_appliesModerationStatusFilter() {
    ManagedBoardPostQueryPersistenceAdapter adapter = adapter();
    persistPost(
        1L,
        PostType.FREE,
        "normal",
        "normal content",
        PostStatus.OPEN,
        PostPublicationStatus.VISIBLE,
        PostModerationStatus.NORMAL,
        at("2025-01-01T00:00:00"));
    Long blockedId =
        persistPost(
            2L,
            PostType.FREE,
            "blocked",
            "blocked content",
            PostStatus.OPEN,
            PostPublicationStatus.VISIBLE,
            PostModerationStatus.BLOCKED,
            at("2025-01-02T00:00:00"));

    var page =
        adapter.loadPage(
            new GetManagedBoardPostsPageQuery(
                null, null, null, null, PostModerationStatus.BLOCKED, 0, 10, "CREATED_AT"));

    assertThat(page.getTotalElements()).isEqualTo(1L);
    assertThat(page.getContent())
        .extracting(ManagedBoardPostView::postId)
        .containsExactly(blockedId);
  }

  @Test
  @DisplayName("loadPage는 status, publicationStatus, moderationStatus, search를 AND 조건으로 적용한다")
  void loadPage_combinesFiltersWithAnd() {
    ManagedBoardPostQueryPersistenceAdapter adapter = adapter();
    Long matchingId =
        persistPost(
            1L,
            PostType.QUESTION,
            "target question",
            "matching content",
            PostStatus.OPEN,
            PostPublicationStatus.FAILED,
            PostModerationStatus.BLOCKED,
            at("2025-01-01T00:00:00"));
    persistPost(
        2L,
        PostType.QUESTION,
        "target resolved",
        "matching content",
        PostStatus.RESOLVED,
        PostPublicationStatus.FAILED,
        PostModerationStatus.BLOCKED,
        at("2025-01-02T00:00:00"));
    persistPost(
        3L,
        PostType.QUESTION,
        "target pending",
        "matching content",
        PostStatus.OPEN,
        PostPublicationStatus.PENDING,
        PostModerationStatus.BLOCKED,
        at("2025-01-03T00:00:00"));
    persistPost(
        4L,
        PostType.QUESTION,
        "target normal",
        "matching content",
        PostStatus.OPEN,
        PostPublicationStatus.FAILED,
        PostModerationStatus.NORMAL,
        at("2025-01-04T00:00:00"));
    persistPost(
        5L,
        PostType.QUESTION,
        "other question",
        "other content",
        PostStatus.OPEN,
        PostPublicationStatus.FAILED,
        PostModerationStatus.BLOCKED,
        at("2025-01-05T00:00:00"));

    var page =
        adapter.loadPage(
            new GetManagedBoardPostsPageQuery(
                "target",
                PostStatus.OPEN,
                null,
                PostPublicationStatus.FAILED,
                PostModerationStatus.BLOCKED,
                0,
                10,
                "CREATED_AT"));

    assertThat(page.getTotalElements()).isEqualTo(1L);
    assertThat(page.getContent())
        .extracting(ManagedBoardPostView::postId)
        .containsExactly(matchingId);
  }

  @Test
  @DisplayName("count는 load와 동일한 status, publicationStatus, moderationStatus, search 필터를 적용한다")
  void count_combinesFiltersWithAnd() {
    ManagedBoardPostQueryPersistenceAdapter adapter = adapter();
    persistPost(
        1L,
        PostType.QUESTION,
        "target question",
        "matching content",
        PostStatus.OPEN,
        PostPublicationStatus.FAILED,
        PostModerationStatus.BLOCKED,
        at("2025-01-01T00:00:00"));
    persistPost(
        2L,
        PostType.QUESTION,
        "target normal",
        "matching content",
        PostStatus.OPEN,
        PostPublicationStatus.FAILED,
        PostModerationStatus.NORMAL,
        at("2025-01-02T00:00:00"));
    persistPost(
        3L,
        PostType.FREE,
        "other",
        "other content",
        PostStatus.OPEN,
        PostPublicationStatus.FAILED,
        PostModerationStatus.BLOCKED,
        at("2025-01-03T00:00:00"));

    long count =
        adapter.count(
            new GetManagedBoardPostsQuery(
                "target",
                PostStatus.OPEN,
                PostType.QUESTION,
                PostPublicationStatus.FAILED,
                PostModerationStatus.BLOCKED));

    assertThat(count).isEqualTo(1L);
  }

  private ManagedBoardPostQueryPersistenceAdapter adapter() {
    return new ManagedBoardPostQueryPersistenceAdapter(new JPAQueryFactory(em.getEntityManager()));
  }

  private Long persistPost(
      Long userId,
      PostType type,
      String title,
      String content,
      PostStatus status,
      LocalDateTime createdAt) {
    return persistPost(
        userId,
        type,
        title,
        content,
        status,
        PostPublicationStatus.VISIBLE,
        PostModerationStatus.NORMAL,
        createdAt);
  }

  private Long persistPost(
      Long userId,
      PostType type,
      String title,
      String content,
      PostStatus status,
      PostPublicationStatus publicationStatus,
      PostModerationStatus moderationStatus,
      LocalDateTime createdAt) {
    PostEntity entity =
        PostEntity.builder()
            .userId(userId)
            .type(type)
            .title(title)
            .content(content)
            .reward(type == PostType.QUESTION ? 100L : 0L)
            .status(status)
            .publicationStatus(publicationStatus)
            .moderationStatus(moderationStatus)
            .build();
    ReflectionTestUtils.setField(entity, "createdAt", createdAt);
    ReflectionTestUtils.setField(entity, "updatedAt", createdAt);
    PostEntity saved = em.persistFlushFind(entity);
    jdbcTemplate.update(
        "UPDATE posts SET created_at = ?, updated_at = ? WHERE id = ?",
        createdAt,
        createdAt,
        saved.getId());
    em.clear();
    return saved.getId();
  }

  private LocalDateTime at(String value) {
    return LocalDateTime.parse(value);
  }
}
