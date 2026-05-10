package momzzangseven.mztkbe.modules.post.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.post.PostNotFoundException;
import momzzangseven.mztkbe.modules.post.application.dto.ManagedBoardPostTargetView;
import momzzangseven.mztkbe.modules.post.application.port.in.GetManagedBoardPostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadManagedBoardPostPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for loading one post target for admin board moderation. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetManagedBoardPostService implements GetManagedBoardPostUseCase {

  private final LoadManagedBoardPostPort loadManagedBoardPostPort;

  @Override
  public ManagedBoardPostTargetView execute(Long postId) {
    return loadManagedBoardPostPort.load(postId).orElseThrow(PostNotFoundException::new);
  }
}
