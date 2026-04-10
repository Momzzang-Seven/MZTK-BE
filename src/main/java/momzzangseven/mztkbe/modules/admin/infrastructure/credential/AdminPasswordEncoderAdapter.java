package momzzangseven.mztkbe.modules.admin.infrastructure.credential;

import momzzangseven.mztkbe.modules.admin.application.port.out.AdminPasswordEncoderPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/** BCrypt-based password encoder for admin accounts (strength=12). */
@Component
public class AdminPasswordEncoderAdapter implements AdminPasswordEncoderPort {

  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

  @Override
  public String encode(String rawPassword) {
    return encoder.encode(rawPassword);
  }

  @Override
  public boolean matches(String rawPassword, String encodedPassword) {
    return encoder.matches(rawPassword, encodedPassword);
  }
}
