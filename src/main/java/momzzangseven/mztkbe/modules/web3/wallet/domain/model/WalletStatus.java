package momzzangseven.mztkbe.modules.web3.wallet.domain.model;

/** Wallet status enum
 *<p>Status Flow:
 *  <ul>
 *    <li>ACTIVE → UNLINKED (User unlink his wallet by himself)</li>
 *    <li>ACTIVE → USER_DELETED (User withdraw)</li>
 *    <li>ACTIVE → BLOCKED (Blocked by admin)</li>
 *    <li>UNLINKED → HARD_DELETED (Hard deleted by wallet hard delete scheduler)</li>
 *    <li>USER_DELETED → HARD_DELETED (Hard deleted by user hard delete scheduler)</li>
 *  </ul>
 */
public enum WalletStatus {
  /** Active and usable */
  ACTIVE,

  /** Unlinked by user himself */
  UNLINKED,

  /** User deleted */
  USER_DELETED,

  /** Blacklisted by admin */
  BLOCKED,

  /** Hard deleted by scheduler */
  HARD_DELETED
}
