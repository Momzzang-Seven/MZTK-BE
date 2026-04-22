package momzzangseven.mztkbe.modules.web3.execution.infrastructure.adapter;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransferException;
import momzzangseven.mztkbe.modules.web3.eip7702.infrastructure.config.Eip7702Properties;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ValidateExecutionDraftPolicyPort;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.eip7702", name = "enabled", havingValue = "true")
public class ExecutionDraftPolicyValidatorAdapter implements ValidateExecutionDraftPolicyPort {

  private static final int FUNCTION_SELECTOR_LENGTH = 10;

  private final Eip7702Properties eip7702Properties;

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
    Set<String> blockedFunctionSelectors = resolveBlockedFunctionSelectors();
    for (ExecutionDraftCall call : calls) {
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
    }
  }

  private Set<String> resolveAllowedDelegateTargets() {
    Set<String> normalized =
        normalizeAddressSet(eip7702Properties.getExecution().getAllowedDelegateTargets());
    if (normalized.isEmpty()) {
      normalized =
          normalizeAddressSet(eip7702Properties.getExecution().getAllowedTargetContracts());
    }
    return normalized;
  }

  private Set<String> resolveAllowedCallTargets() {
    Set<String> normalized =
        normalizeAddressSet(eip7702Properties.getExecution().getAllowedCallTargets());
    if (normalized.isEmpty()) {
      normalized =
          normalizeAddressSet(eip7702Properties.getExecution().getAllowedTargetContracts());
    }
    return normalized;
  }

  private Set<String> resolveBlockedFunctionSelectors() {
    return eip7702Properties.getExecution().getBlockedFunctionSelectors().stream()
        .filter(selector -> selector != null && !selector.isBlank())
        .map(selector -> selector.trim().toLowerCase(Locale.ROOT))
        .collect(Collectors.toUnmodifiableSet());
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
    return normalized.substring(0, FUNCTION_SELECTOR_LENGTH);
  }
}
