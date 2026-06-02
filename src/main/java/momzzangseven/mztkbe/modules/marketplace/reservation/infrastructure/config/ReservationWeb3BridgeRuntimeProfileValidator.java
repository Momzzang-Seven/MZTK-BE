package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.config;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3ConfigInvalidException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.CancelReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowOrderPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPaymentConfigPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionCandidatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionResumePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionWritePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationWalletPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.PrecheckReservationPurchasePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.PrepareReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.ReplayConfirmedReservationExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.ReplayTerminatedReservationExecutionPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** Fail-fast guard for reservation-owned marketplace Web3 bridge wiring. */
@Component
@RequiredArgsConstructor
@ConditionalOnExpression(
    "'${web3.eip7702.enabled:false}' == 'true' "
        + "&& '${web3.marketplace.user-execution.fail-fast:false}' == 'true'")
public class ReservationWeb3BridgeRuntimeProfileValidator {

  private final ObjectProvider<PrecheckReservationPurchasePort> precheckPurchasePort;
  private final ObjectProvider<PrepareReservationEscrowExecutionPort> prepareExecutionPort;
  private final ObjectProvider<LoadReservationWalletPort> loadWalletPort;
  private final ObjectProvider<LoadReservationEscrowPaymentConfigPort> loadPaymentConfigPort;
  private final ObjectProvider<LoadReservationEscrowOrderPort> loadEscrowOrderPort;
  private final ObjectProvider<LoadReservationExecutionWritePort> loadExecutionWritePort;
  private final ObjectProvider<LoadReservationExecutionStatePort> loadExecutionStatePort;
  private final ObjectProvider<LoadReservationExecutionResumePort> loadExecutionResumePort;
  private final ObjectProvider<LoadReservationExecutionCandidatePort> loadExecutionCandidatePort;
  private final ObjectProvider<CancelReservationEscrowExecutionPort> cancelExecutionPort;
  private final ObjectProvider<ReplayConfirmedReservationExecutionPort> replayConfirmedPort;
  private final ObjectProvider<ReplayTerminatedReservationExecutionPort> replayTerminatedPort;

  @PostConstruct
  void validateOnStartup() {
    List<String> missing = new ArrayList<>();
    require("PrecheckReservationPurchasePort", precheckPurchasePort, missing);
    require("PrepareReservationEscrowExecutionPort", prepareExecutionPort, missing);
    require("LoadReservationWalletPort", loadWalletPort, missing);
    require("LoadReservationEscrowPaymentConfigPort", loadPaymentConfigPort, missing);
    require("LoadReservationEscrowOrderPort", loadEscrowOrderPort, missing);
    require("LoadReservationExecutionWritePort", loadExecutionWritePort, missing);
    require("LoadReservationExecutionStatePort", loadExecutionStatePort, missing);
    require("LoadReservationExecutionResumePort", loadExecutionResumePort, missing);
    require("LoadReservationExecutionCandidatePort", loadExecutionCandidatePort, missing);
    require("CancelReservationEscrowExecutionPort", cancelExecutionPort, missing);
    require("ReplayConfirmedReservationExecutionPort", replayConfirmedPort, missing);
    require("ReplayTerminatedReservationExecutionPort", replayTerminatedPort, missing);

    if (!missing.isEmpty()) {
      throw new Web3ConfigInvalidException(
          "Marketplace reservation Web3 bridge is incomplete while web3.eip7702.enabled=true "
              + "and web3.marketplace.user-execution.fail-fast=true. Missing: "
              + String.join(", ", missing));
    }
  }

  private static void require(String name, ObjectProvider<?> provider, List<String> missing) {
    if (provider.getIfAvailable() == null) {
      missing.add(name);
    }
  }
}
