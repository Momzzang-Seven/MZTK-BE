package momzzangseven.mztkbe.global.error;

/** Shared validation messages for level module commands/results. */
public final class LevelValidationMessage {

  public static final String COMMAND_REQUIRED = "command is required";
  public static final String USER_ID_REQUIRED = "userId is required";
  public static final String USER_ID_POSITIVE = "userId must be positive";
  public static final String LEVEL_POLICY_ID_POSITIVE = "levelPolicyId must be positive";
  public static final String LEVEL_RANGE_INVALID = "fromLevel/toLevel are invalid";
  public static final String SPENT_XP_NON_NEGATIVE = "spentXp must be >= 0";
  public static final String REWARD_MZTK_NON_NEGATIVE = "rewardMztk must be >= 0";
  public static final String CREATED_AT_REQUIRED = "createdAt is required";
  public static final String REFERENCE_ID_POSITIVE = "referenceId must be positive";
  public static final String TO_WALLET_REQUIRED = "toWalletAddress is required";
  public static final String USER_IDS_NOT_EMPTY = "userIds must not be empty";
  public static final String XP_TYPE_REQUIRED = "xpType is required";
  public static final String OCCURRED_AT_REQUIRED = "occurredAt is required";
  public static final String IDEMPOTENCY_KEY_REQUIRED = "idempotencyKey is required";
  public static final String REWARD_STATUS_REQUIRED = "Reward status must not be null";

  private LevelValidationMessage() {}
}
