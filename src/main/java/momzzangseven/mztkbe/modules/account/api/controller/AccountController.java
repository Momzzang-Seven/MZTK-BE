package momzzangseven.mztkbe.modules.account.api.controller;

import jakarta.validation.Valid;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.account.api.dto.LoginRequestDTO;
import momzzangseven.mztkbe.modules.account.api.dto.LoginResponseDTO;
import momzzangseven.mztkbe.modules.account.api.dto.ReactivateRequestDTO;
import momzzangseven.mztkbe.modules.account.api.dto.SignupRequestDTO;
import momzzangseven.mztkbe.modules.account.api.dto.SignupResponseDTO;
import momzzangseven.mztkbe.modules.account.api.dto.StepUpRequestDTO;
import momzzangseven.mztkbe.modules.account.api.dto.StepUpResponseDTO;
import momzzangseven.mztkbe.modules.account.api.dto.token.ReissueTokenResponseDTO;
import momzzangseven.mztkbe.modules.account.application.dto.*;
import momzzangseven.mztkbe.modules.account.application.port.in.LoginUseCase;
import momzzangseven.mztkbe.modules.account.application.port.in.LogoutUseCase;
import momzzangseven.mztkbe.modules.account.application.port.in.ReactivateUseCase;
import momzzangseven.mztkbe.modules.account.application.port.in.ReissueTokenUseCase;
import momzzangseven.mztkbe.modules.account.application.port.in.SignupUseCase;
import momzzangseven.mztkbe.modules.account.application.port.in.StepUpUseCase;
import momzzangseven.mztkbe.modules.account.application.port.in.WithdrawUseCase;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AccountController {

  /*
   * Auth endpoints overview:
   * - /auth/login: Authenticate and issue tokens
   *   (DELETED accounts respond with USER_WITHDRAWN(409))
   * - /auth/reactivate: Reactivate a soft-deleted account and issue tokens (public endpoint)
   * - /auth/signup: Register a new LOCAL user
   * - /auth/reissue: Rotate tokens using refresh token cookie
   * - /auth/stepup: Step-up authentication for sensitive actions (requires existing access token)
   *
   * Token delivery:
   * - Access token: returned in JSON body (LoginResponseDTO)
   * - Refresh token: returned as HttpOnly cookie (refreshToken)
   */
  private final LoginUseCase loginUseCase;
  private final ReactivateUseCase reactivateUseCase;
  private final SignupUseCase signupUseCase;
  private final ReissueTokenUseCase reissueTokenUseCase;
  private final LogoutUseCase logoutUseCase;
  private final StepUpUseCase stepUpUseCase;
  private final WithdrawUseCase withdrawUseCase;

  /** Authenticate credentials and issue tokens for LOCAL/social login. */
  @PostMapping("/login")
  public ResponseEntity<ApiResponse<LoginResponseDTO>> login(
      @Valid @RequestBody LoginRequestDTO request) {
    // 1. Convert API DTO -> Application DTO
    LoginCommand command = request.toCommand();

    // 2. Execute Login UseCase
    LoginResult result = loginUseCase.execute(command);

    // 3. Convert Application DTO -> API DTO
    LoginResponseDTO response = LoginResponseDTO.from(result);

    // 4. Set Response Cookie
    ResponseCookie refreshTokenCookie =
        ResponseCookie.from("refreshToken", result.refreshToken())
            .httpOnly(true)
            .secure(false)
            .path("/auth")
            .maxAge(Duration.ofMillis(result.refreshTokenExpiresIn()))
            .sameSite("Strict")
            .build();

    // 5. Set headers
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());

    // 6. Return Response
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .headers(headers)
        .body(ApiResponse.success(response));
  }

  /**
   * Reactivate a soft-deleted user and issue tokens for LOCAL/social.
   *
   * <p>Important: - This endpoint is intentionally public (permitAll) and is excluded from
   * JwtAuthenticationFilter. - Clients may auto-attach an old Authorization header; if
   * JwtAuthenticationFilter ran here, DELETED users could be blocked before reactivation.
   */
  @PostMapping("/reactivate")
  public ResponseEntity<ApiResponse<LoginResponseDTO>> reactivate(
      @Valid @RequestBody ReactivateRequestDTO request) {
    ReactivateCommand command = request.toCommand();
    LoginResult result = reactivateUseCase.execute(command);
    LoginResponseDTO response = LoginResponseDTO.from(result);

    // Refresh token cookie is issued on successful reactivation, same as login.
    ResponseCookie refreshTokenCookie =
        ResponseCookie.from("refreshToken", result.refreshToken())
            .httpOnly(true)
            .secure(false)
            .path("/auth")
            .maxAge(Duration.ofMillis(result.refreshTokenExpiresIn()))
            .sameSite("Strict")
            .build();

    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .headers(headers)
        .body(ApiResponse.success(response));
  }

  /** Register a new LOCAL user and return basic profile data. */
  @PostMapping("/signup")
  public ResponseEntity<ApiResponse<SignupResponseDTO>> signup(
      @Valid @RequestBody SignupRequestDTO request) {
    // 1. Convert API DTO -> Application DTO
    SignupCommand command = request.toCommand();

    // 2. Execute Signup UseCase
    SignupResult result = signupUseCase.execute(command);

    // 3. Convert Application DTO -> API DTO
    SignupResponseDTO response = SignupResponseDTO.from(result);

    // 4. Return Response
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(ApiResponse.success("Sign Up Success", response));
  }

  /** Reissue access/refresh tokens based on the stored refresh token cookie. */
  @PostMapping("/reissue")
  public ResponseEntity<ApiResponse<ReissueTokenResponseDTO>> reissue(
      @CookieValue(value = "refreshToken", required = true) String refreshToken) {

    // 1. Convert to Application Command
    ReissueTokenCommand command = ReissueTokenCommand.of(refreshToken);

    // 2. Execute Token Reissue UseCase
    ReissueTokenResult result = reissueTokenUseCase.execute(command);

    // 3. Convert Application Result -> API DTO (Access Token only)
    ReissueTokenResponseDTO response = ReissueTokenResponseDTO.from(result);

    // 4. Set Response Cookie (New Refresh Token)
    ResponseCookie newRefreshTokenCookie =
        ResponseCookie.from("refreshToken", result.refreshToken())
            .httpOnly(true)
            .secure(false)
            .path("/auth")
            .maxAge(Duration.ofMillis(result.refreshTokenExpiresIn()))
            .sameSite("Strict")
            .build();

    // 5. Set headers
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.SET_COOKIE, newRefreshTokenCookie.toString());

    // 6. Return Response
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .headers(headers)
        .body(ApiResponse.success("Token successfully reissued", response));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @CookieValue(value = "refreshToken", required = false) String refreshToken) {

    if (refreshToken != null && !refreshToken.isBlank()) {
      logoutUseCase.execute(LogoutCommand.of(refreshToken));
    }

    ResponseCookie deleteRefreshTokenCookie =
        ResponseCookie.from("refreshToken", "")
            .httpOnly(true)
            .secure(false)
            .path("/auth")
            .maxAge(Duration.ZERO)
            .sameSite("Strict")
            .build();

    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.SET_COOKIE, deleteRefreshTokenCookie.toString());

    return ResponseEntity.noContent().headers(headers).build();
  }

  /** Withdraw (soft-delete) the current user's account after step-up verification. */
  @PostMapping("/withdrawal")
  public ResponseEntity<ApiResponse<Void>> withdraw(@AuthenticationPrincipal Long userId) {

    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }

    WithdrawCommand command = WithdrawCommand.of(userId);
    withdrawUseCase.execute(command);
    return ResponseEntity.ok(ApiResponse.success("Account withdrawn successfully", null));
  }

  /** Step-up authentication for sensitive operations (requires existing access token). */
  @PostMapping("/stepup")
  public ResponseEntity<ApiResponse<StepUpResponseDTO>> stepUp(
      @Valid @RequestBody StepUpRequestDTO request, @AuthenticationPrincipal Long userId) {

    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }

    // Convert API DTO -> Application DTO
    StepUpCommand command = request.toCommand(userId);

    // Execute Step-up UseCase
    StepUpResult result = stepUpUseCase.execute(command);

    // Convert Application Result -> API DTO
    StepUpResponseDTO response = StepUpResponseDTO.from(result);

    // Return Response
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(ApiResponse.success("Step-up authentication successful", response));
  }
}
