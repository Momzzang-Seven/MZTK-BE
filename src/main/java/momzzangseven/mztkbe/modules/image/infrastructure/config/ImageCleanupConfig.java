package momzzangseven.mztkbe.modules.image.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers cleanup-related {@link
 * org.springframework.boot.context.properties.ConfigurationProperties} beans.
 */
@Configuration
@EnableConfigurationProperties({
  ImageUnlinkedCleanupProperties.class,
  ImagePendingCleanupProperties.class
})
public class ImageCleanupConfig {}
