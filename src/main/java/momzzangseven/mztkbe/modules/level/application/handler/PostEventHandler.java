package momzzangseven.mztkbe.modules.level.application.handler;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpCommand;
import momzzangseven.mztkbe.modules.level.application.port.in.GrantXpUseCase;
import momzzangseven.mztkbe.modules.level.domain.model.XpType;
import momzzangseven.mztkbe.modules.post.domain.event.PostCreatedEvent;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostEventHandler {

  private final GrantXpUseCase grantXpUseCase;

  @EventListener
  public void handlePostCreated(PostCreatedEvent event) {
    // 자유게시판(FREE)인 경우에만 경험치 지급 로직 수행
    if (event.type() == PostType.FREE) {
      GrantXpCommand command =
          GrantXpCommand.of(
              event.userId(),
              XpType.POST,
              LocalDateTime.now(),
              "post:" + event.postId(),
              "Create Post ID: " + event.postId());

      grantXpUseCase.execute(command);
    }
  }
}
