package momzzangseven.mztkbe.modules.account.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import momzzangseven.mztkbe.modules.account.application.dto.ReactivateCommand;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;

/** Reactivation request for soft-deleted accounts. */
@Getter
@NoArgsConstructor
public class ReactivateRequestDTO {

  @NotNull(message = "Provider is required")
  private AuthProvider provider;

  @Email(message = "Invalid email format")
  private String email;

  private String password;

  private String authorizationCode;

  private String redirectUri;

  /** Convert this request to an application-layer command. */
  public ReactivateCommand toCommand() {
    return new ReactivateCommand(provider, email, password, authorizationCode, redirectUri);
  }
}
