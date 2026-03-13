package momzzangseven.mztkbe.modules.verification.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "verification")
public record VerificationRuntimeProperties(Ai ai, Heif heif, Image image) {

  public VerificationRuntimeProperties {
    if (ai == null) {
      ai = new Ai("gemini-2.5-flash-lite", 12, 2, false);
    }
    if (heif == null) {
      heif = new Heif(true, "requires-imageio-plugin");
    }
    if (image == null) {
      image = new Image(5242880L, 25000000L);
    }
  }

  public record Ai(String model, int timeoutSeconds, int retryCount, boolean stubEnabled) {

    public Ai {
      if (model == null || model.isBlank()) {
        model = "gemini-2.5-flash-lite";
      }
      if (timeoutSeconds <= 0) {
        timeoutSeconds = 12;
      }
      if (retryCount < 0) {
        retryCount = 0;
      }
    }
  }

  public record Heif(boolean enabled, String decoderPolicy) {

    public Heif {
      if (decoderPolicy == null || decoderPolicy.isBlank()) {
        decoderPolicy = "requires-imageio-plugin";
      }
    }
  }

  public record Image(long maxOriginalBytes, long maxPixels) {

    public Image {
      if (maxOriginalBytes <= 0) {
        maxOriginalBytes = 5242880L;
      }
      if (maxPixels <= 0) {
        maxPixels = 25000000L;
      }
    }
  }
}
