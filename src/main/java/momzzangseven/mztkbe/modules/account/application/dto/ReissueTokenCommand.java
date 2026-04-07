package momzzangseven.mztkbe.modules.account.application.dto;

import momzzangseven.mztkbe.global.error.token.RefreshTokenNotFoundException;

/**
 * Command for token reissue operation.
 *
 * <p>Data Transfer Object: - Transfers data from API layer to Application layer - Immutable
 * (record) - Contains validation logic
 *
 * <p>Pattern: Command Pattern - Encapsulates request as an object - Allows parameterization of
 * operations
 */
public record ReissueTokenCommand(String refreshToken) {
  /**
   * Create command with validation.
   *
   * @param refreshToken Refresh token string
   * @return Validated ReissueTokenCommand
   * @throws RefreshTokenNotFoundException if validation fails
   */
  public static ReissueTokenCommand of(String refreshToken) {
    ReissueTokenCommand command = new ReissueTokenCommand(refreshToken);
    command.validate();
    return command;
  }

  /**
   * Validate command parameters.
   *
   * <p>Business Rules: - Refresh token must not be null or empty
   *
   * @throws RefreshTokenNotFoundException if validation fails
   */
  public void validate() {
    if (refreshToken == null || refreshToken.isBlank()) {
      throw new RefreshTokenNotFoundException("Refresh token is required");
    }

    // Basic format check (optional)
    if (refreshToken.length() < 10 || refreshToken.length() > 500) {
      throw new RefreshTokenNotFoundException("Invalid refresh token format");
    }
  }
}
