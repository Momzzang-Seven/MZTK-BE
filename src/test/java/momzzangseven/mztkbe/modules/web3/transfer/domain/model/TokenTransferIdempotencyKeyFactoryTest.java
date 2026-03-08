package momzzangseven.mztkbe.modules.web3.transfer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import org.junit.jupiter.api.Test;

class TokenTransferIdempotencyKeyFactoryTest {

  @Test
  void create_andParseDomainType_workTogether() {
    String key =
        TokenTransferIdempotencyKeyFactory.create(DomainReferenceType.QUESTION_REWARD, 9L, "101");

    assertThat(key).isEqualTo("domain:QUESTION_REWARD:101:9");
    assertThat(TokenTransferIdempotencyKeyFactory.parseDomainType(key))
        .isEqualTo(DomainReferenceType.QUESTION_REWARD);
  }

  @Test
  void create_throws_whenReferenceIdBlank() {
    assertThatThrownBy(
            () ->
                TokenTransferIdempotencyKeyFactory.create(
                    DomainReferenceType.QUESTION_REWARD, 9L, " "))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("referenceId is required");
  }

  @Test
  void create_throws_whenDomainTypeNull() {
    assertThatThrownBy(() -> TokenTransferIdempotencyKeyFactory.create(null, 9L, "101"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("domainType is required");
  }

  @Test
  void create_throws_whenFromUserIdInvalid() {
    assertThatThrownBy(
            () ->
                TokenTransferIdempotencyKeyFactory.create(
                    DomainReferenceType.QUESTION_REWARD, 0L, "101"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("fromUserId must be positive");
  }

  @Test
  void parseDomainType_returnsNull_whenMalformed() {
    assertThat(TokenTransferIdempotencyKeyFactory.parseDomainType("bad:key")).isNull();
    assertThat(TokenTransferIdempotencyKeyFactory.parseDomainType("domain:UNKNOWN:1:2")).isNull();
  }
}
