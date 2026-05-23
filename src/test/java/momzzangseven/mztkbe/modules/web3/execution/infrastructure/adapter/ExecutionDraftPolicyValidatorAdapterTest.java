package momzzangseven.mztkbe.modules.web3.execution.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransferException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.execution.infrastructure.config.ExecutionEip7702Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("ExecutionDraftPolicyValidatorAdapter unit test")
class ExecutionDraftPolicyValidatorAdapterTest {

  private static final String BATCH_IMPL = "0x" + "1".repeat(40);
  private static final String TOKEN_CONTRACT = "0x" + "2".repeat(40);
  private static final String QNA_CONTRACT = "0x" + "3".repeat(40);
  private static final String MARKETPLACE_CONTRACT = "0x" + "4".repeat(40);
  private static final String UNAUTHORIZED_SPENDER = "0x" + "5".repeat(40);

  private ExecutionEip7702Properties properties;
  private ExecutionDraftPolicyValidatorAdapter adapter;

  @BeforeEach
  void setUp() {
    properties = new ExecutionEip7702Properties();
    properties.getExecution().setAllowedDelegateTargets(List.of(BATCH_IMPL));
    properties
        .getExecution()
        .setAllowedCallTargets(List.of(TOKEN_CONTRACT, QNA_CONTRACT, MARKETPLACE_CONTRACT));
    properties
        .getExecution()
        .setAllowedApproveSpenders(List.of(QNA_CONTRACT, MARKETPLACE_CONTRACT));
    properties.getExecution().setBlockedFunctionSelectors(List.of("0x095ea7b3"));
    adapter = new ExecutionDraftPolicyValidatorAdapter(properties);
  }

