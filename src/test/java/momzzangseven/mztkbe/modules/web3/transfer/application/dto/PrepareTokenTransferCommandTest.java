package momzzangseven.mztkbe.modules.web3.transfer.application.dto;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.DomainReferenceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PrepareTokenTransferCommand unit test")
class PrepareTokenTransferCommandTest {

  @Test
  @DisplayName("validate rejects non-positive userId")
  void validate_invalidUserId_throwsException() {
    PrepareTokenTransferCommand command =
        new PrepareTokenTransferCommand(
            0L, DomainReferenceType.QUESTION_REWARD, "101", 2L, BigInteger.ONE);

    assertThatThrownBy(command::validate)
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("userId must be positive");
  }

  @Test
  @DisplayName("validate rejects null domain type")
  void validate_nullDomainType_throwsException() {
    PrepareTokenTransferCommand command =
        new PrepareTokenTransferCommand(1L, null, "101", 2L, BigInteger.ONE);

    assertThatThrownBy(command::validate)
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("domainType is required");
  }

  @Test
  @DisplayName("validate rejects too long reference ID")
  void validate_referenceIdTooLong_throwsException() {
    PrepareTokenTransferCommand command =
        new PrepareTokenTransferCommand(
            1L, DomainReferenceType.QUESTION_REWARD, "a".repeat(101), 2L, BigInteger.ONE);

    assertThatThrownBy(command::validate)
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("referenceId length must be <= 100");
  }

  @Test
  @DisplayName("validate rejects blank reference ID")
  void validate_referenceIdBlank_throwsException() {
    PrepareTokenTransferCommand command =
        new PrepareTokenTransferCommand(
            1L, DomainReferenceType.QUESTION_REWARD, " ", 2L, BigInteger.ONE);

    assertThatThrownBy(command::validate)
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("referenceId is required");
  }

  @Test
  @DisplayName("validate rejects non-positive amount")
  void validate_nonPositiveAmount_throwsException() {
    PrepareTokenTransferCommand command =
        new PrepareTokenTransferCommand(
            1L, DomainReferenceType.QUESTION_REWARD, "101", 2L, BigInteger.ZERO);

    assertThatThrownBy(command::validate)
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("amountWei must be > 0");
  }

  @Test
  @DisplayName("validate passes for valid payload")
  void validate_validCommand_doesNotThrow() {
    PrepareTokenTransferCommand command =
        new PrepareTokenTransferCommand(
            1L, DomainReferenceType.QUESTION_REWARD, "101", 2L, BigInteger.TEN);

    assertThatCode(command::validate).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("validate rejects non-positive toUserId")
  void validate_nonPositiveToUserId_throwsException() {
    PrepareTokenTransferCommand command =
        new PrepareTokenTransferCommand(
            1L, DomainReferenceType.QUESTION_REWARD, "101", 0L, BigInteger.ONE);

    assertThatThrownBy(command::validate)
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("toUserId must be positive");
  }
}
