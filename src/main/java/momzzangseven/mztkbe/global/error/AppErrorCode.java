package momzzangseven.mztkbe.global.error;

import org.springframework.http.HttpStatus;

public interface AppErrorCode {

  String getCode();

  String getMessage();

  HttpStatus getHttpStatus();

  default String formatMessage(Object... args) {
    return String.format(getMessage(), args);
  }
}
