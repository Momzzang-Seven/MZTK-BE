package momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceEvidenceSource;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceEvidenceType;

public record SponsorNonceEvidenceView(
    Long id,
    long chainId,
    String fromAddress,
    long nonce,
    SponsorNonceEvidenceType evidenceType,
    SponsorNonceEvidenceSource source,
    String providerAlias,
    String payloadJson,
    Long relatedEvidenceId,
    String createdBy,
    LocalDateTime observedAt,
    LocalDateTime createdAt) {}
