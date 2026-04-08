package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntent;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntentStatus;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity.QuestionRewardIntentEntity;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.repository.QuestionRewardIntentJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuestionRewardIntentPersistenceAdapterTest {

  private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-04-08T00:00:00Z"), APP_ZONE);
  private static final LocalDateTime FIXED_NOW =
      LocalDateTime.ofInstant(FIXED_CLOCK.instant(), APP_ZONE);

  @Mock private QuestionRewardIntentJpaRepository repository;

  private QuestionRewardIntentPersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new QuestionRewardIntentPersistenceAdapter(repository, FIXED_CLOCK);
  }

  @Test
  void create_throws_whenIdPresent() {
    QuestionRewardIntent intent = baseIntent().id(1L).build();

    assertThatThrownBy(() -> adapter.create(intent))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("create requires id to be null");
  }

  @Test
  void update_throws_whenIdMissing() {
    QuestionRewardIntent intent = baseIntent().id(null).build();

    assertThatThrownBy(() -> adapter.update(intent))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("update requires id");
  }

  @Test
  void create_mapsDomainToEntity_andBack() {
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    QuestionRewardIntent created = adapter.create(baseIntent().id(null).build());

    ArgumentCaptor<QuestionRewardIntentEntity> captor =
        ArgumentCaptor.forClass(QuestionRewardIntentEntity.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getPostId()).isEqualTo(101L);
    assertThat(captor.getValue().getCreatedAt()).isEqualTo(FIXED_NOW);
    assertThat(captor.getValue().getUpdatedAt()).isEqualTo(FIXED_NOW);
    assertThat(created.getStatus()).isEqualTo(QuestionRewardIntentStatus.PREPARE_REQUIRED);
    assertThat(created.getCreatedAt()).isEqualTo(FIXED_NOW);
    assertThat(created.getUpdatedAt()).isEqualTo(FIXED_NOW);
  }

  @Test
  void updateStatusIfCurrentIn_delegatesToRepository() {
    when(repository.updateStatusIfCurrentIn(
            101L,
            QuestionRewardIntentStatus.SUBMITTED,
            EnumSet.of(QuestionRewardIntentStatus.PREPARE_REQUIRED)))
        .thenReturn(1);

    int updated =
        adapter.updateStatusIfCurrentIn(
            101L,
            QuestionRewardIntentStatus.SUBMITTED,
            EnumSet.of(QuestionRewardIntentStatus.PREPARE_REQUIRED));

    assertThat(updated).isEqualTo(1);
  }

  @Test
  void findByPostId_mapsEntityToDomain() {
    QuestionRewardIntentEntity entity =
        QuestionRewardIntentEntity.builder()
            .id(9L)
            .postId(101L)
            .acceptedCommentId(201L)
            .fromUserId(7L)
            .toUserId(22L)
            .amountWei(BigInteger.TEN)
            .status(QuestionRewardIntentStatus.SUBMITTED)
            .build();
    when(repository.findByPostId(101L)).thenReturn(Optional.of(entity));

    Optional<QuestionRewardIntent> result = adapter.findByPostId(101L);

    assertThat(result).isPresent();
    assertThat(result.get().getId()).isEqualTo(9L);
    assertThat(result.get().getStatus()).isEqualTo(QuestionRewardIntentStatus.SUBMITTED);
  }

  private QuestionRewardIntent.QuestionRewardIntentBuilder baseIntent() {
    return QuestionRewardIntent.builder()
        .postId(101L)
        .acceptedCommentId(201L)
        .fromUserId(7L)
        .toUserId(22L)
        .amountWei(BigInteger.TEN)
        .status(QuestionRewardIntentStatus.PREPARE_REQUIRED);
  }
}
