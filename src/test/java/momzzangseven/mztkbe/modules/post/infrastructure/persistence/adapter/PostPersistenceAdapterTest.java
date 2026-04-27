package momzzangseven.mztkbe.modules.post.infrastructure.persistence.adapter;

import static momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.QPostEntity.postEntity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.global.pagination.CursorCodec;
import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import momzzangseven.mztkbe.global.pagination.CursorScope;
import momzzangseven.mztkbe.global.pagination.KeysetCursor;
import momzzangseven.mztkbe.modules.post.application.dto.PostCursorSearchCondition;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.PostEntity;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository.PostJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostPersistenceAdapter unit test")
class PostPersistenceAdapterTest {

  @Mock private PostJpaRepository postJpaRepository;
  @Mock private JPAQueryFactory queryFactory;
  @Mock private JPAQuery<PostEntity> jpaQuery;

  @InjectMocks private PostPersistenceAdapter postPersistenceAdapter;

  @Test
  @DisplayName("savePost maps domain to entity and returns mapped domain")
  void savePostMapsDomain() {
    Post post =
        Post.builder()
            .id(null)
            .userId(3L)
            .type(PostType.FREE)
            .title("title")
            .content("content")
            .reward(0L)
            .status(PostStatus.OPEN)
            .build();

    PostEntity savedEntity =
        PostEntity.builder()
            .id(100L)
            .userId(3L)
            .type(PostType.FREE)
            .title("title")
            .content("content")
            .reward(0L)
            .status(PostStatus.OPEN)
            .build();

    when(postJpaRepository.save(any(PostEntity.class))).thenReturn(savedEntity);

    Post result = postPersistenceAdapter.savePost(post);

    ArgumentCaptor<PostEntity> captor = ArgumentCaptor.forClass(PostEntity.class);
    verify(postJpaRepository).save(captor.capture());

    PostEntity mapped = captor.getValue();
    assertThat(mapped.getUserId()).isEqualTo(3L);
    assertThat(mapped.getType()).isEqualTo(PostType.FREE);
    assertThat(mapped.getTitle()).isEqualTo("title");
    assertThat(mapped.getContent()).isEqualTo("content");

    assertThat(result.getId()).isEqualTo(100L);
    assertThat(result.getUserId()).isEqualTo(3L);
    assertThat(result.getType()).isEqualTo(PostType.FREE);
    assertThat(result.getTags()).isEmpty();
  }

