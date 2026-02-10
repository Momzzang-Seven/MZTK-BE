package momzzangseven.mztkbe.modules.location.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.location.application.port.out.GrantXpPort;
import momzzangseven.mztkbe.modules.location.domain.model.LocationVerification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * XP Grant Service
 *
 * <p>Separate Service Bean to ensure transaction independence
 *
 * <p>Prevent self-invocation issue: - @Transactional must be called through Spring AOP proxy -
 * Calling within the same class without proxy will invalidate @Transactional - Separate Bean to
 * resolve
 *
 * <p>Transaction Propagation: - REQUIRES_NEW: always create a new physical transaction - suspend
 * external transaction (suspend) → execute internal transaction → resume external transaction
 * (resume) - XP grant failure does not affect verification saving
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XpGrantService {

  private final GrantXpPort grantXpPort;

  /**
   * Grant XP for location verification
   *
   * <p>Transaction Propagation: REQUIRES_NEW - always create a new transaction (independent of
   * external transaction) - suspend external transaction (suspend) → execute internal transaction →
   * resume external transaction (resume) - Commit/rollback after external transaction
   *
   * <p>Duplicate processing: - GrantXpService in Level module does not throw exceptions - Return
   * grantedXp = 0 for duplicate/daily cap - This method returns 0 normally, and Transaction B is
   * committed
   *
   * @param verification saved location verification information
   * @return granted XP (0 for duplicate/daily cap)
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public int grantXp(LocationVerification verification) {
    log.info("Granting Xp for location verification: verificationId = {}", verification.getId());

    try {
      int grantedXp = grantXpPort.grantLocationVerificationXp(verification);
      log.info("XP granted successfully: xp={}", grantedXp);

      if (grantedXp > 0) {
        log.info(
            "XP granted successfully (GrantXp Transaction committed): verificationId={}, xp={}",
            verification.getId(),
            grantedXp);
      } else {
        log.info(
            "XP not granted (already granted or daily cap reached, GrantXp Transaction committed): verificationId={}",
            verification.getId());
      }
      // GrantXp Transaction commited (grantedXp = 0 also committed)
      return grantedXp;
    } catch (Exception e) {
      // System error occurred in Level module
      // - Unexpected error
      log.error(
          "Failed to grant XP due to system error (GrantXp Transaction will rollback): verificationId={}",
          verification.getId(),
          e);

      // GrantXp Transaction rolled back
      throw e;
    }
  }
}
