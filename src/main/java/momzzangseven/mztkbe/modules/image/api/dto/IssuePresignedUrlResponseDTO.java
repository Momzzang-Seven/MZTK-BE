package momzzangseven.mztkbe.modules.image.api.dto;

import java.util.List;
import momzzangseven.mztkbe.modules.image.application.dto.IssuePresignedUrlResult;
import momzzangseven.mztkbe.modules.image.application.dto.PresignedUrlItem;

/** Response DTO for IssuePresignedUrl use case. */
public record IssuePresignedUrlResponseDTO(List<PresignedUrlItemDTO> items) {

  /**
   * Factory method converting application layer result to response DTO.
   *
   * @param result Application layer result
   * @return Response DTO
   * @return
   */
  public static IssuePresignedUrlResponseDTO from(IssuePresignedUrlResult result) {
    List<PresignedUrlItemDTO> items =
        result.items().stream().map(PresignedUrlItemDTO::from).toList();
    return new IssuePresignedUrlResponseDTO(items);
  }

  /** Nested DTO for presigned url item. */
  public record PresignedUrlItemDTO(String presignedUrl, String tmpObjectKey) {

    public static PresignedUrlItemDTO from(PresignedUrlItem item) {
      return new PresignedUrlItemDTO(item.presignedUrl(), item.tmpObjectKey());
    }
  }
}
