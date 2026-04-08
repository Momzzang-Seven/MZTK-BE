package momzzangseven.mztkbe.modules.account.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import momzzangseven.mztkbe.modules.account.application.dto.LoginCommand;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;

/** Login request DTO from client. Contains validation annotations for HTTP request. */
@Getter
@NoArgsConstructor
public class LoginRequestDTO {

  @NotNull(message = "Provider is required")
  private AuthProvider provider;

  @Email(message = "Invalid email format")
  private String email;

  private String password;

  private String authorizationCode;

  private String redirectUri;

  /**
   * Optional role selection. Uppercase only: "USER" or "TRAINER". Applied only to new social
   * signups; ignored for existing users. Defaults to "USER" if omitted.
   */
  private String role;

  /** Convert this request to an application-layer command. */
  public LoginCommand toCommand() {
    return new LoginCommand(provider, email, password, authorizationCode, redirectUri, role);
  }
}
