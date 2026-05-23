package momzzangseven.mztkbe.modules.web3.marketplace.application.port.out;

public interface CheckMarketplaceAdminRelayerRegistrationPort {

  boolean isRegistered(String signerAddress);
}
