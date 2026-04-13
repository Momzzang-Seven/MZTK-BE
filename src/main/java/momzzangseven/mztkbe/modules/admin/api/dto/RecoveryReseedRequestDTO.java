package momzzangseven.mztkbe.modules.admin.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import momzzangseven.mztkbe.modules.admin.application.dto.RecoveryReseedCommand;

/** Request DTO for recovery reseed. */
@Getter
@NoArgsConstructor
public class RecoveryReseedRequestDTO {

  @NotBlank(message = "Recovery anchor is required")
  private String recoveryAnchor;

  /** Convert to application command with source IP. */
  public RecoveryReseedCommand toCommand(String sourceIp) {
    return new RecoveryReseedCommand(recoveryAnchor, sourceIp);
  }
}
