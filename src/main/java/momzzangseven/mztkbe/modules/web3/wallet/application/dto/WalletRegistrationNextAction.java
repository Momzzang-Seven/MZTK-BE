package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

/** Frontend-facing next action hint for a wallet registration session. */
public enum WalletRegistrationNextAction {
  SIGN_APPROVAL,
  WAIT_FOR_APPROVAL_TRANSACTION,
  RETRY_APPROVAL,
  DONE,
  CONTACT_SUPPORT,
  NONE
}
