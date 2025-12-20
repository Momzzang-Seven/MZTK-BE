package momzzangseven.mztkbe.modules.auth.api.controller;


import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.auth.api.dto.LoginRequestDTO;
import momzzangseven.mztkbe.modules.auth.api.dto.LoginResponseDTO;
import momzzangseven.mztkbe.modules.auth.application.dto.LoginCommand;
import momzzangseven.mztkbe.modules.auth.application.dto.LoginResult;
import momzzangseven.mztkbe.modules.auth.application.port.in.LoginUseCase;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginUseCase loginUseCase;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponseDTO>> login(@RequestBody LoginRequestDTO request){
        // 1. Convert API DTO -> Application DTO
        LoginCommand command = LoginCommand.from(request);

        // 2. Execute Login UseCase
        LoginResult result = loginUseCase.execute(command);

        // 3. Convert Application DTO -> API DTO
        LoginResponseDTO response = LoginResponseDTO.from(result);

        // Set Response Cookie
        ResponseCookie refreshTokenCookie = ResponseCookie.from("refreshToken", result.refreshToken())
                .httpOnly(true)
                .secure(true)
                .path("/api/v1/auth")
                .maxAge(Duration.ofDays(7))
                .sameSite("Strict")
                .build();

        // Set headers
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());

        return ResponseEntity
                .ok()
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<LoginResponseDTO>> signup(@RequestBody LoginRequestDTO request){
        // 1. Convert API DTO -> Application DTO
        SignupCommand command = SignupCommand.from(request);

        // 2. Execute Signup UseCase
        SignupResult result = signupUseCase.execute(command);

        // 3. Convert Application DTO -> API DTO
        SignupResponse response = SignupResponse.from(result);
    }
}
