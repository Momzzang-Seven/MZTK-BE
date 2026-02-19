package momzzangseven.mztkbe.modules.tag.infrastructure.event;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.domain.event.PostDeletedEvent;
import momzzangseven.mztkbe.modules.tag.application.port.in.TagLinkUseCase;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TagEventListener {
    private final TagLinkUseCase tagLinkUseCase;

    @EventListener
    public void handlePostDeletedEvent(PostDeletedEvent event) {
        tagLinkUseCase.deleteTagsByPostId(event.postId());
    }
}