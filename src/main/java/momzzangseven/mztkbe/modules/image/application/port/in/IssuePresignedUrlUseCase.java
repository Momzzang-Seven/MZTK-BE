package momzzangseven.mztkbe.modules.image.application.port.in;

import momzzangseven.mztkbe.modules.image.application.dto.IssuePresignedUrlCommand;
import momzzangseven.mztkbe.modules.image.application.dto.IssuePresignedUrlResult;

/** Input port for issuing S3 pre-signed URLs and recording PENDING image rows. */
public interface IssuePresignedUrlUseCase {

  IssuePresignedUrlResult execute(IssuePresignedUrlCommand command);
}
