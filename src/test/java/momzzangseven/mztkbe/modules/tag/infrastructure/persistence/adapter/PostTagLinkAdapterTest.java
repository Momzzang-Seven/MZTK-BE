package momzzangseven.mztkbe.modules.tag.infrastructure.persistence.adapter;

import static momzzangseven.mztkbe.modules.tag.infrastructure.persistence.entity.QPostTagEntity.postTagEntity;
import static momzzangseven.mztkbe.modules.tag.infrastructure.persistence.entity.QTagEntity.tagEntity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Predicate;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.tag.application.port.in.ManageTagsUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostTagLinkAdapter unit test")
class PostTagLinkAdapterTest {

  @Mock private ManageTagsUseCase manageTagsUseCase;
  @Mock private JPAQueryFactory queryFactory;
  @Mock private JPAQuery<Long> postIdQuery;
  @Mock private JPAQuery<String> tagNameQuery;
  @Mock private JPAQuery<Tuple> tupleQuery;

  @InjectMocks private PostTagLinkAdapter postTagLinkAdapter;

  @Test
  @DisplayName("linkTagsToPost delegates to tag use case")
  void linkTagsToPostDelegates() {
    postTagLinkAdapter.linkTagsToPost(10L, List.of("java"));

    verify(manageTagsUseCase).linkTagsToPost(10L, List.of("java"));
  }

  @Test
  @DisplayName("updateTags delegates to tag use case")
  void updateTagsDelegates() {
    postTagLinkAdapter.updateTags(11L, List.of("spring"));

    verify(manageTagsUseCase).updateTags(11L, List.of("spring"));
  }

  @Test
  @DisplayName("findPostIdsByTagName trims and lowercases query")
  void findPostIdsByTagNameNormalizesInput() {
    when(queryFactory.select(postTagEntity.postId)).thenReturn(postIdQuery);
    when(postIdQuery.from(tagEntity)).thenReturn(postIdQuery);
    when(postIdQuery.join(postTagEntity)).thenReturn(postIdQuery);
    when(postIdQuery.on(any(Predicate.class))).thenReturn(postIdQuery);
    when(postIdQuery.where(any(Predicate.class))).thenReturn(postIdQuery);
    when(postIdQuery.fetch()).thenReturn(List.of(1L, 2L));

    List<Long> result = postTagLinkAdapter.findPostIdsByTagName("  JAVA ");

    assertThat(result).containsExactly(1L, 2L);
    verify(postIdQuery).where(any(Predicate.class));
  }

  @Test
  @DisplayName("findTagNamesByPostId returns names from query")
  void findTagNamesByPostIdReturnsNames() {
    when(queryFactory.select(tagEntity.name)).thenReturn(tagNameQuery);
    when(tagNameQuery.from(postTagEntity)).thenReturn(tagNameQuery);
    when(tagNameQuery.join(tagEntity)).thenReturn(tagNameQuery);
    when(tagNameQuery.on(any(Predicate.class))).thenReturn(tagNameQuery);
    when(tagNameQuery.where(any(Predicate.class))).thenReturn(tagNameQuery);
    when(tagNameQuery.fetch()).thenReturn(List.of("java", "spring"));

    List<String> names = postTagLinkAdapter.findTagNamesByPostId(3L);

    assertThat(names).containsExactly("java", "spring");
    verify(queryFactory).select(tagEntity.name);
    verify(tagNameQuery).fetch();
  }

  @Test
  @DisplayName("findTagsByPostIdsIn groups tuples by post ID")
  void findTagsByPostIdsInGroupsResults() {
    List<Long> postIds = List.of(1L, 2L);

    Tuple row1 = mock(Tuple.class);
    Tuple row2 = mock(Tuple.class);
    Tuple row3 = mock(Tuple.class);

    when(row1.get(postTagEntity.postId)).thenReturn(1L);
    when(row1.get(tagEntity.name)).thenReturn("java");
    when(row2.get(postTagEntity.postId)).thenReturn(1L);
    when(row2.get(tagEntity.name)).thenReturn("spring");
    when(row3.get(postTagEntity.postId)).thenReturn(2L);
    when(row3.get(tagEntity.name)).thenReturn("kotlin");

    when(queryFactory.select(postTagEntity.postId, tagEntity.name)).thenReturn(tupleQuery);
    when(tupleQuery.from(postTagEntity)).thenReturn(tupleQuery);
    when(tupleQuery.join(tagEntity)).thenReturn(tupleQuery);
    when(tupleQuery.on(any(Predicate.class))).thenReturn(tupleQuery);
    when(tupleQuery.where(any(Predicate.class))).thenReturn(tupleQuery);
    when(tupleQuery.fetch()).thenReturn(List.of(row1, row2, row3));

    Map<Long, List<String>> result = postTagLinkAdapter.findTagsByPostIdsIn(postIds);

    assertThat(result).containsOnlyKeys(1L, 2L);
    assertThat(result.get(1L)).containsExactly("java", "spring");
    assertThat(result.get(2L)).containsExactly("kotlin");
  }
}
