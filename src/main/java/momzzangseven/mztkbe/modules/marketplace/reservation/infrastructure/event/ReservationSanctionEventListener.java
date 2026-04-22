package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.sanction.application.dto.RecordTrainerStrikeCommand;
import momzzangseven.mztkbe.modules.marketplace.sanction.application.port.in.RecordTrainerStrikeUseCase;
import momzzangseven.mztkbe.modules.marketplace.sanction.domain.vo.TrainerStrikeEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Domain event listener that records a trainer strike after a successful REJECT transaction commit.
 *
 * <p>Uses {@code AFTER_COMMIT} to ensure the reservation row is committed before modifying sanction
 * state. Opens a new transaction ({@code REQUIRES_NEW}) so the strike write is independent.
 *
 * <p>Delegates to {@link RecordTrainerStrikeUseCase} — an input port — rather than calling an
 * output port directly. This satisfies the ARCHITECTURE.md rule that event handlers, as driving
 * adapters, must always go through {@code application/port/in}.
 *
 * <p>Exceptions are caught and logged without rethrowing; the scheduler acts as the fallback for
 * any missed events.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationSanctionEventListener {

  private final RecordTrainerStrikeUseCase recordTrainerStrikeUseCase;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(TrainerStrikeEvent event) {
    try {
      recordTrainerStrikeUseCase.execute(
          new RecordTrainerStrikeCommand(event.trainerId(), event.reason()));
    } catch (Exception e) {
      log.error(
          "Strike recording failed: trainerId={}, reason={}", event.trainerId(), event.reason(), e);
    }
  }
}
