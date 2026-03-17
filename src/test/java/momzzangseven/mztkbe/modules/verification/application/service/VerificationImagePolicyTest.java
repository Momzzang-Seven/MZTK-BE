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
  void rejectsEmptyOriginalBytes() {
    VerificationImagePolicy policy =
        new VerificationImagePolicy(
            new VerificationRuntimeProperties(
                new VerificationRuntimeProperties.Ai("gemini-2.5-flash-lite", 12, 2, true),
                new VerificationRuntimeProperties.Heif(true, "requires-imageio-plugin"),
                new VerificationRuntimeProperties.Image(5242880L, 25000000L)));

    assertThatThrownBy(() -> policy.validateOriginalSize(new byte[0]))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("empty");
  }

  @Test
  void rejectsNullOriginalBytes() {
    VerificationImagePolicy policy =
        new VerificationImagePolicy(
            new VerificationRuntimeProperties(
                new VerificationRuntimeProperties.Ai("gemini-2.5-flash-lite", 12, 2, true),
                new VerificationRuntimeProperties.Heif(true, "requires-imageio-plugin"),
                new VerificationRuntimeProperties.Image(5242880L, 25000000L)));

    assertThatThrownBy(() -> policy.validateOriginalSize(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("empty");
  }

  @Test
  void acceptsValidOriginalBytes() {
    VerificationImagePolicy policy =
        new VerificationImagePolicy(
            new VerificationRuntimeProperties(
                new VerificationRuntimeProperties.Ai("gemini-2.5-flash-lite", 12, 2, true),
                new VerificationRuntimeProperties.Heif(true, "requires-imageio-plugin"),
                new VerificationRuntimeProperties.Image(5242880L, 25000000L)));

    policy.validateOriginalSize(new byte[] {1, 2, 3});
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
  void acceptsPixelCountWithinLimit() {
    VerificationImagePolicy policy =
        new VerificationImagePolicy(
            new VerificationRuntimeProperties(
                new VerificationRuntimeProperties.Ai("gemini-2.5-flash-lite", 12, 2, true),
                new VerificationRuntimeProperties.Heif(true, "requires-imageio-plugin"),
                new VerificationRuntimeProperties.Image(5242880L, 100L)));

    policy.validatePixels(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB));
  }

  @Test
  void validatesObjectMetadataForJpeg() {
    VerificationImagePolicy policy =
        new VerificationImagePolicy(
            new VerificationRuntimeProperties(
                new VerificationRuntimeProperties.Ai("gemini-2.5-flash-lite", 12, 2, true),
                new VerificationRuntimeProperties.Heif(true, "requires-imageio-plugin"),
                new VerificationRuntimeProperties.Image(5242880L, 25000000L)));

    policy.validateObjectMetadata(100L, "image/jpeg", "jpg");
  }

  @Test
  void allowsBlankContentTypeAndOctetStream() {
    VerificationImagePolicy policy =
        new VerificationImagePolicy(
            new VerificationRuntimeProperties(
                new VerificationRuntimeProperties.Ai("gemini-2.5-flash-lite", 12, 2, true),
                new VerificationRuntimeProperties.Heif(true, "requires-imageio-plugin"),
                new VerificationRuntimeProperties.Image(5242880L, 25000000L)));

    policy.validateObjectMetadata(100L, null, "jpg");
    policy.validateObjectMetadata(100L, "   ", "jpg");
    policy.validateObjectMetadata(100L, "application/octet-stream", "jpg");
  }

  @Test
  void validatesHeicAndUnknownExtensionBranches() {
    VerificationImagePolicy policy =
        new VerificationImagePolicy(
            new VerificationRuntimeProperties(
                new VerificationRuntimeProperties.Ai("gemini-2.5-flash-lite", 12, 2, true),
                new VerificationRuntimeProperties.Heif(true, "requires-imageio-plugin"),
                new VerificationRuntimeProperties.Image(5242880L, 25000000L)));

    policy.validateObjectMetadata(100L, "image/heic", "heic");
    policy.validateObjectMetadata(100L, "image/webp", "webp");
  }

  @Test
  void rejectsInvalidObjectMetadataCases() {
    VerificationImagePolicy policy =
        new VerificationImagePolicy(
            new VerificationRuntimeProperties(
                new VerificationRuntimeProperties.Ai("gemini-2.5-flash-lite", 12, 2, true),
                new VerificationRuntimeProperties.Heif(true, "requires-imageio-plugin"),
                new VerificationRuntimeProperties.Image(1024L, 25000000L)));

    assertThatThrownBy(() -> policy.validateObjectMetadata(0L, "image/jpeg", "jpg"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("content length must be positive");
    assertThatThrownBy(() -> policy.validateObjectMetadata(2048L, "image/jpeg", "jpg"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("exceeds policy");
    assertThatThrownBy(() -> policy.validateObjectMetadata(100L, "text/plain", "jpg"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("content type is invalid");
    assertThatThrownBy(() -> policy.validateObjectMetadata(100L, "image/png", "jpg"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does not match extension");
    assertThatThrownBy(() -> policy.validateObjectMetadata(100L, "image/jpeg", "heif"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does not match extension");
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
