package momzzangseven.mztkbe.modules.post.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;
import momzzangseven.mztkbe.global.error.post.PostNotFoundException;
import momzzangseven.mztkbe.global.security.aspect.AdminOnly;
import momzzangseven.mztkbe.modules.post.application.dto.ModeratePostCommand;
import momzzangseven.mztkbe.modules.post.application.dto.ModeratePostResult;
import momzzangseven.mztkbe.modules.post.application.port.in.BlockPostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.ModerateManagedPostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.UnblockPostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostModerationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ModeratePostService
    implements BlockPostUseCase, UnblockPostUseCase, ModerateManagedPostUseCase {

  private final PostPersistencePort postPersistencePort;

  @Override
  @Transactional
  @AdminOnly(
      actionType = "POST_BLOCK",
      targetType = AuditTargetType.POST_MODERATION,
      operatorId = "#p0.operatorId()",
      targetId = "'post:' + #p0.postId()")
  public ModeratePostResult blockPost(ModeratePostCommand command) {
    return blockManagedPost(command);
  }

  @Override
  @Transactional
  public ModeratePostResult blockManagedPost(ModeratePostCommand command) {
    command.validate();
    Post post =
        postPersistencePort
            .loadPostForUpdate(command.postId())
            .orElseThrow(PostNotFoundException::new);
    return changeModerationStatus(post, PostModerationStatus.BLOCKED);
  }

  @Override
  @Transactional
  @AdminOnly(
      actionType = "POST_UNBLOCK",
      targetType = AuditTargetType.POST_MODERATION,
      operatorId = "#p0.operatorId()",
      targetId = "'post:' + #p0.postId()")
  public ModeratePostResult unblockPost(ModeratePostCommand command) {
    return unblockManagedPost(command);
  }

  @Override
  @Transactional
  public ModeratePostResult unblockManagedPost(ModeratePostCommand command) {
    command.validate();
    Post post =
        postPersistencePort
            .loadPostForUpdate(command.postId())
            .orElseThrow(PostNotFoundException::new);
    return changeModerationStatus(post, PostModerationStatus.NORMAL);
  }

  private ModeratePostResult changeModerationStatus(
      Post post, PostModerationStatus targetModerationStatus) {
    Post changedPost =
        targetModerationStatus == PostModerationStatus.BLOCKED ? post.block() : post.unblock();
    boolean moderated = post.getModerationStatus() != changedPost.getModerationStatus();
    Post resultPost = moderated ? postPersistencePort.savePost(changedPost) : post;
    return new ModeratePostResult(
        resultPost.getId(),
        moderated,
        resultPost.getPublicationStatus(),
        resultPost.getModerationStatus());
  }
}
