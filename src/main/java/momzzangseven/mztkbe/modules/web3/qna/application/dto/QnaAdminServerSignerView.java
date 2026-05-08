package momzzangseven.mztkbe.modules.web3.qna.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;

/**
 * QnA-local diagnostic view of the sponsor treasury signer's readiness. Mirrors the field shape and
 * enum constants of {@code treasury.application.dto.ExecutionSignerCapabilityView} so the wrapping
 * {@link QnaAdminExecutionAuthorityView} preserves its serialized JSON contract on the QnA admin
 * surfaces (settlement / refund review responses).
 *
 * <p>The bridging adapter under {@code qna/infrastructure/external/treasury/} maps the treasury
 * view onto this record by name; that adapter is the only QnA file allowed to import {@code
 * treasury.*}.
 */
public record QnaAdminServerSignerView(
    String walletAlias,
    QnaAdminServerSignerSlotStatus slotStatus,
    QnaAdminServerSignerFailureReason failureReason,
    String signerAddress,
    boolean signable) {

  public QnaAdminServerSignerView {
    if (walletAlias == null || walletAlias.isBlank()) {
      throw new Web3InvalidInputException("walletAlias is required");
    }
    if (slotStatus == null) {
      throw new Web3InvalidInputException("slotStatus is required");
    }
    if (failureReason == null) {
      throw new Web3InvalidInputException("failureReason is required");
    }
    if (signerAddress != null && !signerAddress.isBlank()) {
      signerAddress = EvmAddress.of(signerAddress).value();
    } else {
      signerAddress = null;
    }
    if (signable && signerAddress == null) {
      throw new Web3InvalidInputException("signerAddress is required when signable");
    }
    if (slotStatus == QnaAdminServerSignerSlotStatus.READY && !signable) {
      throw new Web3InvalidInputException("READY signer must be signable");
    }
    if (signable
        && (slotStatus != QnaAdminServerSignerSlotStatus.READY
            || failureReason != QnaAdminServerSignerFailureReason.NONE)) {
      throw new Web3InvalidInputException("signable signer must be READY with no failureReason");
    }
    if (slotStatus == QnaAdminServerSignerSlotStatus.PROVISIONED
        && failureReason == QnaAdminServerSignerFailureReason.NONE) {
      throw new Web3InvalidInputException("PROVISIONED signer requires a failureReason");
    }
  }

  public static QnaAdminServerSignerView slotMissing(String walletAlias) {
    return new QnaAdminServerSignerView(
        walletAlias,
        QnaAdminServerSignerSlotStatus.SLOT_MISSING,
        QnaAdminServerSignerFailureReason.NONE,
        null,
        false);
  }

  public static QnaAdminServerSignerView unprovisioned(String walletAlias) {
    return new QnaAdminServerSignerView(
        walletAlias,
        QnaAdminServerSignerSlotStatus.UNPROVISIONED,
        QnaAdminServerSignerFailureReason.NONE,
        null,
        false);
  }

  public static QnaAdminServerSignerView unavailable(
      String walletAlias,
      QnaAdminServerSignerSlotStatus slotStatus,
      QnaAdminServerSignerFailureReason failureReason) {
    return new QnaAdminServerSignerView(walletAlias, slotStatus, failureReason, null, false);
  }

  public static QnaAdminServerSignerView provisionedUnavailable(
      String walletAlias, QnaAdminServerSignerFailureReason failureReason) {
    return new QnaAdminServerSignerView(
        walletAlias, QnaAdminServerSignerSlotStatus.PROVISIONED, failureReason, null, false);
  }

  public static QnaAdminServerSignerView ready(String walletAlias, String signerAddress) {
    return new QnaAdminServerSignerView(
        walletAlias,
        QnaAdminServerSignerSlotStatus.READY,
        QnaAdminServerSignerFailureReason.NONE,
        signerAddress,
        true);
  }
}
