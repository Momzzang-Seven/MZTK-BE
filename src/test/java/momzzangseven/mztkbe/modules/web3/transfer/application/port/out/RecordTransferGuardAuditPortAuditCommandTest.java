package momzzangseven.mztkbe.modules.web3.transfer.application.port.out;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.DomainReferenceType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TransferGuardAuditReason;
import org.junit.jupiter.api.Test;

class RecordTransferGuardAuditPortAuditCommandTest {

  @Test
  void constructor_acceptsValidCommand() {
    assertThatCode(
            () ->
                new RecordTransferGuardAuditPort.AuditCommand(
                    1L,
                    "127.0.0.1",
                    DomainReferenceType.QUESTION_REWARD,
                    "101",
                    "prepare-1",
                    TransferGuardAuditReason.REQUEST_RESOLVED_MISMATCH,
                    2L,
                    3L,
                    BigInteger.TEN,
                    BigInteger.ONE))
        .doesNotThrowAnyException();
  }

  @Test
  void constructor_rejectsNullOrNonPositiveUserId() {
    assertThatThrownBy(
            () -> commandWith(null, "127.0.0.1", DomainReferenceType.QUESTION_REWARD, "101"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("userId must be positive");

    assertThatThrownBy(
            () -> commandWith(0L, "127.0.0.1", DomainReferenceType.QUESTION_REWARD, "101"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("userId must be positive");
  }

  @Test
  void constructor_rejectsNullOrBlankClientIp() {
    assertThatThrownBy(() -> commandWith(1L, null, DomainReferenceType.QUESTION_REWARD, "101"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("clientIp is required");

    assertThatThrownBy(() -> commandWith(1L, " ", DomainReferenceType.QUESTION_REWARD, "101"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("clientIp is required");
  }

  @Test
  void constructor_rejectsNullDomainType() {
    assertThatThrownBy(() -> commandWith(1L, "127.0.0.1", null, "101"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("domainType is required");
  }

  @Test
  void constructor_rejectsNullOrBlankReferenceId() {
    assertThatThrownBy(
            () -> commandWith(1L, "127.0.0.1", DomainReferenceType.QUESTION_REWARD, null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("referenceId is required");

    assertThatThrownBy(() -> commandWith(1L, "127.0.0.1", DomainReferenceType.QUESTION_REWARD, " "))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("referenceId is required");
  }

  @Test
  void constructor_rejectsNullReason() {
    assertThatThrownBy(
            () ->
                new RecordTransferGuardAuditPort.AuditCommand(
                    1L,
                    "127.0.0.1",
                    DomainReferenceType.QUESTION_REWARD,
                    "101",
                    "prepare-1",
                    null,
                    2L,
                    3L,
                    BigInteger.TEN,
                    BigInteger.ONE))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("reason is required");
  }

  @Test
  void constructor_rejectsNullOrNonPositiveRequestedAmountWei() {
    assertThatThrownBy(
            () ->
                new RecordTransferGuardAuditPort.AuditCommand(
                    1L,
                    "127.0.0.1",
                    DomainReferenceType.QUESTION_REWARD,
                    "101",
                    "prepare-1",
                    TransferGuardAuditReason.REQUEST_RESOLVED_MISMATCH,
                    2L,
                    3L,
                    null,
                    BigInteger.ONE))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("requestedAmountWei must be > 0");

    assertThatThrownBy(
            () ->
                new RecordTransferGuardAuditPort.AuditCommand(
                    1L,
                    "127.0.0.1",
                    DomainReferenceType.QUESTION_REWARD,
                    "101",
                    "prepare-1",
                    TransferGuardAuditReason.REQUEST_RESOLVED_MISMATCH,
                    2L,
                    3L,
                    BigInteger.ZERO,
                    BigInteger.ONE))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("requestedAmountWei must be > 0");
  }

  private RecordTransferGuardAuditPort.AuditCommand commandWith(
      Long userId, String clientIp, DomainReferenceType domainType, String referenceId) {
    return new RecordTransferGuardAuditPort.AuditCommand(
        userId,
        clientIp,
        domainType,
        referenceId,
        "prepare-1",
        TransferGuardAuditReason.REQUEST_RESOLVED_MISMATCH,
        2L,
        3L,
        BigInteger.TEN,
        BigInteger.ONE);
  }
}
