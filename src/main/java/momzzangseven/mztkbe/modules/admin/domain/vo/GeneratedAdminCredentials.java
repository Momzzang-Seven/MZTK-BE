package momzzangseven.mztkbe.modules.admin.domain.vo;

/**
 * Value object carrying a generated admin login ID and its plaintext password. The plaintext is
 * never persisted — it is delivered once and then discarded.
 *
 * @param loginId the generated numeric login identifier
 * @param plaintext the generated plaintext password
 */
public record GeneratedAdminCredentials(String loginId, String plaintext) {

  /** Mask the plaintext password in string representations. */
  @Override
  public String toString() {
    return "GeneratedAdminCredentials[loginId=" + loginId + ", plaintext=***]";
  }
}
