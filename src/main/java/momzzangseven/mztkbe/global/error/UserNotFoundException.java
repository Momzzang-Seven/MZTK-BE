package momzzangseven.mztkbe.global.error;

/** Exception thrown when a user is not found. */
public class UserNotFoundException extends BusinessException {

  public UserNotFoundException(Long userId) {
    super(ErrorCode.USER_NOT_FOUND, "User not found with ID: " + userId);
  }

  public UserNotFoundException(String email) {
    super(ErrorCode.USER_NOT_FOUND, "User not found with email: " + email);
  }
}
