package momzzangseven.mztkbe.modules.web3.execution.api.dto;

public record ExecuteExecutionIntentRequestDTO(
    String authorizationSignature, String submitSignature, String signedRawTransaction) {}
