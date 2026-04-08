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
import momzzangseven.mztkbe.modules.account.application.dto.LoginCommand;
import momzzangseven.mztkbe.modules.account.application.dto.LoginResult;
import momzzangseven.mztkbe.modules.account.application.dto.LogoutCommand;
import momzzangseven.mztkbe.modules.account.application.dto.ReactivateCommand;
import momzzangseven.mztkbe.modules.account.application.dto.ReissueTokenCommand;
import momzzangseven.mztkbe.modules.account.application.dto.ReissueTokenResult;
import momzzangseven.mztkbe.modules.account.application.dto.SignupCommand;
import momzzangseven.mztkbe.modules.account.application.dto.SignupResult;
import momzzangseven.mztkbe.modules.account.application.dto.StepUpCommand;
import momzzangseven.mztkbe.modules.account.application.dto.StepUpResult;
import momzzangseven.mztkbe.modules.account.application.dto.WithdrawCommand;
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
    LoginCommand command = request.toCommand();
    LoginResult result = loginUseCase.execute(command);
    LoginResponseDTO response = LoginResponseDTO.from(result);

    HttpHeaders headers =
        refreshTokenHeaders(result.refreshToken(), result.refreshTokenExpiresIn());
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

    HttpHeaders headers =
        refreshTokenHeaders(result.refreshToken(), result.refreshTokenExpiresIn());
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .headers(headers)
        .body(ApiResponse.success(response));
  }

  /** Register a new LOCAL user and return basic profile data. */
  @PostMapping("/signup")
  public ResponseEntity<ApiResponse<SignupResponseDTO>> signup(
      @Valid @RequestBody SignupRequestDTO request) {
    SignupCommand command = request.toCommand();
    SignupResult result = signupUseCase.execute(command);
    SignupResponseDTO response = SignupResponseDTO.from(result);

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(ApiResponse.success("Sign Up Success", response));
  }

  /** Reissue access/refresh tokens based on the stored refresh token cookie. */
  @PostMapping("/reissue")
  public ResponseEntity<ApiResponse<ReissueTokenResponseDTO>> reissue(
      @CookieValue("refreshToken") String refreshToken) {
    ReissueTokenCommand command = ReissueTokenCommand.of(refreshToken);
    ReissueTokenResult result = reissueTokenUseCase.execute(command);
    ReissueTokenResponseDTO response = ReissueTokenResponseDTO.from(result);

    HttpHeaders headers =
        refreshTokenHeaders(result.refreshToken(), result.refreshTokenExpiresIn());
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

    HttpHeaders headers = refreshTokenHeaders("", 0);
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

    StepUpCommand command = request.toCommand(userId);
    StepUpResult result = stepUpUseCase.execute(command);
    StepUpResponseDTO response = StepUpResponseDTO.from(result);

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(ApiResponse.success("Step-up authentication successful", response));
  }

  private HttpHeaders refreshTokenHeaders(String tokenValue, long expiresInMillis) {
    ResponseCookie cookie =
        ResponseCookie.from("refreshToken", tokenValue)
            .httpOnly(true)
            .secure(false)
            .path("/auth")
            .maxAge(Duration.ofMillis(expiresInMillis))
            .sameSite("Strict")
            .build();

    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.SET_COOKIE, cookie.toString());
    return headers;
  }
}
