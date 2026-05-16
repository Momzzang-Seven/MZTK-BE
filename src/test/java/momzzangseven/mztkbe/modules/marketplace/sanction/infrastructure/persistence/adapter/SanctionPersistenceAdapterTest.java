package momzzangseven.mztkbe.modules.marketplace.sanction.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.sanction.application.dto.RecordTrainerStrikeCommand;
import momzzangseven.mztkbe.modules.marketplace.sanction.application.port.out.ManageTrainerSanctionPort.RecordStrikeResult;
import momzzangseven.mztkbe.modules.marketplace.sanction.infrastructure.persistence.entity.TrainerSanctionEntity;
import momzzangseven.mztkbe.modules.marketplace.sanction.infrastructure.persistence.entity.TrainerStrikeRecordEntity;
import momzzangseven.mztkbe.modules.marketplace.sanction.infrastructure.persistence.repository.TrainerSanctionJpaRepository;
import momzzangseven.mztkbe.modules.marketplace.sanction.infrastructure.persistence.repository.TrainerStrikeRecordJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SanctionPersistenceAdapterTest {

  private static final Long TRAINER_ID = 9L;
  private static final String SOURCE_TYPE =
      RecordTrainerStrikeCommand.SOURCE_MARKETPLACE_RESERVATION_REJECT;
  private static final String SOURCE_ID = "reservation-123";

  @Mock private TrainerSanctionJpaRepository sanctionRepository;
  @Mock private TrainerStrikeRecordJpaRepository strikeRecordRepository;

  private SanctionPersistenceAdapter sut;

  @BeforeEach
  void setUp() {
    sut =
        new SanctionPersistenceAdapter(
            sanctionRepository,
            strikeRecordRepository,
            Clock.fixed(Instant.parse("2026-05-16T00:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  @DisplayName("source identity가 이미 기록된 trainer strike는 strike count를 다시 증가시키지 않는다")
  void duplicateSource_doesNotIncrementStrikeCount() {
    given(sanctionRepository.findByIdWithLock(TRAINER_ID))
        .willReturn(
            Optional.of(
                TrainerSanctionEntity.builder().trainerId(TRAINER_ID).strikeCount(2).build()));
    given(strikeRecordRepository.existsBySourceTypeAndSourceId(SOURCE_TYPE, SOURCE_ID))
        .willReturn(true);

    RecordStrikeResult result =
        sut.recordStrike(
            TRAINER_ID, RecordTrainerStrikeCommand.REASON_REJECT, SOURCE_TYPE, SOURCE_ID);

    assertThat(result.strikeCount()).isEqualTo(2);
    assertThat(result.isBanned()).isFalse();
    then(sanctionRepository).should(never()).save(any());
    then(strikeRecordRepository).should(never()).save(any());
  }

  @Test
  @DisplayName("source identity가 있는 trainer strike는 record row에 source를 함께 저장한다")
  void sourceIdentity_isPersistedOnStrikeRecord() {
    given(sanctionRepository.findByIdWithLock(TRAINER_ID))
        .willReturn(
            Optional.of(
                TrainerSanctionEntity.builder().trainerId(TRAINER_ID).strikeCount(1).build()));
    given(strikeRecordRepository.existsBySourceTypeAndSourceId(SOURCE_TYPE, SOURCE_ID))
        .willReturn(false);

    sut.recordStrike(TRAINER_ID, RecordTrainerStrikeCommand.REASON_REJECT, SOURCE_TYPE, SOURCE_ID);

    ArgumentCaptor<TrainerStrikeRecordEntity> captor =
        ArgumentCaptor.forClass(TrainerStrikeRecordEntity.class);
    then(strikeRecordRepository).should().save(captor.capture());
    assertThat(captor.getValue().getSourceType()).isEqualTo(SOURCE_TYPE);
    assertThat(captor.getValue().getSourceId()).isEqualTo(SOURCE_ID);
  }
}
