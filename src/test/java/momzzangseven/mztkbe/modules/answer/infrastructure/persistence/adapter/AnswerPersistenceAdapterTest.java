// src/test/java/momzzangseven/mztkbe/modules/answer/infrastructure/persistence/adapter/AnswerPersistenceAdapterTest.java
package momzzangseven.mztkbe.modules.answer.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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
@DisplayName("AnswerPersistenceAdapter 단위 테스트")
class AnswerPersistenceAdapterTest {

  @Mock private AnswerJpaRepository answerJpaRepository;

  @InjectMocks private AnswerPersistenceAdapter answerPersistenceAdapter;

  @Nested
  @DisplayName("성공 케이스")
  class SuccessCases {

    @Test
    @DisplayName("답변 저장 시 도메인을 엔티티로 변환해 저장하고 다시 도메인으로 반환한다")
    void saveAnswer_convertsDomainAndReturnsSavedDomain() {
      // given
      Answer answer = buildAnswer(null, 10L, 20L, "답변 내용", false, List.of("https://image"));
      AnswerEntity savedEntity =
          buildEntity(99L, 10L, 20L, "답변 내용", false, List.of("https://image"));
      given(answerJpaRepository.save(any(AnswerEntity.class))).willReturn(savedEntity);

      // when
      Answer result = answerPersistenceAdapter.saveAnswer(answer);

      // then
      assertThat(result.getId()).isEqualTo(99L);
      assertThat(result.getPostId()).isEqualTo(10L);
      assertThat(result.getUserId()).isEqualTo(20L);
      ArgumentCaptor<AnswerEntity> entityCaptor = ArgumentCaptor.forClass(AnswerEntity.class);
      verify(answerJpaRepository).save(entityCaptor.capture());
      assertThat(entityCaptor.getValue().getPostId()).isEqualTo(10L);
      assertThat(entityCaptor.getValue().getContent()).isEqualTo("답변 내용");
      assertThat(entityCaptor.getValue().getImageUrls()).containsExactly("https://image");
    }

    @Test
    @DisplayName("게시글 ID로 답변 목록 조회 시 엔티티 목록을 도메인 목록으로 변환한다")
    void loadAnswersByPostId_mapsEntitiesToDomains() {
      // given
      Long postId = 10L;
      List<AnswerEntity> entities =
          List.of(
              buildEntity(1L, 10L, 20L, "첫 답변", true, List.of()),
              buildEntity(2L, 10L, 21L, "둘째 답변", false, List.of("https://image")));
      given(answerJpaRepository.findByPostIdOrderByIsAcceptedDescCreatedAtAsc(postId))
          .willReturn(entities);

      // when
      List<Answer> result = answerPersistenceAdapter.loadAnswersByPostId(postId);

      // then
      assertThat(result).hasSize(2);
      assertThat(result.get(0).getIsAccepted()).isTrue();
      assertThat(result.get(1).getImageUrls()).containsExactly("https://image");
    }

    @Test
    @DisplayName("답변 단건 조회 시 엔티티를 도메인으로 변환한다")
    void loadAnswer_mapsEntityToDomain() {
      // given
      AnswerEntity entity = buildEntity(1L, 10L, 20L, "답변 내용", false, List.of());
      given(answerJpaRepository.findById(1L)).willReturn(Optional.of(entity));

      // when
      Optional<Answer> result = answerPersistenceAdapter.loadAnswer(1L);

      // then
      assertThat(result).isPresent();
      assertThat(result.orElseThrow().getContent()).isEqualTo("답변 내용");
    }
  }

  @Nested
  @DisplayName("실패 케이스")
  class FailureCases {

    @Test
    @DisplayName("답변 단건 조회 시 엔티티가 없으면 빈 결과를 반환한다")
    void loadAnswer_returnsEmpty_whenEntityNotFound() {
      // given
      given(answerJpaRepository.findById(1L)).willReturn(Optional.empty());

      // when
      Optional<Answer> result = answerPersistenceAdapter.loadAnswer(1L);

      // then
      assertThat(result).isEmpty();
    }
  }

  private Answer buildAnswer(
      Long id,
      Long postId,
      Long userId,
      String content,
      boolean isAccepted,
      List<String> imageUrls) {
    return Answer.builder()
        .id(id)
        .postId(postId)
        .userId(userId)
        .content(content)
        .isAccepted(isAccepted)
        .imageUrls(imageUrls)
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .build();
  }

  private AnswerEntity buildEntity(
      Long id,
      Long postId,
      Long userId,
      String content,
      boolean isAccepted,
      List<String> imageUrls) {
    return AnswerEntity.builder()
        .id(id)
        .postId(postId)
        .userId(userId)
        .content(content)
        .isAccepted(isAccepted)
        .imageUrls(imageUrls)
        .build();
  }
}
