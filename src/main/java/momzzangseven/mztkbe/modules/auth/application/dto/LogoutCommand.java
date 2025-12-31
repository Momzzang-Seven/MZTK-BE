package momzzangseven.mztkbe.modules.auth.application.dto;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LogoutCommand {

  private String refreshToken;

  private LogoutCommand(String refreshToken) {
    this.refreshToken = refreshToken;
  }

  public static LogoutCommand of(String refreshToken) {
    return new LogoutCommand(refreshToken);
  }
}
