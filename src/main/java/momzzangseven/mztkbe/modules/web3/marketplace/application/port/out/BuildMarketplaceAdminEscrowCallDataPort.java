package momzzangseven.mztkbe.modules.web3.marketplace.application.port.out;

/** Encodes marketplace admin escrow calldata. */
public interface BuildMarketplaceAdminEscrowCallDataPort {

  String encodeAdminRefund(String orderKey);

  String encodeAdminSettle(String orderKey);
}
