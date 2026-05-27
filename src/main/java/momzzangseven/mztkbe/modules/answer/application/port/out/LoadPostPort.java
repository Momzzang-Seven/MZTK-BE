package momzzangseven.mztkbe.modules.answer.application.port.out;

import java.util.Optional;

public interface LoadPostPort {

  Optional<PostContext> loadPost(Long postId);

  /**
   * Loads the post with a pessimistic row lock for lost-update guards on answer write paths.
   *
   * <p>Must be invoked from inside an outer read-write transaction; the underlying post-module
   * use-case enforces {@code Propagation.MANDATORY}. Use this whenever the caller validates a
   * delete-blocking / mutate-blocking invariant on the post and then either persists answer state
   * or hands {@code post.content} / {@code post.reward} to an external (on-chain) payload — without
   * the lock, a concurrent post mutation can slip between validate and act, producing FK violations
   * or escrow-payload divergence (MOM-459 answer write-path guard).
   */
  Optional<PostContext> loadPostForUpdate(Long postId);

  record PostContext(
      Long postId,
      Long writerId,
      boolean isSolved,
      boolean questionPost,
      String content,
      Long reward,
      boolean answerLocked,
      boolean publiclyVisible) {

    public PostContext(Long postId, Long writerId, boolean isSolved, boolean questionPost) {
      this(postId, writerId, isSolved, questionPost, null, null, isSolved, true);
    }

    public PostContext(
        Long postId,
        Long writerId,
        boolean isSolved,
        boolean questionPost,
        String content,
        Long reward) {
      this(postId, writerId, isSolved, questionPost, content, reward, isSolved, true);
    }

    public PostContext(
        Long postId,
        Long writerId,
        boolean isSolved,
        boolean questionPost,
        String content,
        Long reward,
        boolean answerLocked) {
      this(postId, writerId, isSolved, questionPost, content, reward, answerLocked, true);
    }

    public boolean readableBy(Long requesterUserId) {
      return publiclyVisible || (requesterUserId != null && writerId.equals(requesterUserId));
    }

    public boolean writable() {
      return publiclyVisible;
    }
  }
}
