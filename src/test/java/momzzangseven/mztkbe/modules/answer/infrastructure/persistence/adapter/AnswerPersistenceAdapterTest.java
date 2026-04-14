package momzzangseven.mztkbe.modules.answer.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.answer.domain.model.Answer;
import momzzangseven.mztkbe.modules.answer.infrastructure.persistence.entity.AnswerEntity;
import momzzangseven.mztkbe.modules.answer.infrastructure.persistence.repository.AnswerJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnswerPersistenceAdapter")
class AnswerPersistenceAdapterTest {

  @Mock private AnswerJpaRepository answerJpaRepository;

  @InjectMocks private AnswerPersistenceAdapter answerPersistenceAdapter;

  @Nested
  @DisplayName("Success cases")
  class SuccessCases {

    @Test
    @DisplayName("saveAnswer() converts a domain object to an entity and back")
    void saveAnswer_convertsDomainAndReturnsSavedDomain() {
      Answer answer = buildAnswer(null, 10L, 20L, "answer content", false);
      AnswerEntity savedEntity = buildEntity(99L, 10L, 20L, "answer content", false);
      given(answerJpaRepository.save(any(AnswerEntity.class))).willReturn(savedEntity);

      Answer result = answerPersistenceAdapter.saveAnswer(answer);

      assertThat(result.getId()).isEqualTo(99L);
      assertThat(result.getPostId()).isEqualTo(10L);
      assertThat(result.getUserId()).isEqualTo(20L);
      ArgumentCaptor<AnswerEntity> entityCaptor = ArgumentCaptor.forClass(AnswerEntity.class);
      verify(answerJpaRepository).save(entityCaptor.capture());
      assertThat(entityCaptor.getValue().getPostId()).isEqualTo(10L);
      assertThat(entityCaptor.getValue().getContent()).isEqualTo("answer content");
    }

    @Test
    @DisplayName("loadAnswersByPostId() maps entities to domain objects in repository order")
    void loadAnswersByPostId_mapsEntitiesToDomains() {
      Long postId = 10L;
      List<AnswerEntity> entities =
          List.of(
              buildEntity(1L, 10L, 20L, "accepted", true),
              buildEntity(2L, 10L, 21L, "regular", false));
      given(answerJpaRepository.findByPostIdOrderByIsAcceptedDescCreatedAtAsc(postId))
          .willReturn(entities);

      List<Answer> result = answerPersistenceAdapter.loadAnswersByPostId(postId);

      assertThat(result).hasSize(2);
      assertThat(result.get(0).getIsAccepted()).isTrue();
      assertThat(result.get(0).getId()).isEqualTo(1L);
      assertThat(result.get(1).getContent()).isEqualTo("regular");
    }

    @Test
    @DisplayName("loadAnswersByPostId() returns an empty list when there are no answers")
    void loadAnswersByPostId_returnsEmpty_whenNoAnswers() {
      given(answerJpaRepository.findByPostIdOrderByIsAcceptedDescCreatedAtAsc(10L))
          .willReturn(List.of());

      List<Answer> result = answerPersistenceAdapter.loadAnswersByPostId(10L);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("loadAnswer() maps an entity to a domain object")
    void loadAnswer_mapsEntityToDomain() {
      AnswerEntity entity = buildEntity(1L, 10L, 20L, "answer content", false);
      given(answerJpaRepository.findById(1L)).willReturn(Optional.of(entity));

      Optional<Answer> result = answerPersistenceAdapter.loadAnswer(1L);

      assertThat(result).isPresent();
      assertThat(result.orElseThrow().getContent()).isEqualTo("answer content");
    }

    @Test
    @DisplayName("countAnswers() delegates to repository count query")
    void countAnswers_delegatesToRepository() {
      given(answerJpaRepository.countByPostId(10L)).willReturn(2L);

      long result = answerPersistenceAdapter.countAnswers(10L);

      assertThat(result).isEqualTo(2L);
      verify(answerJpaRepository).countByPostId(10L);
    }

    @Test
    @DisplayName("deleteAnswer() delegates to deleteById")
    void deleteAnswer_deletesById() {
      answerPersistenceAdapter.deleteAnswer(100L);

      verify(answerJpaRepository).deleteById(100L);
    }

    @Test
    @DisplayName("deleteAnswersByPostId() delegates to deleteAllByPostId")
    void deleteAnswersByPostId_deletesAllByPostId() {
      answerPersistenceAdapter.deleteAnswersByPostId(10L);

      verify(answerJpaRepository).deleteAllByPostId(10L);
    }

    @Test
    @DisplayName("loadOrphanAnswerIds() delegates to findOrphanAnswerIds")
    void loadOrphanAnswerIds_delegatesToRepository() {
      given(answerJpaRepository.findOrphanAnswerIds(100)).willReturn(List.of(10L, 11L));

      List<Long> result = answerPersistenceAdapter.loadOrphanAnswerIds(100);

      assertThat(result).containsExactly(10L, 11L);
      verify(answerJpaRepository).findOrphanAnswerIds(100);
    }

    @Test
    @DisplayName("deleteAnswersByIds() delegates to deleteAllByIdInBatch")
    void deleteAnswersByIds_delegatesToDeleteAllByIdInBatch() {
      answerPersistenceAdapter.deleteAnswersByIds(List.of(10L, 11L));

      verify(answerJpaRepository).deleteAllByIdInBatch(List.of(10L, 11L));
    }

    @Test
    @DisplayName("deleteAnswersByIds() skips repository call when ids are empty")
    void deleteAnswersByIds_skipsWhenEmpty() {
      answerPersistenceAdapter.deleteAnswersByIds(List.of());

      verify(answerJpaRepository, never()).deleteAllByIdInBatch(any());
    }
  }

  @Nested
  @DisplayName("Failure cases")
  class FailureCases {

    @Test
    @DisplayName("loadAnswer() returns empty when the entity does not exist")
    void loadAnswer_returnsEmpty_whenEntityNotFound() {
      given(answerJpaRepository.findById(1L)).willReturn(Optional.empty());

      Optional<Answer> result = answerPersistenceAdapter.loadAnswer(1L);

      assertThat(result).isEmpty();
    }
  }

  private Answer buildAnswer(
      Long id, Long postId, Long userId, String content, boolean isAccepted) {
    return Answer.builder()
        .id(id)
        .postId(postId)
        .userId(userId)
        .content(content)
        .isAccepted(isAccepted)
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .build();
  }

  private AnswerEntity buildEntity(
      Long id, Long postId, Long userId, String content, boolean isAccepted) {
    return AnswerEntity.builder()
        .id(id)
        .postId(postId)
        .userId(userId)
        .content(content)
        .isAccepted(isAccepted)
        .build();
  }
}
