package momzzangseven.mztkbe.modules.marketplace.sanction.infrastructure.persistence.adapter;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.sanction.application.port.out.LoadTrainerSanctionPort;
import momzzangseven.mztkbe.modules.marketplace.sanction.application.port.out.ManageTrainerSanctionPort;
import momzzangseven.mztkbe.modules.marketplace.sanction.infrastructure.persistence.entity.TrainerSanctionEntity;
import momzzangseven.mztkbe.modules.marketplace.sanction.infrastructure.persistence.entity.TrainerStrikeRecordEntity;
import momzzangseven.mztkbe.modules.marketplace.sanction.infrastructure.persistence.repository.TrainerSanctionJpaRepository;
import momzzangseven.mztkbe.modules.marketplace.sanction.infrastructure.persistence.repository.TrainerStrikeRecordJpaRepository;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SanctionPersistenceAdapter
    implements ManageTrainerSanctionPort, LoadTrainerSanctionPort {

  private final TrainerSanctionJpaRepository sanctionRepository;
  private final TrainerStrikeRecordJpaRepository strikeRecordRepository;
  private final Clock clock;

  @Override
  public RecordStrikeResult recordStrike(Long trainerId, String reason) {
    TrainerSanctionEntity sanction =
        sanctionRepository
            .findById(trainerId)
            .orElseGet(() -> TrainerSanctionEntity.builder().trainerId(trainerId).build());

    LocalDateTime now = LocalDateTime.now(clock);
    boolean wasBanned =
        sanction.getSuspendedUntil() != null && sanction.getSuspendedUntil().isAfter(now);

    TrainerSanctionEntity updatedSanction = sanction.addStrike(now);
    boolean isBanned =
        updatedSanction.getSuspendedUntil() != null
            && updatedSanction.getSuspendedUntil().isAfter(now);

    boolean newlyBanned = !wasBanned && isBanned;

    sanctionRepository.save(updatedSanction);

    TrainerStrikeRecordEntity record =
        TrainerStrikeRecordEntity.builder().trainerId(trainerId).reason(reason).build();
    strikeRecordRepository.save(record);

    log.info(
        "Recorded strike for trainerId={}, reason={}, count={}, banned={}",
        trainerId,
        reason,
        updatedSanction.getStrikeCount(),
        isBanned);

    return new RecordStrikeResult(updatedSanction.getStrikeCount(), newlyBanned);
  }

  @Override
  public boolean hasActiveSanction(Long trainerId) {
    return getSuspendedUntil(trainerId)
        .map(suspendedUntil -> suspendedUntil.isAfter(LocalDateTime.now(clock)))
        .orElse(false);
  }

  @Override
  public Optional<LocalDateTime> getSuspendedUntil(Long trainerId) {
    return sanctionRepository.findById(trainerId).map(TrainerSanctionEntity::getSuspendedUntil);
  }
}
