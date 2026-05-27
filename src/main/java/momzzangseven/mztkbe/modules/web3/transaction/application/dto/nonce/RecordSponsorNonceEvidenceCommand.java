package momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceEvidenceSource;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceEvidenceType;

public record RecordSponsorNonceEvidenceCommand(
    long chainId,
    String fromAddress,
    long nonce,
    SponsorNonceEvidenceType evidenceType,
    SponsorNonceEvidenceSource source,
    String providerAlias,
    String payloadJson,
    Long relatedEvidenceId,
    String createdBy,
    LocalDateTime observedAt) {

  public RecordSponsorNonceEvidenceCommand {
    if (chainId <= 0) {
      throw new Web3InvalidInputException("chainId must be positive");
    }
    fromAddress = EvmAddress.of(fromAddress).value();
    if (nonce < 0) {
      throw new Web3InvalidInputException("nonce must be >= 0");
    }
    if (evidenceType == null) {
      throw new Web3InvalidInputException("evidenceType is required");
    }
    if (source == null) {
      throw new Web3InvalidInputException("source is required");
    }
    if (payloadJson == null || payloadJson.isBlank()) {
      throw new Web3InvalidInputException("payloadJson is required");
    }
    if (observedAt == null) {
      throw new Web3InvalidInputException("observedAt is required");
    }
  }
}
