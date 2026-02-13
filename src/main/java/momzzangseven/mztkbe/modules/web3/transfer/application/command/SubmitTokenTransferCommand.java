package momzzangseven.mztkbe.modules.web3.transfer.application.command;

public record SubmitTokenTransferCommand(
    Long userId, String prepareId, String authorizationSignature, String executionSignature) {}
