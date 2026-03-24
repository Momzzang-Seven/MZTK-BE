package momzzangseven.mztkbe.modules.post.infrastructure.external.image.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "cloud.aws.s3")
public class PostImageStorageProperties {

  @NotBlank private String urlPrefix;
}