  @Test
  @DisplayName("loadPost returns empty when missing (write-tx path: findByIdForUpdate)")
  void loadPostReturnsEmptyWhenMissing() {
    // 단위 테스트 환경에서는 Spring 트랜잭션 컨텍스트가 없으므로
    // isCurrentTransactionReadOnly() == false → findByIdForUpdate 경로 실행
    when(postJpaRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

    Optional<Post> result = postPersistenceAdapter.loadPost(999L);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("loadPost maps found entity with empty tags (write-tx path: findByIdForUpdate)")
  void loadPostMapsFoundEntity() {
    PostEntity entity =
        PostEntity.builder()
            .id(10L)
            .userId(4L)
            .type(PostType.QUESTION)
            .title("question")
            .content("body")
            .reward(50L)
            .status(PostStatus.OPEN)
            .build();

    // 단위 테스트 환경에서는 Spring 트랜잭션 컨텍스트가 없으므로
    // isCurrentTransactionReadOnly() == false → findByIdForUpdate 경로 실행
    when(postJpaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(entity));

    Optional<Post> result = postPersistenceAdapter.loadPost(10L);

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().getId()).isEqualTo(10L);
    assertThat(result.orElseThrow().getType()).isEqualTo(PostType.QUESTION);
    assertThat(result.orElseThrow().getTags()).isEmpty();
  }

  @Test
  @DisplayName("loadPost fails fast for inconsistent persisted question state")
  void loadPostFailsForInconsistentPersistedState() {
    PostEntity entity =
        PostEntity.builder()
            .id(11L)
            .userId(4L)
            .type(PostType.QUESTION)
            .title("question")
            .content("body")
            .reward(50L)
            .status(PostStatus.RESOLVED)
            .build();

    when(postJpaRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(entity));

    assertThatThrownBy(() -> postPersistenceAdapter.loadPost(11L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("acceptedAnswerId");
  }

  @Test
  @DisplayName("loadPostForUpdate delegates to repository lock query")
  void loadPostForUpdateDelegates() {
    PostEntity entity =
        PostEntity.builder()
            .id(15L)
            .userId(4L)
            .type(PostType.FREE)
            .title(null)
            .content("body")
            .reward(0L)
            .status(PostStatus.OPEN)
            .build();

    when(postJpaRepository.findByIdForUpdate(15L)).thenReturn(Optional.of(entity));

    Optional<Post> result = postPersistenceAdapter.loadPostForUpdate(15L);

    assertThat(result).isPresent();
    verify(postJpaRepository).findByIdForUpdate(15L);
  }

  @Test
  @DisplayName("deletePost removes row and flushes")
  void deletePostDelegatesToRepository() {
    Post post =
        Post.builder()
            .id(77L)
            .userId(1L)
            .type(PostType.FREE)
            .title("t")
            .content("c")
            .reward(0L)
            .status(PostStatus.OPEN)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

    postPersistenceAdapter.deletePost(post);

    verify(postJpaRepository).deleteById(77L);
    verify(postJpaRepository).flush();
  }

  @Test
  @DisplayName("existsPost delegates to repository")
  void existsPostDelegates() {
    when(postJpaRepository.existsById(5L)).thenReturn(true);

    boolean exists = postPersistenceAdapter.existsPost(5L);

    assertThat(exists).isTrue();
    verify(postJpaRepository).existsById(5L);
  }

  @Test
  @DisplayName("markQuestionPostSolved performs status-only conditional resolve update")
  void markQuestionPostSolvedDelegates() {
    when(postJpaRepository.markResolvedByIdIfType(
            9L, PostType.QUESTION, PostStatus.OPEN, PostStatus.RESOLVED))
        .thenReturn(1);

    int updated = postPersistenceAdapter.markQuestionPostSolved(9L);

    assertThat(updated).isEqualTo(1);
    verify(postJpaRepository)
        .markResolvedByIdIfType(9L, PostType.QUESTION, PostStatus.OPEN, PostStatus.RESOLVED);
  }

  @Test
  @DisplayName("cursor tag first page delegates to native query with normalized FREE search")
  void findPostsByCursorConditionWithTagFirstPageDelegates() {
    PostCursorSearchCondition condition =
        PostCursorSearchCondition.of(PostType.FREE, "Java", "ignored", null, 10);
    when(postJpaRepository.findPostsByConditionWithTagFirstPageNative("FREE", null, 7L, 11))
        .thenReturn(List.of());

    List<Post> result = postPersistenceAdapter.findPostsByCursorCondition(condition, 7L);

    assertThat(result).isEmpty();
    verify(postJpaRepository).findPostsByConditionWithTagFirstPageNative("FREE", null, 7L, 11);
  }

  @Test
  @DisplayName("cursor tag next page delegates to native query with keyset cursor")
  void findPostsByCursorConditionWithTagAfterCursorDelegates() {
    LocalDateTime cursorCreatedAt = LocalDateTime.of(2026, 4, 24, 12, 0);
    String scope = CursorScope.posts("QUESTION", "java", "form");
    String cursor = CursorCodec.encode(new KeysetCursor(cursorCreatedAt, 15L, scope));
    PostCursorSearchCondition condition =
        PostCursorSearchCondition.of(PostType.QUESTION, "Java", "FoRm", cursor, 10);
    when(postJpaRepository.findPostsByConditionWithTagAfterCursorNative(
            "QUESTION", "form", 7L, cursorCreatedAt, 15L, 11))
        .thenReturn(List.of());

    List<Post> result = postPersistenceAdapter.findPostsByCursorCondition(condition, 7L);

    assertThat(result).isEmpty();
    verify(postJpaRepository)
        .findPostsByConditionWithTagAfterCursorNative(
            "QUESTION", "form", 7L, cursorCreatedAt, 15L, 11);
  }

  @Test
  @DisplayName("my posts first page without search delegates to authored native query")
  void findPostsByAuthorCursorFirstPageDelegates() {
    CursorPageRequest pageRequest =
        CursorPageRequest.of(
            null, 10, 10, 50, CursorScope.myPosts(7L, PostType.QUESTION.name(), null, null));
    when(postJpaRepository.findPostsByAuthorFirstPageNative(7L, "QUESTION", 11))
        .thenReturn(List.of());

    List<Post> result =
        postPersistenceAdapter.findPostsByAuthorCursor(
            7L, PostType.QUESTION, null, null, pageRequest);

    assertThat(result).isEmpty();
    verify(postJpaRepository).findPostsByAuthorFirstPageNative(7L, "QUESTION", 11);
  }

  @Test
  @DisplayName("my posts next page delegates to authored native query with keyset cursor")
  void findPostsByAuthorCursorAfterCursorDelegates() {
    LocalDateTime cursorCreatedAt = LocalDateTime.of(2026, 4, 27, 12, 0);
    String scope = CursorScope.myPosts(7L, PostType.FREE.name(), null, null);
    String cursor = CursorCodec.encode(new KeysetCursor(cursorCreatedAt, 15L, scope));
    CursorPageRequest pageRequest = CursorPageRequest.of(cursor, 10, 10, 50, scope);
    when(postJpaRepository.findPostsByAuthorAfterCursorNative(7L, "FREE", cursorCreatedAt, 15L, 11))
        .thenReturn(List.of());

    List<Post> result =
        postPersistenceAdapter.findPostsByAuthorCursor(7L, PostType.FREE, null, null, pageRequest);

    assertThat(result).isEmpty();
    verify(postJpaRepository)
        .findPostsByAuthorAfterCursorNative(7L, "FREE", cursorCreatedAt, 15L, 11);
  }

  @Test
  @DisplayName("my posts with tag and without search delegates to authored tag native query")
  void findPostsByAuthorCursorWithTagDelegates() {
    CursorPageRequest pageRequest =
        CursorPageRequest.of(
            null, 10, 10, 50, CursorScope.myPosts(7L, PostType.QUESTION.name(), "squat", null));
    when(postJpaRepository.findPostsByAuthorWithTagFirstPageNative(7L, "QUESTION", 99L, 11))
        .thenReturn(List.of());

    List<Post> result =
        postPersistenceAdapter.findPostsByAuthorCursor(
            7L, PostType.QUESTION, 99L, null, pageRequest);

    assertThat(result).isEmpty();
    verify(postJpaRepository).findPostsByAuthorWithTagFirstPageNative(7L, "QUESTION", 99L, 11);
  }

  @Test
  @DisplayName("my posts search uses QueryDSL instead of authored native query")
  void findPostsByAuthorCursorWithSearchUsesQueryDsl() {
    CursorPageRequest pageRequest =
        CursorPageRequest.of(
            null, 10, 10, 50, CursorScope.myPosts(7L, PostType.QUESTION.name(), "squat", "100%_!"));
    when(queryFactory.selectFrom(postEntity)).thenReturn(jpaQuery);
    when(jpaQuery.where(any(Predicate[].class))).thenReturn(jpaQuery);
    when(jpaQuery.orderBy(any(OrderSpecifier[].class))).thenReturn(jpaQuery);
    when(jpaQuery.limit(11L)).thenReturn(jpaQuery);
    when(jpaQuery.fetch()).thenReturn(List.of());

    List<Post> result =
        postPersistenceAdapter.findPostsByAuthorCursor(
            7L, PostType.QUESTION, 99L, "100%_!", pageRequest);

    assertThat(result).isEmpty();
    verify(queryFactory).selectFrom(postEntity);
    verify(jpaQuery).where(any(Predicate[].class));
    verify(jpaQuery).limit(11L);
    verifyNoInteractions(postJpaRepository);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // findPostsByCondition() private helper branches
  // QueryDSL 표현식 생성 메서드는 DB 없이 순수 Java 객체를 생성하므로
  // 리플렉션으로 private 메서드를 직접 호출하여 분기를 커버합니다.
  // ─────────────────────────────────────────────────────────────────────────
  @Nested
  @DisplayName("eqType() - 타입 필터 분기")
  class EqTypeBranch {

    private java.lang.reflect.Method eqType;

    @BeforeEach
    void setUp() throws Exception {
      eqType = PostPersistenceAdapter.class.getDeclaredMethod("eqType", PostType.class);
      eqType.setAccessible(true);
    }

    @Test
    @DisplayName("type=null → null 반환 (필터 없음)")
    void nullType_returnsNull() throws Exception {
      Object result = eqType.invoke(postPersistenceAdapter, (PostType) null);
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("type=FREE → BooleanExpression 반환 (타입 필터 적용)")
    void nonNullType_returnsBooleanExpression() throws Exception {
      Object result = eqType.invoke(postPersistenceAdapter, PostType.FREE);
      assertThat(result).isNotNull();
    }
  }

  @Nested
  @DisplayName("containsSearch() - 검색어 필터 분기")
  class ContainsSearchBranch {

    private java.lang.reflect.Method containsSearch;

    @BeforeEach
    void setUp() throws Exception {
      containsSearch =
          PostPersistenceAdapter.class.getDeclaredMethod(
              "containsSearch", PostType.class, String.class);
      containsSearch.setAccessible(true);
    }

    @Test
    @DisplayName("search=null → null 반환 (검색어 없음)")
    void nullSearch_returnsNull() throws Exception {
      Object result = containsSearch.invoke(postPersistenceAdapter, PostType.FREE, null);
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("search=빈 문자열 → null 반환 (검색어 없음)")
    void blankSearch_returnsNull() throws Exception {
      Object result = containsSearch.invoke(postPersistenceAdapter, PostType.QUESTION, "   ");
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("search=text, type=FREE → null 반환 (자유게시판 제목 검색 생략)")
    void searchWithFreeType_returnsNull() throws Exception {
      Object result = containsSearch.invoke(postPersistenceAdapter, PostType.FREE, "spring");
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("search=text, type=QUESTION → 제목 포함 표현식 반환")
    void searchWithNonFreeType_returnsBooleanExpression() throws Exception {
      Object result = containsSearch.invoke(postPersistenceAdapter, PostType.QUESTION, "spring");
      assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("search=text, type=null → 제목 포함 표현식 반환")
    void searchWithNullType_returnsBooleanExpression() throws Exception {
      Object result = containsSearch.invoke(postPersistenceAdapter, null, "spring");
      assertThat(result).isNotNull();
    }
  }

  @Nested
  @DisplayName("containsCursorSearch() - v2 커서 검색어 필터 분기")
  class ContainsCursorSearchBranch {

    private java.lang.reflect.Method containsCursorSearch;

    @BeforeEach
    void setUp() throws Exception {
      containsCursorSearch =
          PostPersistenceAdapter.class.getDeclaredMethod(
              "containsCursorSearch", PostType.class, String.class);
      containsCursorSearch.setAccessible(true);
    }

    @Test
    @DisplayName("search=text, type=QUESTION → 대소문자 무시 제목 포함 표현식 반환")
    void searchWithNonFreeType_returnsIgnoreCaseBooleanExpression() throws Exception {
      Object result =
          containsCursorSearch.invoke(postPersistenceAdapter, PostType.QUESTION, "form");

      assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("search=text, type=FREE → null 반환")
    void searchWithFreeType_returnsNull() throws Exception {
      Object result = containsCursorSearch.invoke(postPersistenceAdapter, PostType.FREE, "form");

      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("filterByTagIds() - 태그 ID 필터 분기")
  class FilterByTagIdsBranch {

    private java.lang.reflect.Method filterByTagIds;

    @BeforeEach
    void setUp() throws Exception {
      filterByTagIds = PostPersistenceAdapter.class.getDeclaredMethod("filterByTagIds", List.class);
      filterByTagIds.setAccessible(true);
    }

    @Test
    @DisplayName("postIds=null → null 반환 (태그 필터 없음)")
    void nullPostIds_returnsNull() throws Exception {
      Object result = filterByTagIds.invoke(postPersistenceAdapter, (Object) null);
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("postIds=[] → id=-1 표현식 반환 (결과 0건 보장)")
    void emptyPostIds_returnsAlwaysFalseExpression() throws Exception {
      Object result = filterByTagIds.invoke(postPersistenceAdapter, List.of());
      assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("postIds=[1L,2L] → IN 표현식 반환")
    void nonEmptyPostIds_returnsInExpression() throws Exception {
      Object result = filterByTagIds.invoke(postPersistenceAdapter, List.of(1L, 2L));
      assertThat(result).isNotNull();
    }
  }

  @Nested
  @DisplayName("fetchLimit() - hasNext probe query size")
  class FetchLimitBranch {

    private java.lang.reflect.Method fetchLimit;

    @BeforeEach
    void setUp() throws Exception {
      fetchLimit =
          PostPersistenceAdapter.class.getDeclaredMethod(
              "fetchLimit",
              momzzangseven.mztkbe.modules.post.application.dto.PostSearchCondition.class);
      fetchLimit.setAccessible(true);
    }

    @Test
    @DisplayName("returns requested size plus one probe row")
    void returnsRequestedSizePlusOne() throws Exception {
      Object result =
          fetchLimit.invoke(
              postPersistenceAdapter,
              new momzzangseven.mztkbe.modules.post.application.dto.PostSearchCondition(
                  PostType.FREE, null, null, 0, 10));
      assertThat(result).isEqualTo(11L);
    }
  }
}
