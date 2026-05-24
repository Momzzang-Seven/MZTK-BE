package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RecoverExpiredMarketplaceAdminExecutionAttemptCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RecoverExpiredMarketplaceAdminExecutionAttemptResult;

/** Recovers expired, unbound marketplace admin execution preparations. */
public interface RecoverExpiredMarketplaceAdminExecutionAttemptUseCase {

  RecoverExpiredMarketplaceAdminExecutionAttemptResult execute(
      RecoverExpiredMarketplaceAdminExecutionAttemptCommand command);
}
