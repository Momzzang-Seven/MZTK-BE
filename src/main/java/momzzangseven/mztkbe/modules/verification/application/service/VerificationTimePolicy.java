package momzzangseven.mztkbe.modules.verification.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.verification.domain.vo.CompletedMethod;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VerificationTimePolicy {

  private final ZoneId appZoneId;
  private final Clock appClock;

  public LocalDate today() {
    return LocalDate.now(appClock);
  }

  public boolean isToday(Instant instant) {
    return instant.atZone(appZoneId).toLocalDate().equals(today());
  }

  public boolean isToday(LocalDate date) {
    return date != null && date.equals(today());
  }

  public CompletedMethod deriveCompletedMethod(String sourceRef) {
    if (sourceRef == null || sourceRef.isBlank()) {
      return CompletedMethod.UNKNOWN;
    }
    if (sourceRef.startsWith("location-verification:")) {
      return CompletedMethod.LOCATION;
    }
    if (sourceRef.startsWith("workout-photo-verification:")) {
      return CompletedMethod.WORKOUT_PHOTO;
    }
    if (sourceRef.startsWith("workout-record-verification:")) {
      return CompletedMethod.WORKOUT_RECORD;
    }
    return CompletedMethod.UNKNOWN;
  }
}
