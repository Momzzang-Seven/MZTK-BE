package momzzangseven.mztkbe.modules.location.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.location.api.dto.RegisterLocationRequestDTO;
import momzzangseven.mztkbe.modules.location.api.dto.RegisterLocationResponseDTO;
import momzzangseven.mztkbe.modules.location.api.dto.VerifyLocationRequestDTO;
import momzzangseven.mztkbe.modules.location.api.dto.VerifyLocationResponseDTO;
import momzzangseven.mztkbe.modules.location.application.dto.RegisterLocationCommand;
import momzzangseven.mztkbe.modules.location.application.dto.RegisterLocationResult;
import momzzangseven.mztkbe.modules.location.application.dto.VerifyLocationCommand;
import momzzangseven.mztkbe.modules.location.application.dto.VerifyLocationResult;
import momzzangseven.mztkbe.modules.location.application.port.in.RegisterLocationUseCase;
import momzzangseven.mztkbe.modules.location.application.port.in.VerifyLocationUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LocationController {
  private final RegisterLocationUseCase registerLocationUseCase;
  private final VerifyLocationUseCase verifyLocationUseCase;

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

  /**
   * 위치 인증 API
   *
   * <p>POST /api/v1/locations/verify
   *
   * @param userId Verified user ID (@AuthenticationPrincipal)
   * @param request Location verification request DTO
   * @return Location verification result
   */
  @PostMapping("/locations/verify")
  public ResponseEntity<ApiResponse<VerifyLocationResponseDTO>> verifyLocation(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody VerifyLocationRequestDTO request) {
    // userId null validation
    userId = requireUserId(userId);

    // Create Command
    VerifyLocationCommand command =
        VerifyLocationCommand.of(
            userId, request.locationId(), request.currentLatitude(), request.currentLongitude());

    // Execute Use Case
    VerifyLocationResult result = verifyLocationUseCase.execute(command);

    // Convert Response and return
    VerifyLocationResponseDTO response = VerifyLocationResponseDTO.from(result);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  private Long requireUserId(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
    return userId;
  }
}
