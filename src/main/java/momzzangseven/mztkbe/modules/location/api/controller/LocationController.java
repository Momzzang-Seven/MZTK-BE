package momzzangseven.mztkbe.modules.location.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.location.api.dto.*;
import momzzangseven.mztkbe.modules.location.application.dto.*;
import momzzangseven.mztkbe.modules.location.application.port.in.DeleteLocationUseCase;
import momzzangseven.mztkbe.modules.location.application.port.in.RegisterLocationUseCase;
import momzzangseven.mztkbe.modules.location.application.port.in.VerifyLocationUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class LocationController {
  private final RegisterLocationUseCase registerLocationUseCase;
  private final VerifyLocationUseCase verifyLocationUseCase;
  private final DeleteLocationUseCase deleteLocationUseCase;

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

  /**
   * Location Deletion API
   *
   * @param userId
   * @param locationId
   * @return
   */
  @DeleteMapping("/users/me/locations/{locationId}")
  public ResponseEntity<ApiResponse<DeleteLocationResponseDTO>> deleteLocation(
      @AuthenticationPrincipal Long userId, @PathVariable Long locationId) {

    // userId null validation
    userId = requireUserId(userId);

    // Create Command
    DeleteLocationCommand command = DeleteLocationCommand.of(userId, locationId);

    // Execute Use Case
    DeleteLocationResult result = deleteLocationUseCase.execute(command);

    // Convert Response
    DeleteLocationResponseDTO response = DeleteLocationResponseDTO.from(result);

    return ResponseEntity.ok(ApiResponse.success(response));
  }

  private Long requireUserId(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
    return userId;
  }
}
