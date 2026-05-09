package momzzangseven.mztkbe.modules.post.application.port.out;

public interface LoadAnswerCreateIntentConflictPort {

  boolean hasActiveAnswerCreateIntent(Long postId);
}