  @Test
  @DisplayName("validate passes when delegate target and call target are allowlisted")
  void validate_passes_whenTargetsAreAllowlisted() {
    assertThatCode(
            () ->
                adapter.validate(
                    BATCH_IMPL,
                    List.of(
                        new ExecutionDraftCall(
                            TOKEN_CONTRACT, BigInteger.ZERO, "0xa9059cbb" + "0".repeat(128)))))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("validate throws when delegate target is not allowlisted")
  void validate_throws_whenDelegateTargetIsNotAllowlisted() {
    assertThatThrownBy(
            () ->
                adapter.validate(
                    "0x" + "9".repeat(40),
                    List.of(
                        new ExecutionDraftCall(
                            TOKEN_CONTRACT, BigInteger.ZERO, "0xa9059cbb" + "0".repeat(128)))))
        .isInstanceOf(Web3TransferException.class)
        .hasMessageContaining("delegate target is not allowlisted");
  }

  @Test
  @DisplayName("validate throws when execution call target is not allowlisted")
  void validate_throws_whenCallTargetIsNotAllowlisted() {
    assertThatThrownBy(
            () ->
                adapter.validate(
                    BATCH_IMPL,
                    List.of(
                        new ExecutionDraftCall(
                            "0x" + "8".repeat(40),
                            BigInteger.ZERO,
                            "0xa9059cbb" + "0".repeat(128)))))
        .isInstanceOf(Web3TransferException.class)
        .hasMessageContaining("execution call target is not allowlisted");
  }

  @Test
  @DisplayName("validate throws when selector is blocked")
  void validate_throws_whenSelectorIsBlocked() {
    assertThatThrownBy(
            () ->
                adapter.validate(
                    BATCH_IMPL,
                    List.of(
                        new ExecutionDraftCall(
                            TOKEN_CONTRACT, BigInteger.ZERO, "0x095ea7b3" + "0".repeat(128)))))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("blocked function selector");
  }

  @Test
  @DisplayName(
      "validate passes max approve when spender is allowlisted and selector is not blocked")
  void validate_passes_whenApproveUsesMaxAmountAndAllowlistedSpender() {
    properties.getExecution().setBlockedFunctionSelectors(List.of());

    assertThatCode(
            () ->
                adapter.validate(
                    BATCH_IMPL,
                    List.of(
                        new ExecutionDraftCall(
                            TOKEN_CONTRACT,
                            BigInteger.ZERO,
                            approveData(QNA_CONTRACT, maxWord())))))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("validate throws when approve spender is not allowlisted")
  void validate_throws_whenApproveSpenderIsNotAllowlisted() {
    properties.getExecution().setBlockedFunctionSelectors(List.of());

    assertThatThrownBy(
            () ->
                adapter.validate(
                    BATCH_IMPL,
                    List.of(
                        new ExecutionDraftCall(
                            TOKEN_CONTRACT,
                            BigInteger.ZERO,
                            approveData(UNAUTHORIZED_SPENDER, maxWord())))))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("approve spender is not allowlisted");
  }

  @Test
  @DisplayName("validate throws when approve spender allowlist is empty")
  void validate_throws_whenApproveSpenderAllowlistIsEmpty() {
    properties.getExecution().setBlockedFunctionSelectors(List.of());
    properties.getExecution().setAllowedApproveSpenders(List.of());

    assertThatThrownBy(
            () ->
                adapter.validate(
                    BATCH_IMPL,
                    List.of(
                        new ExecutionDraftCall(
                            TOKEN_CONTRACT,
                            BigInteger.ZERO,
                            approveData(QNA_CONTRACT, maxWord())))))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("approve spender is not allowlisted");
  }

  @Test
  @DisplayName("validate throws when approve amount is not uint256 max")
  void validate_throws_whenApproveAmountIsNotMax() {
    properties.getExecution().setBlockedFunctionSelectors(List.of());

    assertThatThrownBy(
            () ->
                adapter.validate(
                    BATCH_IMPL,
                    List.of(
                        new ExecutionDraftCall(
                            TOKEN_CONTRACT,
                            BigInteger.ZERO,
                            approveData(QNA_CONTRACT, "0".repeat(63) + "1")))))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("approve amount must be uint256 max");
  }

  @Test
  @DisplayName("validate throws when EIP-7702 batch call carries native value")
  void validate_throws_whenCallValueIsPositive() {
    assertThatThrownBy(
            () ->
                adapter.validate(
                    BATCH_IMPL,
                    List.of(
                        new ExecutionDraftCall(
                            TOKEN_CONTRACT, BigInteger.ONE, "0xa9059cbb" + "0".repeat(128)))))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("EIP-7702 execution call value must be zero");
  }

  @ParameterizedTest
  @ValueSource(strings = {"0xa9059czz", "0xa9059cbb0"})
  @DisplayName("validate throws when calldata is malformed hex")
  void validate_throws_whenCallDataIsMalformedHex(String malformedData) {
    assertThatThrownBy(
            () ->
                adapter.validate(
                    BATCH_IMPL,
                    List.of(
                        new ExecutionDraftCall(TOKEN_CONTRACT, BigInteger.ZERO, malformedData))))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("execution call data");
  }

  @Test
  @DisplayName("validate falls back to legacy allowed-target-contracts when split lists are empty")
  void validate_usesLegacyAllowlistFallback() {
    properties.getExecution().setAllowedDelegateTargets(List.of());
    properties.getExecution().setAllowedCallTargets(List.of());
    properties
        .getExecution()
        .setAllowedTargetContracts(List.of(BATCH_IMPL, TOKEN_CONTRACT, QNA_CONTRACT));

    assertThatCode(
            () ->
                adapter.validate(
                    BATCH_IMPL,
                    List.of(
                        new ExecutionDraftCall(
                            TOKEN_CONTRACT, BigInteger.ZERO, "0xa9059cbb" + "0".repeat(128)))))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName(
      "validate falls back to legacy allowlist when split lists contain only blank env placeholders")
  void validate_usesLegacyFallback_whenSplitListsOnlyContainBlankEntries() {
    properties.getExecution().setAllowedDelegateTargets(List.of(""));
    properties.getExecution().setAllowedCallTargets(List.of("", "   "));
    properties
        .getExecution()
        .setAllowedTargetContracts(List.of(BATCH_IMPL, TOKEN_CONTRACT, QNA_CONTRACT));

    assertThatCode(
            () ->
                adapter.validate(
                    BATCH_IMPL,
                    List.of(
                        new ExecutionDraftCall(
                            TOKEN_CONTRACT, BigInteger.ZERO, "0xa9059cbb" + "0".repeat(128)))))
        .doesNotThrowAnyException();
  }

  private String approveData(String spender, String amountWord) {
    return "0x095ea7b3" + "0".repeat(24) + spender.substring(2) + amountWord;
  }

  private String maxWord() {
    return "f".repeat(64);
  }
}
