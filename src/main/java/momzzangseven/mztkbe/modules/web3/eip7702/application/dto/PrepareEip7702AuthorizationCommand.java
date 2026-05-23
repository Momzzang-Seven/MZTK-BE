package momzzangseven.mztkbe.modules.web3.eip7702.application.dto;

public record PrepareEip7702AuthorizationCommand(
    long chainId, String delegateTarget, String authorityAddress) {}
