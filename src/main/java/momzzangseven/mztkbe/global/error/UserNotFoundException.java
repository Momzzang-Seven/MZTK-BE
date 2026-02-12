package momzzangseven.mztkbe.global.error;

import momzzangseven.mztkbe.global.error.auth.AuthErrorCode;

/** Exception thrown when a user is not found. */
public class UserNotFoundException extends BusinessException {

  public UserNotFoundException(Long userId) {
    super(AuthErrorCode.USER_NOT_FOUND, "User not found with ID: " + userId);
  }

  public UserNotFoundException(String email) {
    super(AuthErrorCode.USER_NOT_FOUND, "User not found with email: " + email);
  }
}
