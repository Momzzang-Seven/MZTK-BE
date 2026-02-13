package momzzangseven.mztkbe.modules.comment.application.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "comment.hard-delete")
public class CommentHardDeleteProperties {
  private int retentionDays = 30;
  private int batchSize = 100;
}
