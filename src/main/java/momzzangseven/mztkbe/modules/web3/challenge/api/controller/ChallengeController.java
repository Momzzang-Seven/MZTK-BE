package momzzangseven.mztkbe.modules.web3.challenge.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.web3.challenge.api.dto.ChallengeResponseDTO;
import momzzangseven.mztkbe.modules.web3.challenge.api.dto.CreateChallengeRequestDTO;
import momzzangseven.mztkbe.modules.web3.challenge.application.dto.CreateChallengeCommand;
import momzzangseven.mztkbe.modules.web3.challenge.application.dto.CreateChallengeResult;
import momzzangseven.mztkbe.modules.web3.challenge.application.port.in.CreateChallengeUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Challenge API Controller
 *
 * <p>Endpoints: - POST /web3/challenges: 챌린지 발급
 */
@Slf4j
@RestController
@RequestMapping("/web3/challenges")
@RequiredArgsConstructor
public class ChallengeController {

  private final CreateChallengeUseCase createChallengeUseCase;

  /**
   * Create a challenge
   *
   * <p>- Access Token required - make EIP-4361 message
   */
  @PostMapping
  public ResponseEntity<ApiResponse<ChallengeResponseDTO>> createChallenge(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody CreateChallengeRequestDTO request) {
    userId = requireUserId(userId);

    CreateChallengeCommand command =
        new CreateChallengeCommand(userId, request.purpose(), request.walletAddress());

    CreateChallengeResult result = createChallengeUseCase.execute(command);

    ChallengeResponseDTO response = ChallengeResponseDTO.from(result);

    return ResponseEntity.ok(ApiResponse.success(response));
  }

  private Long requireUserId(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
    return userId;
  }
}
