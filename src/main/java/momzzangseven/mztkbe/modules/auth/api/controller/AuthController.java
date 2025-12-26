package momzzangseven.mztkbe.modules.auth.api.controller;

import jakarta.validation.Valid;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.token.RefreshTokenNotFoundException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.auth.api.dto.LoginRequestDTO;
import momzzangseven.mztkbe.modules.auth.api.dto.LoginResponseDTO;
import momzzangseven.mztkbe.modules.auth.api.dto.SignupRequestDTO;
import momzzangseven.mztkbe.modules.auth.api.dto.SignupResponseDTO;
import momzzangseven.mztkbe.modules.auth.api.dto.token.ReissueTokenResponseDTO;
import momzzangseven.mztkbe.modules.auth.application.dto.*;
import momzzangseven.mztkbe.modules.auth.application.port.in.LoginUseCase;
import momzzangseven.mztkbe.modules.auth.application.port.in.ReissueTokenUseCase;
import momzzangseven.mztkbe.modules.auth.application.port.in.SignupUseCase;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

  private final LoginUseCase loginUseCase;
  private final SignupUseCase signupUseCase;
  private final ReissueTokenUseCase reissueTokenUseCase;

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<LoginResponseDTO>> login(
      @Valid @RequestBody LoginRequestDTO request) {
    // 1. Convert API DTO -> Application DTO
    LoginCommand command = LoginCommand.from(request);

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
            .maxAge(Duration.ofDays(7))
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

  @PostMapping("/signup")
  public ResponseEntity<ApiResponse<SignupResponseDTO>> signup(
      @Valid @RequestBody SignupRequestDTO request) {
    // 1. Convert API DTO -> Application DTO
    SignupCommand command = SignupCommand.from(request);

    // 2. Execute Signup UseCase
    SignupResult result = signupUseCase.execute(command);

    // 3. Convert Application DTO -> API DTO
    SignupResponseDTO response = SignupResponseDTO.from(result);

    // 4. Return Response
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(ApiResponse.success("Sign Up Success", response));
  }

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
                    .maxAge(Duration.ofDays(7))
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
}
