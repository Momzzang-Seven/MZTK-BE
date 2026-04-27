package momzzangseven.mztkbe.modules.user.api.controller;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.user.api.dto.GetUserLeaderboardResponseDTO;
import momzzangseven.mztkbe.modules.user.application.dto.GetUserLeaderboardResult;
import momzzangseven.mztkbe.modules.user.application.port.in.GetUserLeaderboardUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public controller for user leaderboard queries. */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserLeaderboardController {

  private final GetUserLeaderboardUseCase getUserLeaderboardUseCase;

  @GetMapping("/leaderboard")
  public ResponseEntity<ApiResponse<GetUserLeaderboardResponseDTO>> getUserLeaderboard() {
    GetUserLeaderboardResult result = getUserLeaderboardUseCase.execute();
    return ResponseEntity.ok(ApiResponse.success(GetUserLeaderboardResponseDTO.from(result)));
  }
}
