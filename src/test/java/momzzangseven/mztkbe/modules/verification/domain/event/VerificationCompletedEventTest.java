package momzzangseven.mztkbe.modules.verification.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class VerificationCompletedEventTest {

  @Test
  void exposesCompletionPayload() {
    LocalDateTime occurredAt = LocalDateTime.of(2026, 3, 8, 12, 0);
    VerificationCompletedEvent event =
        new VerificationCompletedEvent("verification-123", 7L, occurredAt);

    assertThat(event.verificationId()).isEqualTo("verification-123");
    assertThat(event.userId()).isEqualTo(7L);
    assertThat(event.occurredAt()).isEqualTo(occurredAt);
  }
}
