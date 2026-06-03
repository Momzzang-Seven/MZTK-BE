package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

@DisplayName("ReservationWeb3BridgeRuntimeProfileValidator")
class ReservationWeb3BridgeRuntimeProfileValidatorTest {

  @Test
  @DisplayName("reservation bridge port 가 모두 있으면 통과한다")
  void validateOnStartup_allowsCompleteBridgeWiring() {
    ReservationWeb3BridgeRuntimeProfileValidator validator =
        validator(
            mock(PrecheckReservationPurchasePort.class),
            mock(PrepareReservationEscrowExecutionPort.class),
            mock(LoadReservationWalletPort.class),
            mock(LoadReservationEscrowPaymentConfigPort.class),
            mock(LoadReservationEscrowOrderPort.class),
            mock(LoadReservationExecutionWritePort.class),
            mock(LoadReservationExecutionStatePort.class),
            mock(LoadReservationExecutionResumePort.class),
            mock(LoadReservationExecutionCandidatePort.class),
            mock(CancelReservationEscrowExecutionPort.class),
            mock(ReplayConfirmedReservationExecutionPort.class),
            mock(ReplayTerminatedReservationExecutionPort.class));

    assertThatCode(validator::validateOnStartup).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("reservation bridge port 누락이면 startup validation 이 실패한다")
  void validateOnStartup_rejectsMissingBridgeWiring() {
    ReservationWeb3BridgeRuntimeProfileValidator validator =
        validator(
            null,
            mock(PrepareReservationEscrowExecutionPort.class),
            mock(LoadReservationWalletPort.class),
            mock(LoadReservationEscrowPaymentConfigPort.class),
            null,
            mock(LoadReservationExecutionWritePort.class),
            mock(LoadReservationExecutionStatePort.class),
            mock(LoadReservationExecutionResumePort.class),
            mock(LoadReservationExecutionCandidatePort.class),
            mock(CancelReservationEscrowExecutionPort.class),
            mock(ReplayConfirmedReservationExecutionPort.class),
            mock(ReplayTerminatedReservationExecutionPort.class));

    assertThatThrownBy(validator::validateOnStartup)
        .isInstanceOf(Web3ConfigInvalidException.class)
        .hasMessageContaining("PrecheckReservationPurchasePort")
        .hasMessageContaining("LoadReservationEscrowOrderPort");
  }

  private static ReservationWeb3BridgeRuntimeProfileValidator validator(
      PrecheckReservationPurchasePort precheckPurchasePort,
      PrepareReservationEscrowExecutionPort prepareExecutionPort,
      LoadReservationWalletPort loadWalletPort,
      LoadReservationEscrowPaymentConfigPort loadPaymentConfigPort,
      LoadReservationEscrowOrderPort loadEscrowOrderPort,
      LoadReservationExecutionWritePort loadExecutionWritePort,
      LoadReservationExecutionStatePort loadExecutionStatePort,
      LoadReservationExecutionResumePort loadExecutionResumePort,
      LoadReservationExecutionCandidatePort loadExecutionCandidatePort,
      CancelReservationEscrowExecutionPort cancelExecutionPort,
      ReplayConfirmedReservationExecutionPort replayConfirmedPort,
      ReplayTerminatedReservationExecutionPort replayTerminatedPort) {
    return new ReservationWeb3BridgeRuntimeProfileValidator(
        provider(precheckPurchasePort),
        provider(prepareExecutionPort),
        provider(loadWalletPort),
        provider(loadPaymentConfigPort),
        provider(loadEscrowOrderPort),
        provider(loadExecutionWritePort),
        provider(loadExecutionStatePort),
        provider(loadExecutionResumePort),
        provider(loadExecutionCandidatePort),
        provider(cancelExecutionPort),
        provider(replayConfirmedPort),
        provider(replayTerminatedPort));
  }

  private static <T> ObjectProvider<T> provider(T bean) {
    return new ObjectProvider<>() {
      @Override
      public T getObject(Object... args) {
        return bean;
      }

      @Override
      public T getObject() {
        return bean;
      }

      @Override
      public T getIfAvailable() {
        return bean;
      }

      @Override
      public T getIfUnique() {
        return bean;
      }

      @Override
      public java.util.Iterator<T> iterator() {
        return bean == null
            ? java.util.Collections.emptyIterator()
            : java.util.Collections.singleton(bean).iterator();
      }

      @Override
      public java.util.stream.Stream<T> stream() {
        return bean == null ? java.util.stream.Stream.empty() : java.util.stream.Stream.of(bean);
      }
    };
  }
}
