package momzzangseven.mztkbe.global.error.code;

import org.springframework.http.HttpStatus;

public interface ErrorCode {

  String getCode();

  String getMessage();

  HttpStatus getHttpStatus();

  default String formatMessage(Object... args) {
    return String.format(getMessage(), args);
  }
}
