package momzzangseven.mztkbe.global.error;

public class NotEnoughXpException extends BusinessException {
  public NotEnoughXpException() {
    super(ErrorCode.NOT_ENOUGH_XP);
  }

  public NotEnoughXpException(String customMessage) {
    super(ErrorCode.NOT_ENOUGH_XP, customMessage);
  }
}
