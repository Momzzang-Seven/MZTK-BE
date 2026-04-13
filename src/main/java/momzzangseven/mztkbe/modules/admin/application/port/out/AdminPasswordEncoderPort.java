package momzzangseven.mztkbe.modules.admin.application.port.out;

/** Output port for encoding and verifying admin passwords. */
public interface AdminPasswordEncoderPort {

  /** Encode a raw password using BCrypt. */
  String encode(String rawPassword);

  /** Check if a raw password matches the encoded hash. */
  boolean matches(String rawPassword, String encodedPassword);
}
