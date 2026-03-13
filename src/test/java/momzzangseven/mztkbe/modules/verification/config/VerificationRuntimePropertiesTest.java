package momzzangseven.mztkbe.modules.verification.config;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.verification.application.config.VerificationRuntimeProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class VerificationRuntimePropertiesTest {

  @Test
  @DisplayName("verification runtime properties가 정상 바인딩된다")
  void bindsRuntimeProperties() {
    MapConfigurationPropertySource source =
        new MapConfigurationPropertySource(
            java.util.Map.of(
                "verification.ai.model", "gemini-2.5-flash-lite",
                "verification.ai.timeout-seconds", "12",
                "verification.ai.retry-count", "2",
                "verification.ai.stub-enabled", "true",
                "verification.heif.enabled", "true",
                "verification.image.max-original-bytes", "5242880",
                "verification.image.max-pixels", "25000000"));

    Binder binder = new Binder(source);
    VerificationRuntimeProperties properties =
        binder
            .bind("verification", Bindable.of(VerificationRuntimeProperties.class))
            .orElseThrow(() -> new IllegalStateException("verification properties bind failed"));
    assertThat(properties.ai().model()).isEqualTo("gemini-2.5-flash-lite");
    assertThat(properties.ai().timeoutSeconds()).isEqualTo(12);
    assertThat(properties.ai().retryCount()).isEqualTo(2);
    assertThat(properties.ai().stubEnabled()).isTrue();
    assertThat(properties.heif().enabled()).isTrue();
    assertThat(properties.image().maxOriginalBytes()).isEqualTo(5242880L);
    assertThat(properties.image().maxPixels()).isEqualTo(25000000L);
  }
}
