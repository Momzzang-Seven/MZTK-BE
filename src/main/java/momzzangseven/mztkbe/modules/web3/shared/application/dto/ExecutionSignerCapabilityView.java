package momzzangseven.mztkbe.modules.web3.shared.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;

public record ExecutionSignerCapabilityView(
    String walletAlias,
    ExecutionSignerSlotStatus slotStatus,
    ExecutionSignerFailureReason failureReason,
    String signerAddress,
    boolean signable) {

  public ExecutionSignerCapabilityView {
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
    if (slotStatus == ExecutionSignerSlotStatus.READY && !signable) {
      throw new Web3InvalidInputException("READY signer must be signable");
    }
    if (signable
        && (slotStatus != ExecutionSignerSlotStatus.READY
            || failureReason != ExecutionSignerFailureReason.NONE)) {
      throw new Web3InvalidInputException("signable signer must be READY with no failureReason");
    }
    if (slotStatus == ExecutionSignerSlotStatus.PROVISIONED
        && failureReason == ExecutionSignerFailureReason.NONE) {
      throw new Web3InvalidInputException("PROVISIONED signer requires a failureReason");
    }
  }

  public static ExecutionSignerCapabilityView slotMissing(String walletAlias) {
    return new ExecutionSignerCapabilityView(
        walletAlias,
        ExecutionSignerSlotStatus.SLOT_MISSING,
        ExecutionSignerFailureReason.NONE,
        null,
        false);
  }

  public static ExecutionSignerCapabilityView unprovisioned(String walletAlias) {
    return new ExecutionSignerCapabilityView(
        walletAlias,
        ExecutionSignerSlotStatus.UNPROVISIONED,
        ExecutionSignerFailureReason.NONE,
        null,
        false);
  }

  public static ExecutionSignerCapabilityView unavailable(
      String walletAlias,
      ExecutionSignerSlotStatus slotStatus,
      ExecutionSignerFailureReason failureReason) {
    return new ExecutionSignerCapabilityView(walletAlias, slotStatus, failureReason, null, false);
  }

  public static ExecutionSignerCapabilityView provisionedUnavailable(
      String walletAlias, ExecutionSignerFailureReason failureReason) {
    return new ExecutionSignerCapabilityView(
        walletAlias, ExecutionSignerSlotStatus.PROVISIONED, failureReason, null, false);
  }

  public static ExecutionSignerCapabilityView ready(String walletAlias, String signerAddress) {
    return new ExecutionSignerCapabilityView(
        walletAlias,
        ExecutionSignerSlotStatus.READY,
        ExecutionSignerFailureReason.NONE,
        signerAddress,
        true);
  }
}
