package momzzangseven.mztkbe.modules.web3.transfer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DomainReferenceType unit test")
class DomainReferenceTypeTest {

  @Test
  @DisplayName("toTokenTransferReferenceType maps domain type")
  void toTokenTransferReferenceType_mapsCorrectly() {
    assertThat(DomainReferenceType.QUESTION_REWARD.toTokenTransferReferenceType())
        .isEqualTo(TokenTransferReferenceType.USER_TO_USER);
    assertThat(DomainReferenceType.LEVEL_UP_REWARD.toTokenTransferReferenceType())
        .isEqualTo(TokenTransferReferenceType.SERVER_TO_USER);
  }

  @Test
  @DisplayName("isUserPrepareSupported is true only for QUESTION_REWARD")
  void isUserPrepareSupported_returnsExpectedValue() {
    assertThat(DomainReferenceType.QUESTION_REWARD.isUserPrepareSupported()).isTrue();
    assertThat(DomainReferenceType.LEVEL_UP_REWARD.isUserPrepareSupported()).isFalse();
  }

  @Test
  @DisplayName("valueOf rejects invalid name")
  void valueOf_invalidName_throwsException() {
    assertThatThrownBy(() -> DomainReferenceType.valueOf("__INVALID__"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
