package momzzangseven.mztkbe.modules.level.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.level.domain.model.XpType;

public record GrantXpRequestDTO(
    @NotNull(message = "type is required") XpType type,
    @NotBlank(message = "idempotencyKey is required") String idempotencyKey,
    String sourceRef,
    LocalDateTime occurredAt) {

  public LocalDateTime resolvedOccurredAt() {
    return occurredAt == null ? LocalDateTime.now() : occurredAt;
  }
}
