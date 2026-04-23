package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.sanction;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.CheckTrainerSanctionPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stub implementation of {@link CheckTrainerSanctionPort}.
 *
 * <p>The real adapter should delegate to the sanction module's {@code LoadTrainerSanctionPort}
 * once cross-module wiring is complete. Until then this stub always reports no active sanction.
 *
 * <p><b>Note:</b> A {@link PostConstruct} guard throws {@link IllegalStateException} on startup
 * when the active profile is {@code prod}, preventing silent stub usage in production. Replace with
 * the real implementation before deploying to production.
 */
@Slf4j
@Component
public class TrainerSanctionCheckAdapter implements CheckTrainerSanctionPort {

  @Value("${spring.profiles.active:}")
  private String activeProfiles;

  @PostConstruct
  void rejectIfProd() {
    if (activeProfiles.contains("prod")) {
      throw new IllegalStateException(
          "[STUB] TrainerSanctionCheckAdapter is a stub and must not run in prod. "
              + "Wire it to the real SanctionPersistenceAdapter before deploying.");
    }
    log.warn(
        "[STUB] TrainerSanctionCheckAdapter is active (profiles={}). "
            + "All sanction checks return false (no suspension).",
        activeProfiles);
  }

  @Override
  public boolean hasActiveSanction(Long trainerId) {
    // TODO: delegate to sanction module's LoadTrainerSanctionPort when available
    return false;
  }

  @Override
  public Optional<LocalDateTime> getSuspendedUntil(Long trainerId) {
    // TODO: delegate to sanction module's LoadTrainerSanctionPort when available
    return Optional.empty();
  }
}
