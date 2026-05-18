package momzzangseven.mztkbe.modules.web3.eip7702.application.dto;

public record PrepareEip7702AuthorizationResult(
    long authorityNonce, String authorizationPayloadHash) {}
