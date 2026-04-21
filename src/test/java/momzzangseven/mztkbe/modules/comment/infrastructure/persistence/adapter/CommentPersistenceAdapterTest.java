package momzzangseven.mztkbe.modules.comment.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import momzzangseven.mztkbe.modules.comment.domain.model.Comment;
import momzzangseven.mztkbe.modules.comment.infrastructure.persistence.entity.CommentEntity;
import momzzangseven.mztkbe.modules.comment.infrastructure.persistence.repository.CommentJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommentPersistenceAdapter unit test")
class CommentPersistenceAdapterTest {

  @Mock private CommentJpaRepository commentRepository;

  @InjectMocks private CommentPersistenceAdapter adapter;

  @Test
  @DisplayName("saveComment() resolves parent reference when parentId exists")
  void saveComment_withParentId_resolvesParentReference() {
    LocalDateTime now = LocalDateTime.now();
    Comment input =
        Comment.builder()
            .id(null)
            .postId(100L)
            .writerId(200L)
            .parentId(10L)
            .content("reply")
            .isDeleted(false)
            .createdAt(now)
            .updatedAt(now)
            .build();

    CommentEntity parent =
        CommentEntity.builder()
            .id(10L)
            .postId(100L)
            .writerId(999L)
            .content("parent")
            .isDeleted(false)
            .createdAt(now)
            .updatedAt(now)
            .build();

    given(commentRepository.getReferenceById(10L)).willReturn(parent);
    given(commentRepository.save(any(CommentEntity.class)))
        .willAnswer(
            invocation -> {
              CommentEntity toSave = invocation.getArgument(0);
              return CommentEntity.builder()
                  .id(55L)
                  .postId(toSave.getPostId())
                  .writerId(toSave.getWriterId())
                  .content(toSave.getContent())
                  .isDeleted(toSave.isDeleted())
                  .parent(toSave.getParent())
                  .createdAt(toSave.getCreatedAt())
                  .updatedAt(toSave.getUpdatedAt())
                  .build();
            });

    Comment saved = adapter.saveComment(input);

    assertThat(saved.getId()).isEqualTo(55L);
    assertThat(saved.getPostId()).isEqualTo(100L);
    assertThat(saved.getWriterId()).isEqualTo(200L);
    assertThat(saved.getParentId()).isEqualTo(10L);
    assertThat(saved.getContent()).isEqualTo("reply");

    verify(commentRepository).getReferenceById(10L);
    verify(commentRepository).save(any(CommentEntity.class));
  }

  @Test
  @DisplayName("loadComment() maps entity to domain")
  void loadComment_mapsEntityToDomain() {
    LocalDateTime now = LocalDateTime.now();
    CommentEntity entity =
        CommentEntity.builder()
            .id(1L)
            .postId(100L)
            .writerId(200L)
            .content("hello")
            .isDeleted(false)
            .createdAt(now)
            .updatedAt(now)
            .build();

    given(commentRepository.findById(1L)).willReturn(Optional.of(entity));

    Optional<Comment> loaded = adapter.loadComment(1L);

    assertThat(loaded).isPresent();
    assertThat(loaded.orElseThrow().getId()).isEqualTo(1L);
    assertThat(loaded.orElseThrow().getContent()).isEqualTo("hello");
  }

  @Test
  @DisplayName("countDirectRepliesByParentIds() maps repository projections")
  void countDirectRepliesByParentIds_mapsProjection() {
    CommentJpaRepository.DirectReplyCount first = directReplyCount(10L, 2L);
    CommentJpaRepository.DirectReplyCount second = directReplyCount(11L, 1L);
    given(commentRepository.countDirectRepliesByParentIds(List.of(10L, 11L)))
        .willReturn(List.of(first, second));

    Map<Long, Long> result = adapter.countDirectRepliesByParentIds(List.of(10L, 11L));

    assertThat(result).containsEntry(10L, 2L).containsEntry(11L, 1L);
  }

  @Test
  @DisplayName("countDirectRepliesByParentIds() no-ops for null or empty list")
  void countDirectRepliesByParentIds_nullOrEmpty_returnsEmptyMap() {
    assertThat(adapter.countDirectRepliesByParentIds(null)).isEmpty();
    assertThat(adapter.countDirectRepliesByParentIds(List.of())).isEmpty();

    verifyNoInteractions(commentRepository);
  }

  @Test
  @DisplayName("deleteAllById() no-ops for null or empty list")
  void deleteAllById_nullOrEmpty_noOp() {
    adapter.deleteAllById(null);
    adapter.deleteAllById(List.of());

    verifyNoInteractions(commentRepository);
  }

  @Test
  @DisplayName("deleteAllById() deletes children first then parents")
  void deleteAllById_deletesChildrenThenParents() {
    List<Long> ids = List.of(1L, 2L, 3L);

    adapter.deleteAllById(ids);

    InOrder inOrder = inOrder(commentRepository);
    inOrder.verify(commentRepository).deleteByParentIdIn(ids);
    inOrder.verify(commentRepository).deleteAllByIdInBatch(ids);
  }

  @Test
  @DisplayName("loadCommentIdsForDeletion() uses first page with configured batch size")
  void loadCommentIdsForDeletion_usesFirstPageWithBatchSize() {
    LocalDateTime cutoff = LocalDateTime.of(2026, 1, 1, 0, 0);
    given(
            commentRepository.findIdsByIsDeletedTrueAndUpdatedAtBefore(
                eq(cutoff), any(Pageable.class)))
        .willReturn(List.of(7L, 8L));

    List<Long> ids = adapter.loadCommentIdsForDeletion(cutoff, 2);

    assertThat(ids).containsExactly(7L, 8L);

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(commentRepository)
        .findIdsByIsDeletedTrueAndUpdatedAtBefore(eq(cutoff), pageableCaptor.capture());
    Pageable pageable = pageableCaptor.getValue();
    assertThat(pageable.getPageNumber()).isZero();
    assertThat(pageable.getPageSize()).isEqualTo(2);
  }

  private CommentJpaRepository.DirectReplyCount directReplyCount(Long parentId, Long replyCount) {
    return new CommentJpaRepository.DirectReplyCount() {
      @Override
      public Long getParentId() {
        return parentId;
      }

      @Override
      public Long getReplyCount() {
        return replyCount;
      }
    };
  }
}
