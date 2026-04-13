package momzzangseven.mztkbe.modules.admin.infrastructure.credential;

import java.security.SecureRandom;
import momzzangseven.mztkbe.modules.admin.application.port.out.GenerateCredentialPort;
import momzzangseven.mztkbe.modules.admin.domain.vo.GeneratedAdminCredentials;
import org.springframework.stereotype.Component;

/** Generates cryptographically secure admin credentials using {@link SecureRandom}. */
@Component
public class SecureCredentialGenerator implements GenerateCredentialPort {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int LOGIN_ID_LENGTH = 8;
  private static final int PASSWORD_LENGTH = 20;
  private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
  private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
  private static final String DIGITS = "0123456789";
  private static final String SPECIAL = "!@#$%^&*()-_=+";
  private static final String ALL_CHARS = UPPER + LOWER + DIGITS + SPECIAL;

  @Override
  public GeneratedAdminCredentials generate() {
    String loginId = generateLoginId();
    String password = generatePassword();
    return new GeneratedAdminCredentials(loginId, password);
  }

  @Override
  public String generatePasswordOnly() {
    return generatePassword();
  }

  private String generateLoginId() {
    StringBuilder sb = new StringBuilder(LOGIN_ID_LENGTH);
    for (int i = 0; i < LOGIN_ID_LENGTH; i++) {
      sb.append(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
    }
    return sb.toString();
  }

  private String generatePassword() {
    StringBuilder sb = new StringBuilder(PASSWORD_LENGTH);
    // Guarantee at least one character from each category
    sb.append(UPPER.charAt(RANDOM.nextInt(UPPER.length())));
    sb.append(LOWER.charAt(RANDOM.nextInt(LOWER.length())));
    sb.append(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
    sb.append(SPECIAL.charAt(RANDOM.nextInt(SPECIAL.length())));
    // Fill remaining with random characters from all categories
    for (int i = 4; i < PASSWORD_LENGTH; i++) {
      sb.append(ALL_CHARS.charAt(RANDOM.nextInt(ALL_CHARS.length())));
    }
    // Shuffle to avoid predictable positions
    char[] chars = sb.toString().toCharArray();
    for (int i = chars.length - 1; i > 0; i--) {
      int j = RANDOM.nextInt(i + 1);
      char temp = chars[i];
      chars[i] = chars[j];
      chars[j] = temp;
    }
    return new String(chars);
  }
}
