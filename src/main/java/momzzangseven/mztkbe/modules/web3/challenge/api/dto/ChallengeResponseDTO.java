package momzzangseven.mztkbe.modules.web3.challenge.api.dto;

import momzzangseven.mztkbe.modules.web3.challenge.application.dto.CreateChallengeResult;

public record ChallengeResponseDTO(String nonce, String message, int expiresIn) {

  public static ChallengeResponseDTO from(CreateChallengeResult result) {
    return new ChallengeResponseDTO(result.nonce(), result.message(), result.expiresIn());
  }
}
