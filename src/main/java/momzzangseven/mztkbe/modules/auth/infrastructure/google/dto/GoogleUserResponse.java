package momzzangseven.mztkbe.modules.auth.infrastructure.google.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class GoogleUserResponse {

  private String sub;

  private String email;

  private String name;

  private String picture;

  @JsonProperty("email_verified")
  private Boolean emailVerified;

  private String locale;
}
