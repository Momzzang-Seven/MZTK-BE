package momzzangseven.mztkbe.modules.verification.domain.event;

import java.time.LocalDateTime;

/** Event emitted after a verification is finalized successfully. */
public record VerificationCompletedEvent(
    String verificationId, Long userId, LocalDateTime occurredAt) {}
