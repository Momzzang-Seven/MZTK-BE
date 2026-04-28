package momzzangseven.mztkbe.modules.web3.treasury.application.port.out;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.junit.jupiter.api.Test;

class RecordTreasuryProvisionAuditPortAuditCommandTest {

  @Test
  void constructor_acceptsValidSuccessAndFailureCases() {
    assertThatCode(
            () ->
                new RecordTreasuryProvisionAuditPort.AuditCommand(
                    1L, "0x" + "a".repeat(40), true, null))
        .doesNotThrowAnyException();

    assertThatCode(
            () ->
                new RecordTreasuryProvisionAuditPort.AuditCommand(
                    1L, "0x" + "a".repeat(40), false, "RPC_ERROR"))
        .doesNotThrowAnyException();

    assertThatCode(() -> new RecordTreasuryProvisionAuditPort.AuditCommand(1L, null, true, null))
        .doesNotThrowAnyException();

    assertThatCode(() -> new RecordTreasuryProvisionAuditPort.AuditCommand(1L, " ", true, null))
        .doesNotThrowAnyException();
  }

  @Test
  void constructor_rejectsInvalidOperatorId() {
    assertThatThrownBy(
            () -> new RecordTreasuryProvisionAuditPort.AuditCommand(null, null, true, null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("operatorId must be positive");

    assertThatThrownBy(
            () -> new RecordTreasuryProvisionAuditPort.AuditCommand(0L, null, true, null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("operatorId must be positive");
  }

  @Test
  void constructor_rejectsInvalidTreasuryAddress() {
    assertThatThrownBy(
            () -> new RecordTreasuryProvisionAuditPort.AuditCommand(1L, "not-address", true, null))
        .isInstanceOf(Web3InvalidInputException.class);
  }

  @Test
  void constructor_rejectsMissingFailureReasonWhenSuccessFalse() {
    assertThatThrownBy(
            () ->
                new RecordTreasuryProvisionAuditPort.AuditCommand(
                    1L, "0x" + "a".repeat(40), false, null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("failureReason is required");

    assertThatThrownBy(
            () ->
                new RecordTreasuryProvisionAuditPort.AuditCommand(
                    1L, "0x" + "a".repeat(40), false, " "))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("failureReason is required");
  }
}
