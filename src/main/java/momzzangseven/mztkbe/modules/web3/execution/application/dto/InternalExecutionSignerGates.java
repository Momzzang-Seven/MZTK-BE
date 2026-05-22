package momzzangseven.mztkbe.modules.web3.execution.application.dto;

import java.util.EnumMap;
import java.util.Map;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;

/**
 * Preflighted signer gates keyed by internal execution action type.
 *
 * <p>Built outside the transactional issuer boundary. The delegate picks the gate that matches the
 * claimed intent after the FOR UPDATE claim, avoiding a sponsor-only assumption for marketplace
 * admin actions.
 */
public record InternalExecutionSignerGates(Map<ExecutionActionType, SponsorWalletGate> gates) {

  public InternalExecutionSignerGates {
    if (gates == null || gates.isEmpty()) {
      throw new Web3InvalidInputException("internal execution signer gates required");
    }
    EnumMap<ExecutionActionType, SponsorWalletGate> copy = new EnumMap<>(ExecutionActionType.class);
    gates.forEach(
        (actionType, gate) -> {
          if (actionType == null) {
            throw new Web3InvalidInputException("internal execution action type required");
          }
          if (gate == null) {
            throw new Web3InvalidInputException("internal execution signer gate required");
          }
          copy.put(actionType, gate);
        });
    gates = Map.copyOf(copy);
  }

  public SponsorWalletGate gateFor(ExecutionActionType actionType) {
    SponsorWalletGate gate = gates.get(actionType);
    if (gate == null) {
      throw new Web3InvalidInputException("internal execution signer gate is missing");
    }
    return gate;
  }
}
