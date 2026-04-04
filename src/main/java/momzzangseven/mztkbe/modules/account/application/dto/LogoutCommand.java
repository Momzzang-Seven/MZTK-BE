package momzzangseven.mztkbe.modules.account.application.dto;

/** Command for user logout operation. */
public record LogoutCommand(String refreshToken) {

  public static LogoutCommand of(String refreshToken) {
    return new LogoutCommand(refreshToken);
  }
}
