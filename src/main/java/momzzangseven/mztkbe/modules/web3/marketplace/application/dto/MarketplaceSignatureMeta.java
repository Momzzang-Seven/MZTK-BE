package momzzangseven.mztkbe.modules.web3.marketplace.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/** Server-signature timing metadata for marketplace server-signed escrow calls. */
public record MarketplaceSignatureMeta(Long signedAt, Long signatureExpiresAt) {

  public MarketplaceSignatureMeta {
    if (signedAt == null || signedAt <= 0) {
      throw new Web3InvalidInputException("signedAt must be positive");
    }
    if (signatureExpiresAt == null || signatureExpiresAt <= signedAt) {
      throw new Web3InvalidInputException("signatureExpiresAt must be after signedAt");
    }
  }
}
