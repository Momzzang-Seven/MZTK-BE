package momzzangseven.mztkbe.modules.admin.board.infrastructure.config;

import lombok.Setter;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.LoadAdminBoardPostListPolicyPort;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for admin board post list policies.
 *
 * <p>Bound from the {@code mztk.admin.board.posts} prefix in application.yml.
 */
@Setter
@ConfigurationProperties(prefix = "mztk.admin.board.posts")
public class AdminBoardPostListProperties implements LoadAdminBoardPostListPolicyPort {

  /** Maximum matching posts scanned for in-memory COMMENT_COUNT sort. Defaults to 5000. */
  private int commentCountSortMaxScanSize = 5000;

  @Override
  public int maxCommentCountSortScanSize() {
    return commentCountSortMaxScanSize;
  }
}
