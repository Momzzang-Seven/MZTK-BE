package momzzangseven.mztkbe.modules.web3.transaction.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.ReserveNoncePort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.UpdateTransactionPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxFailureReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservedNonceCompensatorTest {

  private static final Long TX_ID = 42L;
  private static final String FROM_ADDRESS = "0x" + "a".repeat(40);
  private static final long RESERVED_NONCE = 7L;

  @Mock private UpdateTransactionPort updateTransactionPort;
  @Mock private ReserveNoncePort reserveNoncePort;

  private ReservedNonceCompensator compensator;

  @BeforeEach
  void setUp() {
    compensator = new ReservedNonceCompensator(updateTransactionPort, reserveNoncePort);
  }

  @Test
  void compensate_invokesClearScheduleReleaseInOrder_onTerminalReason() {
    when(reserveNoncePort.releaseNonce(FROM_ADDRESS, RESERVED_NONCE)).thenReturn(true);

    compensator.compensate(
        TX_ID, FROM_ADDRESS, RESERVED_NONCE, Web3TxFailureReason.KMS_SIGN_FAILED_TERMINAL);

    InOrder order = inOrder(updateTransactionPort, reserveNoncePort);
    order.verify(updateTransactionPort).clearNonce(TX_ID);
    order
        .verify(updateTransactionPort)
        .scheduleRetry(
            eq(TX_ID), eq(Web3TxFailureReason.KMS_SIGN_FAILED_TERMINAL.code()), isNull());
    order.verify(reserveNoncePort).releaseNonce(FROM_ADDRESS, RESERVED_NONCE);
  }

  @Test
  void compensate_doesNotShortCircuit_whenReleaseReturnsFalse() {
    when(reserveNoncePort.releaseNonce(FROM_ADDRESS, RESERVED_NONCE)).thenReturn(false);

    compensator.compensate(
        TX_ID, FROM_ADDRESS, RESERVED_NONCE, Web3TxFailureReason.SIGNATURE_INVALID);

    verify(updateTransactionPort).clearNonce(TX_ID);
    verify(updateTransactionPort)
        .scheduleRetry(eq(TX_ID), eq(Web3TxFailureReason.SIGNATURE_INVALID.code()), isNull());
    verify(reserveNoncePort).releaseNonce(FROM_ADDRESS, RESERVED_NONCE);
  }

  @Test
  void compensate_throws_whenTransactionIdNullOrNonPositive() {
    assertThatThrownBy(
            () ->
                compensator.compensate(
                    null,
                    FROM_ADDRESS,
                    RESERVED_NONCE,
                    Web3TxFailureReason.KMS_SIGN_FAILED_TERMINAL))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("transactionId must be positive");

    assertThatThrownBy(
            () ->
                compensator.compensate(
                    0L, FROM_ADDRESS, RESERVED_NONCE, Web3TxFailureReason.KMS_SIGN_FAILED_TERMINAL))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("transactionId must be positive");

    verifyNoInteractions(updateTransactionPort, reserveNoncePort);
  }

  @Test
  void compensate_throws_whenFromAddressBlank() {
    assertThatThrownBy(
            () ->
                compensator.compensate(
                    TX_ID, " ", RESERVED_NONCE, Web3TxFailureReason.KMS_SIGN_FAILED_TERMINAL))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("fromAddress is required");

    verifyNoInteractions(updateTransactionPort, reserveNoncePort);
  }

  @Test
  void compensate_throws_whenNonceNegative() {
    assertThatThrownBy(
            () ->
                compensator.compensate(
                    TX_ID, FROM_ADDRESS, -1L, Web3TxFailureReason.KMS_SIGN_FAILED_TERMINAL))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("nonce must be >= 0");

    verifyNoInteractions(updateTransactionPort, reserveNoncePort);
  }

  @Test
  void compensate_throws_whenTerminalReasonNull() {
    assertThatThrownBy(() -> compensator.compensate(TX_ID, FROM_ADDRESS, RESERVED_NONCE, null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("terminalReason is required");

    verifyNoInteractions(updateTransactionPort, reserveNoncePort);
  }

  @Test
  void compensate_throws_whenReasonIsRetryable() {
    assertThatThrownBy(
            () ->
                compensator.compensate(
                    TX_ID, FROM_ADDRESS, RESERVED_NONCE, Web3TxFailureReason.KMS_SIGN_FAILED))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("terminalReason must be non-retryable");

    verifyNoInteractions(updateTransactionPort, reserveNoncePort);
  }
}
