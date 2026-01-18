package momzzangseven.mztkbe.modules.level.api.controller;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.level.api.dto.GrantXpRequestDTO;
import momzzangseven.mztkbe.modules.level.api.dto.GrantXpResponseDTO;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpCommand;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpResult;
import momzzangseven.mztkbe.modules.level.application.port.in.GrantXpUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LevelActionController {

  private final GrantXpUseCase grantXpUseCase;

  @PostMapping("/users/me/xp-grants")
  public ResponseEntity<ApiResponse<GrantXpResponseDTO>> grantXp(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody GrantXpRequestDTO request) {
    userId = requireUserId(userId);
    LocalDateTime occurredAt = request.resolvedOccurredAt();
    GrantXpResult result =
        grantXpUseCase.execute(
            GrantXpCommand.of(
                userId, request.type(), occurredAt, request.idempotencyKey(), request.sourceRef()));
    return ResponseEntity.ok(ApiResponse.success(GrantXpResponseDTO.from(request.type(), result)));
  }

  private Long requireUserId(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
    return userId;
  }
}
