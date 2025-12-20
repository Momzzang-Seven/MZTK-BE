package momzzangseven.mztkbe.modules.auth.application.dto;

import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;

@Getter
@Builder
public class AuthenticatedUser {
  private AuthProvider provider;
  private String providerUserId;

  private String email;
  private String nickname;
  private String profileImageUrl;
}
