package momzzangseven.mztkbe.global.error.web3;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Shared validation messages for web3 input/domain checks. */
public final class Web3ValidationMessage {

  public static final String COMMAND_REQUIRED = "command is required";
  public static final String USER_ID_POSITIVE = "userId must be positive";
  public static final String LEVEL_UP_HISTORY_ID_POSITIVE = "levelUpHistoryId must be positive";
  public static final String IDEMPOTENCY_KEY_REQUIRED = "idempotencyKey is required";
  public static final String FROM_ADDRESS_REQUIRED = "fromAddress is required";
  public static final String TO_ADDRESS_REQUIRED = "toAddress is required";
  public static final String AMOUNT_WEI_NON_NEGATIVE = "amountWei must be >= 0";
  public static final String STATUS_REQUIRED = "status is required";
  public static final String NOW_REQUIRED = "now is required";
  public static final String NEXT_STATUS_REQUIRED = "nextStatus is required";
  public static final String TX_HASH_REQUIRED = "txHash is required";

  private Web3ValidationMessage() {}
}
