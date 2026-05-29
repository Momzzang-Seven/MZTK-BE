package momzzangseven.mztkbe.modules.level.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpCommand;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpResult;
import momzzangseven.mztkbe.modules.level.application.port.in.GrantXpUseCase;
import momzzangseven.mztkbe.modules.level.application.port.in.GuaranteedGrantXpUseCase;
import momzzangseven.mztkbe.modules.level.application.port.out.XpGrantOutboxPort;
import org.springframework.stereotype.Service;

/**
 * Synchronous grant with a guaranteed-delivery fallback.
 *
 * <p>Intentionally <b>not</b> {@code @Transactional}: the only transaction in the grant chain is
 * {@link GrantXpUseCase#execute} (T2). Keeping this orchestrator transaction-free is what lets the
 * caller's entity transaction (T1) fully commit and release its connection <em>before</em> T2
 * acquires one — so the request holds at most one connection at a time.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuaranteedGrantXpService implements GuaranteedGrantXpUseCase {

  private final GrantXpUseCase grantXpUseCase;
  private final XpGrantOutboxPort outboxPort;

  @Override
  public GrantXpResult execute(GrantXpCommand command) {
    try {
      return grantXpUseCase.execute(command);
    } catch (Exception e) {
      log.error(
          "Synchronous XP grant failed; enqueueing to outbox for guaranteed retry: key={}",
          command.idempotencyKey(),
          e);
      outboxPort.enqueue(command);
      return GrantXpResult.deferred(command.occurredAt().toLocalDate());
    }
  }
}
