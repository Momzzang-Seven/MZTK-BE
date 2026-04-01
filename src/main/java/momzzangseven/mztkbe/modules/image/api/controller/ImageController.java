package momzzangseven.mztkbe.modules.image.api.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.image.api.dto.GetImagesByIdsResponseDTO;
import momzzangseven.mztkbe.modules.image.api.dto.IssuePresignedUrlRequestDTO;
import momzzangseven.mztkbe.modules.image.api.dto.IssuePresignedUrlResponseDTO;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByIdsCommand;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByIdsResult;
import momzzangseven.mztkbe.modules.image.application.dto.IssuePresignedUrlCommand;
import momzzangseven.mztkbe.modules.image.application.dto.IssuePresignedUrlResult;
import momzzangseven.mztkbe.modules.image.application.port.in.GetImagesByIdsUseCase;
import momzzangseven.mztkbe.modules.image.application.port.in.IssuePresignedUrlUseCase;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for image-related operations. */
@RestController
@RequestMapping("/images")
@RequiredArgsConstructor
public class ImageController {

  private final IssuePresignedUrlUseCase issuePresignedUrlUseCase;
  private final GetImagesByIdsUseCase getImagesByIdsUseCase;

  /**
   * Issues S3 pre-signed PUT URLs and registers PENDING image records.
   *
   * @param userId authenticated user ID from JWT
   * @param request referenceType + list of image filenames
   * @return list of { presignedUrl, tmpObjectKey } per image
   */
  @PostMapping("/presigned-urls")
  public ResponseEntity<ApiResponse<IssuePresignedUrlResponseDTO>> issuePresignedUrls(
      @AuthenticationPrincipal Long userId,
      @Valid @RequestBody IssuePresignedUrlRequestDTO request) {

    userId = requireUserId(userId);

    IssuePresignedUrlCommand command = request.toCommand(userId);

    IssuePresignedUrlResult result = issuePresignedUrlUseCase.execute(command);

    return ResponseEntity.ok(ApiResponse.success(IssuePresignedUrlResponseDTO.from(result)));
  }

  /**
   * Returns image metadata for a given list of image IDs, referenceType, and referenceId.
   *
   * @param userId authenticated user ID from JWT
   * @param ids list of image IDs to look up
   * @param referenceType reference type the images belong to
   * @param referenceId reference entity ID the images belong to
   * @return list of image metadata items; non-existent IDs are silently excluded
   */
  @GetMapping
  public ResponseEntity<ApiResponse<GetImagesByIdsResponseDTO>> getImagesByIds(
      @AuthenticationPrincipal Long userId,
      @RequestParam List<Long> ids,
      @RequestParam ImageReferenceType referenceType,
      @RequestParam Long referenceId) {

    userId = requireUserId(userId);

    GetImagesByIdsCommand command =
        new GetImagesByIdsCommand(userId, referenceType, referenceId, ids);

    GetImagesByIdsResult result = getImagesByIdsUseCase.execute(command);

    return ResponseEntity.ok(ApiResponse.success(GetImagesByIdsResponseDTO.from(result)));
  }

  private Long requireUserId(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
    return userId;
  }
}
