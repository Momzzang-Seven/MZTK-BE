package momzzangseven.mztkbe.modules.image.application.dto;

import java.util.List;

/** Output result of ImageController. */
public record IssuePresignedUrlResult(List<PresignedUrlItem> items) {

  public static IssuePresignedUrlResult of(List<PresignedUrlItem> items) {
    return new IssuePresignedUrlResult(items);
  }
}
