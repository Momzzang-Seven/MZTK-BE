package momzzangseven.mztkbe.modules.web3.execution.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransferException;
import momzzangseven.mztkbe.modules.web3.eip7702.infrastructure.config.Eip7702Properties;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraftCall;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ExecutionDraftPolicyValidatorAdapter unit test")
class ExecutionDraftPolicyValidatorAdapterTest {

  private static final String BATCH_IMPL = "0x" + "1".repeat(40);
  private static final String TOKEN_CONTRACT = "0x" + "2".repeat(40);
  private static final String QNA_CONTRACT = "0x" + "3".repeat(40);
  private static final String MARKETPLACE_CONTRACT = "0x" + "4".repeat(40);

  private Eip7702Properties properties;
  private ExecutionDraftPolicyValidatorAdapter adapter;

  @BeforeEach
  void setUp() {
    properties = new Eip7702Properties();
    properties.getExecution().setAllowedDelegateTargets(List.of(BATCH_IMPL));
    properties
        .getExecution()
        .setAllowedCallTargets(List.of(TOKEN_CONTRACT, QNA_CONTRACT, MARKETPLACE_CONTRACT));
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
  @DisplayName("validate falls back to legacy allowlist when split lists contain only blank env placeholders")
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
}
