package momzzangseven.mztkbe.modules.web3.execution.infrastructure.adapter;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransferException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ValidateExecutionDraftPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.infrastructure.config.ExecutionEip7702Properties;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.eip7702", name = "enabled", havingValue = "true")
public class ExecutionDraftPolicyValidatorAdapter implements ValidateExecutionDraftPolicyPort {

  private static final int FUNCTION_SELECTOR_LENGTH = 10;
  private static final int ABI_WORD_HEX_LENGTH = 64;
  private static final int APPROVE_DATA_LENGTH =
      FUNCTION_SELECTOR_LENGTH + (ABI_WORD_HEX_LENGTH * 2);
  private static final String APPROVE_SELECTOR = "0x095ea7b3";
  private static final String MAX_UINT256_HEX = "f".repeat(ABI_WORD_HEX_LENGTH);

  private final ExecutionEip7702Properties executionEip7702Properties;

  @Override
  public void validate(String delegateTarget, List<ExecutionDraftCall> calls) {
    String normalizedDelegateTarget = normalizeAddress(delegateTarget);
    Set<String> allowedDelegateTargets = resolveAllowedDelegateTargets();
    if (!allowedDelegateTargets.contains(normalizedDelegateTarget)) {
      throw new Web3TransferException(
          ErrorCode.DELEGATE_NOT_ALLOWLISTED,
          "delegate target is not allowlisted: " + normalizedDelegateTarget,
          false);
    }

    Set<String> allowedCallTargets = resolveAllowedCallTargets();
    Set<String> allowedApproveSpenders = resolveAllowedApproveSpenders();
    Set<String> blockedFunctionSelectors = resolveBlockedFunctionSelectors();
    for (ExecutionDraftCall call : calls) {
      if (call.valueWei().signum() != 0) {
        throw new Web3InvalidInputException("EIP-7702 execution call value must be zero");
      }

      String normalizedCallTarget = normalizeAddress(call.toAddress());
      if (!allowedCallTargets.contains(normalizedCallTarget)) {
        throw new Web3TransferException(
            ErrorCode.DELEGATE_NOT_ALLOWLISTED,
            "execution call target is not allowlisted: " + normalizedCallTarget,
            false);
      }

      String selector = extractFunctionSelector(call.data());
      if (blockedFunctionSelectors.contains(selector)) {
        throw new Web3InvalidInputException("blocked function selector: " + selector);
      }
      if (APPROVE_SELECTOR.equals(selector)) {
        validateMaxApprove(call.data(), allowedApproveSpenders);
      }
    }
  }

  private Set<String> resolveAllowedDelegateTargets() {
    Set<String> normalized =
        normalizeAddressSet(executionEip7702Properties.getExecution().getAllowedDelegateTargets());
    if (normalized.isEmpty()) {
      normalized =
          normalizeAddressSet(
              executionEip7702Properties.getExecution().getAllowedTargetContracts());
    }
    return normalized;
  }

  private Set<String> resolveAllowedCallTargets() {
    Set<String> normalized =
        normalizeAddressSet(executionEip7702Properties.getExecution().getAllowedCallTargets());
    if (normalized.isEmpty()) {
      normalized =
          normalizeAddressSet(
              executionEip7702Properties.getExecution().getAllowedTargetContracts());
    }
    return normalized;
  }

  private Set<String> resolveBlockedFunctionSelectors() {
    return executionEip7702Properties.getExecution().getBlockedFunctionSelectors().stream()
        .filter(selector -> selector != null && !selector.isBlank())
        .map(selector -> selector.trim().toLowerCase(Locale.ROOT))
        .collect(Collectors.toUnmodifiableSet());
  }

  private Set<String> resolveAllowedApproveSpenders() {
    return normalizeAddressSet(
        executionEip7702Properties.getExecution().getAllowedApproveSpenders());
  }

  private Set<String> normalizeAddressSet(List<String> rawAddresses) {
    if (rawAddresses == null) {
      return Set.of();
    }
    return rawAddresses.stream()
        .filter(address -> address != null && !address.isBlank())
        .map(this::normalizeAddress)
        .collect(Collectors.toUnmodifiableSet());
  }

  private String normalizeAddress(String rawAddress) {
    return EvmAddress.of(rawAddress).value();
  }

  private String extractFunctionSelector(String data) {
    String normalized = data.trim().toLowerCase(Locale.ROOT);
    if (!normalized.startsWith("0x")) {
      throw new Web3InvalidInputException("execution call data must start with 0x");
    }
    if (normalized.length() < FUNCTION_SELECTOR_LENGTH) {
      throw new Web3InvalidInputException("execution call data is shorter than function selector");
    }
    if ((normalized.length() - 2) % 2 != 0) {
      throw new Web3InvalidInputException("execution call data hex length must be even");
    }
    if (!isHex(normalized.substring(2))) {
      throw new Web3InvalidInputException("execution call data must be hex");
    }
    return normalized.substring(0, FUNCTION_SELECTOR_LENGTH);
  }

  private boolean isHex(String value) {
    for (int i = 0; i < value.length(); i++) {
      char current = value.charAt(i);
      boolean isHexDigit = (current >= '0' && current <= '9') || (current >= 'a' && current <= 'f');
      if (!isHexDigit) {
        return false;
      }
    }
    return true;
  }

  private void validateMaxApprove(String data, Set<String> allowedApproveSpenders) {
    String normalized = data.trim().toLowerCase(Locale.ROOT);
    if (normalized.length() != APPROVE_DATA_LENGTH) {
      throw new Web3InvalidInputException("approve calldata length is invalid");
    }
    String spender = normalizeAddress("0x" + normalized.substring(34, 74));
    if (!allowedApproveSpenders.contains(spender)) {
      throw new Web3InvalidInputException("approve spender is not allowlisted: " + spender);
    }
    String amount = normalized.substring(74, 138);
    if (!MAX_UINT256_HEX.equals(amount)) {
      throw new Web3InvalidInputException("approve amount must be uint256 max");
    }
  }
}
