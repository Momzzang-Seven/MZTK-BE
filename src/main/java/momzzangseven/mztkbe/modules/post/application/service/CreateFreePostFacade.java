package momzzangseven.mztkbe.modules.post.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostCommand;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostResult;
import momzzangseven.mztkbe.modules.post.application.port.in.CreatePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.GrantPostXpPort;
import org.springframework.stereotype.Service;

/**
 * Orchestrates free-post creation as two sequential transactions to keep connection occupancy at
 * one: T1 saves the post (commits and releases its connection), then T2 grants XP on a fresh
 * connection.
 *
 * <p>Intentionally <b>not</b> {@code @Transactional} — wrapping it would suspend nothing and
 * instead keep T1's connection open across the XP grant, defeating the purpose. XP-grant failures
 * are absorbed by the guaranteed-delivery path behind {@link GrantPostXpPort}, so a saved post is
 * never lost to a transient grant error.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateFreePostFacade implements CreatePostUseCase {

  private final CreatePostService createPostService;
  private final GrantPostXpPort grantPostXpPort;

  @Override
  public CreatePostResult execute(CreatePostCommand command) {
    Long postId = createPostService.createFreePost(command);

    Long grantedXp = grantPostXpPort.grantCreatePostXp(command.userId(), postId);
    boolean xpGranted = grantedXp != null && grantedXp > 0;
    String message = xpGranted ? "게시글 작성 완료! (+" + grantedXp + " XP)" : "게시글 작성 완료";

    return new CreatePostResult(postId, xpGranted, grantedXp == null ? 0L : grantedXp, message);
  }
}
