package momzzangseven.mztkbe.modules.post.domain.exception;

/** 게시글에 대한 수정/삭제 권한이 없을 때 발생하는 예외 (403 Forbidden 대응) */
public class PostUnauthorizedException extends RuntimeException {
  public PostUnauthorizedException(String message) {
    super(message);
  }
}
