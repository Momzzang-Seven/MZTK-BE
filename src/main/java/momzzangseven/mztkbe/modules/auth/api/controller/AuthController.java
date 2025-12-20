package momzzangseven.mztkbe.modules.auth.api.controller;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.auth.api.dto.LoginRequest;
import momzzangseven.mztkbe.modules.auth.api.dto.LoginResponse;
import momzzangseven.mztkbe.modules.auth.application.dto.AuthenticationContext;
import momzzangseven.mztkbe.modules.auth.application.dto.LoginResult;
import momzzangseven.mztkbe.modules.auth.application.port.in.LoginUseCase;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

  private final LoginUseCase loginUseCase;

  @PostMapping("/login")
  public LoginResponse login(@RequestBody LoginRequest request) {

    // 1️⃣ API DTO → Application DTO
    AuthenticationContext context = AuthenticationContext.from(request);

    // 2️⃣ UseCase 실행
    LoginResult result = loginUseCase.login(context);

    // 3️⃣ Application DTO → API DTO
    return LoginResponse.from(result);
  }
}
