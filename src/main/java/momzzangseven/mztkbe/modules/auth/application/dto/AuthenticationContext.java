package momzzangseven.mztkbe.modules.auth.application.dto;

import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.modules.auth.api.dto.LoginRequest;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;

@Getter
@Builder
public class AuthenticationContext {

  private AuthProvider provider;

  // LOCAL
  private String email;
  private String password;

  // SOCIAL
  private String authorizationCode;
  private String redirectUri;

  public static AuthenticationContext from(LoginRequest request) {
    return AuthenticationContext.builder()
        .provider(request.getProvider())
        .email(request.getEmail())
        .password(request.getPassword())
        .authorizationCode(request.getCode())
        .build();
  }
}
