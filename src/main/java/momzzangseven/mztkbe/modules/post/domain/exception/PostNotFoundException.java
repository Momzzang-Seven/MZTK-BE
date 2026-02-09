package momzzangseven.mztkbe.modules.post.domain.exception;

/** 게시글을 찾을 수 없을 때 발생하는 예외 */
public class PostNotFoundException extends RuntimeException {
  public PostNotFoundException(String message) {
    super(message);
  }
}
