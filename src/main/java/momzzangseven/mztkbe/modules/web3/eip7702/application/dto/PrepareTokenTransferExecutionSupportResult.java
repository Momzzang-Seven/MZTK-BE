package momzzangseven.mztkbe.modules.web3.eip7702.application.dto;

public record PrepareTokenTransferExecutionSupportResult(
    long authorityNonce, String authorizationPayloadHash, String transferData) {}
