package momzzangseven.mztkbe.global.error;

/**
 * Exception thrown when login credentials are invalid.
 */
public class InvalidCredentialsException extends BusinessException {

    public InvalidCredentialsException() {
        super(ErrorCode.INVALID_CREDENTIALS);  // Uses default message
    }

    public InvalidCredentialsException(String customMessage) {
        super(ErrorCode.INVALID_CREDENTIALS, customMessage);
    }
}