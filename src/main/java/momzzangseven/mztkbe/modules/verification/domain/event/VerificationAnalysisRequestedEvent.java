package momzzangseven.mztkbe.modules.verification.domain.event;

/** Event emitted after a verification request is persisted and ready for async analysis. */
public record VerificationAnalysisRequestedEvent(Long verificationRequestId) {}
