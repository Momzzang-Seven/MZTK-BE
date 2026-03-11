package momzzangseven.mztkbe.modules.image.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.image.api.dto.IssuePresignedUrlRequestDTO;
import momzzangseven.mztkbe.modules.image.api.dto.IssuePresignedUrlResponseDTO;
import momzzangseven.mztkbe.modules.image.application.dto.IssuePresignedUrlCommand;
import momzzangseven.mztkbe.modules.image.application.dto.IssuePresignedUrlResult;
import momzzangseven.mztkbe.modules.image.application.port.in.IssuePresignedUrlUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for image-related operations. */
@RestController
@RequestMapping("/images")
@RequiredArgsConstructor
public class ImageController {

  private final IssuePresignedUrlUseCase issuePresignedUrlUseCase;

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

  private Long requireUserId(Long userId) {
    if (userId == null) {
      throw new UserNotAuthenticatedException();
    }
    return userId;
  }
}
