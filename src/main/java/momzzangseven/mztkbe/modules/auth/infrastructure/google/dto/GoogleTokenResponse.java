package momzzangseven.mztkbe.modules.auth.infrastructure.google.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class GoogleTokenResponse {

  @JsonProperty("token_type")
  private String tokenType;

  @JsonProperty("access_token")
  private String accessToken;

  @JsonProperty("expires_in")
  private Long expiresIn;

  @JsonProperty("refresh_token")
  private String refreshToken;

  @JsonProperty("scope")
  private String scope;

  @JsonProperty("id_token")
  private String idToken;
}
