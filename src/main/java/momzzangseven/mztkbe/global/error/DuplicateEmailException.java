package momzzangseven.mztkbe.global.error;

/** Exception thrown when email already exists during signup. */
public class DuplicateEmailException extends BusinessException {

  public DuplicateEmailException(String email) {
    super(ErrorCode.DUPLICATE_EMAIL, ErrorCode.DUPLICATE_EMAIL.getMessage() + ": " + email);
  }
}
