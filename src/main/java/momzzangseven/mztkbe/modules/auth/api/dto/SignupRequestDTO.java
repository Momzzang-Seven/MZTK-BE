package momzzangseven.mztkbe.modules.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Signup request DTO for LOCAL authentication. */
@Getter
@NoArgsConstructor
public class SignupRequestDTO {
  /**
   * User's email address.
   *
   * <p>Constraints: - Must be a valid email format - Cannot be blank - Duplicate check is performed
   * in the service layer
   *
   * <p>Example: "newuser@example.com"
   */
  @NotBlank(message = "Email is required")
  @Email(message = "Invalid email format")
  private String email;

  /**
   * User's password.
   *
   * <p>Constraints: - Minimum 8 characters - Must include at least one special character - Must
   * include at least one letter and one number
   *
   * <p>Example: "StrongPassword123!"
   */
  @NotBlank(message = "Password is required")
  @Size(min = 8, message = "Password must be at least 8 characters long")
  @Pattern(
      regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,}$",
      message = "Password must include at least one letter, one number, and one special character")
  private String password;

  /**
   * User's display nickname.
   *
   * <p>Constraints: - Cannot be blank - Between 2 and 50 characters
   *
   * <p>Example: "gildong"
   */
  @NotBlank(message = "Nickname is required")
  @Size(min = 2, max = 50, message = "Nickname must be between 2 and 50 characters")
  private String nickname;
}
