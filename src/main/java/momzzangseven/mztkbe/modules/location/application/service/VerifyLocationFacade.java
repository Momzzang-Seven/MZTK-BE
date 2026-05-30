package momzzangseven.mztkbe.modules.location.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.location.application.dto.VerifyLocationCommand;
import momzzangseven.mztkbe.modules.location.application.dto.VerifyLocationResult;
import momzzangseven.mztkbe.modules.location.application.dto.XpGrantInfo;
import momzzangseven.mztkbe.modules.location.application.port.in.VerifyLocationUseCase;
import momzzangseven.mztkbe.modules.location.application.port.out.GrantXpPort;
import momzzangseven.mztkbe.modules.location.domain.model.LocationVerification;
import org.springframework.stereotype.Service;

/**
 * Orchestrates location verification as two sequential transactions: T1 verifies and saves the
 * record (commits and releases its connection), then — only on success — T2 grants XP on a fresh
 * connection.
 *
 * <p>Intentionally <b>not</b> {@code @Transactional} so T1's connection is released before the XP
 * grant runs, keeping connection occupancy at one. The real granted amount is reflected in the
 * response; grant failures are made durable by the guaranteed-delivery path behind {@link
 * GrantXpPort}.
 */
@Service
@RequiredArgsConstructor
public class VerifyLocationFacade implements VerifyLocationUseCase {

  private final VerifyLocationService verifyLocationService;
  private final GrantXpPort grantXpPort;

  @Override
  public VerifyLocationResult execute(VerifyLocationCommand command) {
    LocationVerification saved = verifyLocationService.verify(command);

    XpGrantInfo xpInfo;
    if (saved.isSuccessful()) {
      int grantedXp = grantXpPort.grantLocationVerificationXp(saved);
      xpInfo =
          grantedXp > 0
              ? new XpGrantInfo(true, grantedXp, "XP granted successfully")
              : new XpGrantInfo(false, 0, "XP already granted for WORKOUT type");
    } else {
      xpInfo = new XpGrantInfo(false, 0, "Verification failed - XP not granted");
    }

    return VerifyLocationResult.from(saved, xpInfo);
  }
}
