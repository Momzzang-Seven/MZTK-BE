package momzzangseven.mztkbe.global.error.image;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class InvalidImageExtensionException extends BusinessException {
    public InvalidImageExtensionException(String msg) {
        super(ErrorCode.IMAGE_INVALID_EXTENSION, msg);
    }
}
