package momzzangseven.mztkbe.modules.level.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.level.domain.model.XpType;

public record GrantXpRequestDTO(
    @NotNull(message = "type is required") XpType type,
    @NotBlank(message = "idempotencyKey is required")
        @Size(max = 200, message = "idempotencyKey must be <= 200 characters")
        @Pattern(
            regexp = "^[A-Za-z0-9][A-Za-z0-9:_\\-.]{0,199}$",
            message = "idempotencyKey contains invalid characters")
        String idempotencyKey,
    String sourceRef,
    LocalDateTime occurredAt) {

  public LocalDateTime resolvedOccurredAt() {
    return occurredAt == null
        ? java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Seoul")).toLocalDateTime()
        : occurredAt;
  }
}
