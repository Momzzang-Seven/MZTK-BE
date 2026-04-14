package momzzangseven.mztkbe.global.error.image;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class ImageLambdaUnauthorizedException extends BusinessException {
  public ImageLambdaUnauthorizedException() {
    super(ErrorCode.IMAGE_LAMBDA_UNAUTHORIZED);
  }
}
