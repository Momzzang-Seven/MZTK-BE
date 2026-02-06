package momzzangseven.mztkbe.modules.location.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.location.api.dto.RegisterLocationRequestDTO;
import momzzangseven.mztkbe.modules.location.api.dto.RegisterLocationResponseDTO;
import momzzangseven.mztkbe.modules.location.application.dto.RegisterLocationCommand;
import momzzangseven.mztkbe.modules.location.application.dto.RegisterLocationResult;
import momzzangseven.mztkbe.modules.location.application.port.in.RegisterLocationUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LocationController {
  private final RegisterLocationUseCase registerLocationUseCase;

  /** 위치 등록 POST /users/me/locations/register */
  @PostMapping("/users/me/locations/register")
  public ResponseEntity<ApiResponse<RegisterLocationResponseDTO>> registerLocation(
      @Valid @RequestBody RegisterLocationRequestDTO request,
      @AuthenticationPrincipal Long userId) {
    userId = requireUserId(userId);

    // Create Command
    RegisterLocationCommand command = RegisterLocationCommand.from(userId, request);

    // Call Service
    RegisterLocationResult result = registerLocationUseCase.execute(command);

    // Response
    RegisterLocationResponseDTO response = RegisterLocationResponseDTO.from(result);

    return ResponseEntity.ok(ApiResponse.success(response));
  }

  private Long requireUserId(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
    return userId;
  }
}
