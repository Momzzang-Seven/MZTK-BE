package momzzangseven.mztkbe.modules.post.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.post.PostNotFoundException;
import momzzangseven.mztkbe.modules.post.application.dto.ModeratePostCommand;
import momzzangseven.mztkbe.modules.post.application.port.in.BlockPostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.UnblockPostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ModeratePostService implements BlockPostUseCase, UnblockPostUseCase {

  private final PostPersistencePort postPersistencePort;

  @Override
  @Transactional
  public void blockPost(ModeratePostCommand command) {
    command.validate();
    postPersistencePort
        .loadPostForUpdate(command.postId())
        .map(post -> postPersistencePort.savePost(post.block()))
        .orElseThrow(PostNotFoundException::new);
  }

  @Override
  @Transactional
  public void unblockPost(ModeratePostCommand command) {
    command.validate();
    postPersistencePort
        .loadPostForUpdate(command.postId())
        .map(post -> postPersistencePort.savePost(post.unblock()))
        .orElseThrow(PostNotFoundException::new);
  }
}
