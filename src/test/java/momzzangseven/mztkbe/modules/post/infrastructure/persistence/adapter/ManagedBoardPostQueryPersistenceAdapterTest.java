package momzzangseven.mztkbe.modules.post.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.post.application.dto.GetManagedBoardPostsPageQuery;
import momzzangseven.mztkbe.modules.post.application.dto.ManagedBoardPostView;
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

    var page = adapter.loadPage(new GetManagedBoardPostsPageQuery(null, null, 0, 2, "CREATED_AT"));

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
            new GetManagedBoardPostsPageQuery("target", PostStatus.OPEN, 0, 10, "TYPE"));

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

    var page = adapter.loadPage(new GetManagedBoardPostsPageQuery(null, null, 0, 2, "POST_ID"));

    assertThat(page.getContent())
        .extracting(ManagedBoardPostView::postId)
        .containsExactly(secondId, firstId);
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
    PostEntity entity =
        PostEntity.builder()
            .userId(userId)
            .type(type)
            .title(title)
            .content(content)
            .reward(type == PostType.QUESTION ? 100L : 0L)
            .status(status)
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
