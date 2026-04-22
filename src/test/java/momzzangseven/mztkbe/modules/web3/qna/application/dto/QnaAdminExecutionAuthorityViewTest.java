package momzzangseven.mztkbe.modules.web3.qna.application.dto;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.ExecutionSignerCapabilityView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("QnaAdminExecutionAuthorityView 단위 테스트")
class QnaAdminExecutionAuthorityViewTest {

  @Test
  @DisplayName("REGISTERED 상태면 relayerRegistered=true 여야 한다")
  void rejectsRegisteredStatusWhenRelayerRegisteredIsFalse() {
    assertThatThrownBy(
            () ->
                new QnaAdminExecutionAuthorityView(
                    ExecutionSignerCapabilityView.ready(
                        "sponsor-treasury", "0x" + "1".repeat(40)),
                    false,
                    QnaAdminRelayerRegistrationStatus.REGISTERED,
                    false,
                    "SERVER_RELAYER_ONLY"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("relayerRegistered must be true");
  }

  @Test
  @DisplayName("signable signer 는 UNCHECKED relayer 상태를 가질 수 없다")
  void rejectsUncheckedStatusWhenSignerIsAvailable() {
    assertThatThrownBy(
            () ->
                new QnaAdminExecutionAuthorityView(
                    ExecutionSignerCapabilityView.ready(
                        "sponsor-treasury", "0x" + "1".repeat(40)),
                    false,
                    QnaAdminRelayerRegistrationStatus.UNCHECKED,
                    false,
                    "SERVER_RELAYER_ONLY"))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("must be checked");
  }

  @Test
  @DisplayName("REGISTERED 조합은 정상 생성된다")
  void allowsConsistentRegisteredState() {
    assertThatCode(
            () ->
                new QnaAdminExecutionAuthorityView(
                    ExecutionSignerCapabilityView.ready(
                        "sponsor-treasury", "0x" + "1".repeat(40)),
                    true,
                    QnaAdminRelayerRegistrationStatus.REGISTERED,
                    false,
                    "SERVER_RELAYER_ONLY"))
        .doesNotThrowAnyException();
  }
}
