package momzzangseven.mztkbe.modules.auth.api.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;

@Getter
@NoArgsConstructor
public class LoginRequest {
  private AuthProvider provider;

  // LOCAL
  private String email;
  private String password;

  // SOCIAL
  private String code;
}
