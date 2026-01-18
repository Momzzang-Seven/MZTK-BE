package momzzangseven.mztkbe.modules.level.api.controller;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.level.api.dto.GetLevelPoliciesResponseDTO;
import momzzangseven.mztkbe.modules.level.api.dto.GetMyLevelResponseDTO;
import momzzangseven.mztkbe.modules.level.api.dto.GetMyLevelUpHistoriesResponseDTO;
import momzzangseven.mztkbe.modules.level.api.dto.GetMyXpLedgerResponseDTO;
import momzzangseven.mztkbe.modules.level.api.dto.LevelUpResponseDTO;
import momzzangseven.mztkbe.modules.level.application.dto.GetLevelPoliciesResult;
import momzzangseven.mztkbe.modules.level.application.dto.GetMyLevelResult;
import momzzangseven.mztkbe.modules.level.application.dto.GetMyLevelUpHistoriesResult;
import momzzangseven.mztkbe.modules.level.application.dto.GetMyXpLedgerResult;
import momzzangseven.mztkbe.modules.level.application.dto.LevelUpCommand;
import momzzangseven.mztkbe.modules.level.application.dto.LevelUpResult;
import momzzangseven.mztkbe.modules.level.application.port.in.GetLevelPoliciesUseCase;
import momzzangseven.mztkbe.modules.level.application.port.in.GetMyLevelUpHistoriesUseCase;
import momzzangseven.mztkbe.modules.level.application.port.in.GetMyLevelUseCase;
import momzzangseven.mztkbe.modules.level.application.port.in.GetMyXpLedgerUseCase;
import momzzangseven.mztkbe.modules.level.application.port.in.LevelUpUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LevelController {

  private final GetMyLevelUseCase getMyLevelUseCase;
  private final GetLevelPoliciesUseCase getLevelPoliciesUseCase;
  private final LevelUpUseCase levelUpUseCase;
  private final GetMyLevelUpHistoriesUseCase getMyLevelUpHistoriesUseCase;
  private final GetMyXpLedgerUseCase getMyXpLedgerUseCase;

  @GetMapping("/users/me/level")
  public ResponseEntity<ApiResponse<GetMyLevelResponseDTO>> getMyLevel(
      @AuthenticationPrincipal Long userId) {
    userId = requireUserId(userId);
    GetMyLevelResult result = getMyLevelUseCase.execute(userId);
    return ResponseEntity.ok(ApiResponse.success(GetMyLevelResponseDTO.from(result)));
  }

  @GetMapping("/levels/policies")
  public ResponseEntity<ApiResponse<GetLevelPoliciesResponseDTO>> getLevelPolicies() {
    GetLevelPoliciesResult result = getLevelPoliciesUseCase.execute();
    return ResponseEntity.ok(ApiResponse.success(GetLevelPoliciesResponseDTO.from(result)));
  }

  @PostMapping("/users/me/level-ups")
  public ResponseEntity<ApiResponse<LevelUpResponseDTO>> levelUp(
      @AuthenticationPrincipal Long userId) {
    userId = requireUserId(userId);
    LevelUpResult result = levelUpUseCase.execute(LevelUpCommand.of(userId));
    return ResponseEntity.ok(ApiResponse.success(LevelUpResponseDTO.from(result)));
  }

  @GetMapping("/users/me/level-up-histories")
  public ResponseEntity<ApiResponse<GetMyLevelUpHistoriesResponseDTO>> getMyLevelUpHistories(
      @AuthenticationPrincipal Long userId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    userId = requireUserId(userId);
    GetMyLevelUpHistoriesResult result = getMyLevelUpHistoriesUseCase.execute(userId, page, size);
    return ResponseEntity.ok(ApiResponse.success(GetMyLevelUpHistoriesResponseDTO.from(result)));
  }

  @GetMapping("/users/me/xp-ledger")
  public ResponseEntity<ApiResponse<GetMyXpLedgerResponseDTO>> getMyXpLedger(
      @AuthenticationPrincipal Long userId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    userId = requireUserId(userId);
    GetMyXpLedgerResult result = getMyXpLedgerUseCase.execute(userId, page, size);
    return ResponseEntity.ok(ApiResponse.success(GetMyXpLedgerResponseDTO.from(result)));
  }

  private Long requireUserId(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
    return userId;
  }
}
