package momzzangseven.mztkbe.modules.admin.api.dto;

import momzzangseven.mztkbe.modules.admin.application.dto.RecoveryReseedResult;

/** Response DTO for recovery reseed. */
public record RecoveryReseedResponseDTO(String deliveredVia, int newSeedCount) {

  public static RecoveryReseedResponseDTO from(RecoveryReseedResult result) {
    return new RecoveryReseedResponseDTO(result.deliveredVia(), result.newSeedCount());
  }
}
