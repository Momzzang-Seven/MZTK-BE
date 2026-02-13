package momzzangseven.mztkbe.modules.level.application.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.level.LevelUpCommandInvalidException;
import momzzangseven.mztkbe.global.error.level.LevelValidationMessage;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpCommand;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpResult;
import momzzangseven.mztkbe.modules.level.application.port.in.GrantXpUseCase;
import momzzangseven.mztkbe.modules.level.application.port.out.PolicyPort;
import momzzangseven.mztkbe.modules.level.application.port.out.UserProgressPort;
import momzzangseven.mztkbe.modules.level.application.port.out.XpLedgerPort;
import momzzangseven.mztkbe.modules.level.domain.model.UserProgress;
import momzzangseven.mztkbe.modules.level.domain.model.XpLedgerEntry;
import momzzangseven.mztkbe.modules.level.domain.model.XpPolicy;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class GrantXpService implements GrantXpUseCase {

  private final UserProgressPort userProgressPort;
  private final PolicyPort policyPort;
  private final XpLedgerPort xpLedgerPort;
  private final ZoneId appZoneId;

  @Override
  public GrantXpResult execute(GrantXpCommand command) {
    if (command == null) {
      throw new LevelUpCommandInvalidException(LevelValidationMessage.COMMAND_REQUIRED);
    }

    Long userId = command.userId();
    LocalDateTime occurredAt = command.occurredAt();
    XpType xpType = command.xpType();

    userProgressPort.loadOrCreateUserProgress(userId);
    UserProgress progress = userProgressPort.loadUserProgressWithLock(userId);

    XpPolicy policy =
        policyPort
            .loadXpPolicy(xpType, occurredAt)
            .orElseThrow(() -> new IllegalStateException("XP policy not found: type=" + xpType));

    String idempotencyKey = command.idempotencyKey();
    int dailyCap = policy.getDailyCap();
    java.time.LocalDate earnedOn = occurredAt.atZone(appZoneId).toLocalDate();
    int grantedToday = xpLedgerPort.countByUserIdAndTypeAndEarnedOn(userId, xpType, earnedOn);

    if (xpLedgerPort.existsByUserIdAndIdempotencyKey(userId, idempotencyKey)) {
      return GrantXpResult.alreadyGranted(dailyCap, grantedToday, earnedOn);
    }

    if (dailyCap == 0) {
      return GrantXpResult.dailyCapReached(dailyCap, grantedToday, earnedOn);
    }
    if (dailyCap > 0 && grantedToday >= dailyCap) {
      return GrantXpResult.dailyCapReached(dailyCap, grantedToday, earnedOn);
    }

    XpLedgerEntry entry =
        XpLedgerEntry.create(
            userId,
            xpType,
            policy.getXpAmount(),
            earnedOn,
            occurredAt,
            idempotencyKey,
            command.sourceRef());
    if (!xpLedgerPort.trySaveXpLedger(entry)) {
      log.info(
          "XP grant already recorded (idempotency): userId={}, type={}, key={}",
          userId,
          xpType,
          idempotencyKey);
      return GrantXpResult.alreadyGranted(dailyCap, grantedToday, earnedOn);
    }

    UserProgress updated = progress.grantXp(policy.getXpAmount(), LocalDateTime.now());
    userProgressPort.saveUserProgress(updated);

    return GrantXpResult.granted(policy.getXpAmount(), dailyCap, grantedToday + 1, earnedOn);
  }
}
