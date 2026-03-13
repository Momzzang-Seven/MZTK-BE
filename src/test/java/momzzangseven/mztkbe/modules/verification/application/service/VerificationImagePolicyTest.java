package momzzangseven.mztkbe.modules.verification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import momzzangseven.mztkbe.modules.verification.application.config.VerificationRuntimeProperties;
import org.junit.jupiter.api.Test;

class VerificationImagePolicyTest {

  @Test
  void rejectsOversizedOriginalBytes() {
    VerificationImagePolicy policy =
        new VerificationImagePolicy(
            new VerificationRuntimeProperties(
                new VerificationRuntimeProperties.Ai("gemini-2.5-flash-lite", 12, 2, true),
                new VerificationRuntimeProperties.Heif(true, "requires-imageio-plugin"),
                new VerificationRuntimeProperties.Image(2L, 25000000L)));

    assertThatThrownBy(() -> policy.validateOriginalSize(new byte[] {1, 2, 3}))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsOversizedPixelCount() {
    VerificationImagePolicy policy =
        new VerificationImagePolicy(
            new VerificationRuntimeProperties(
                new VerificationRuntimeProperties.Ai("gemini-2.5-flash-lite", 12, 2, true),
                new VerificationRuntimeProperties.Heif(true, "requires-imageio-plugin"),
                new VerificationRuntimeProperties.Image(5242880L, 10L)));

    assertThatThrownBy(
            () -> policy.validatePixels(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void exposesHeifToggle() {
    VerificationImagePolicy policy =
        new VerificationImagePolicy(
            new VerificationRuntimeProperties(
                new VerificationRuntimeProperties.Ai("gemini-2.5-flash-lite", 12, 2, true),
                new VerificationRuntimeProperties.Heif(false, "requires-imageio-plugin"),
                new VerificationRuntimeProperties.Image(5242880L, 25000000L)));

    assertThat(policy.isHeifEnabled()).isFalse();
  }
}
