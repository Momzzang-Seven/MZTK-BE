package momzzangseven.mztkbe.modules.level.application.handler;

import java.time.LocalDateTime;
import java.time.ZoneId;
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
  private final ZoneId appZoneId;

  @EventListener
  public void handlePostCreated(PostCreatedEvent event) {
    if (event.type() == PostType.FREE) {
      GrantXpCommand command =
          GrantXpCommand.of(
              event.userId(),
              XpType.POST,
              LocalDateTime.now(appZoneId),
              "post:" + event.postId(),
              "Create Post ID: " + event.postId());
      grantXpUseCase.execute(command);
    }
  }
}
