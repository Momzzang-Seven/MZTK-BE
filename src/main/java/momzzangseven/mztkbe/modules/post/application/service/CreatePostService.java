package momzzangseven.mztkbe.modules.post.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostCommand;
import momzzangseven.mztkbe.modules.post.application.port.in.CreatePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.event.PostCreatedEvent;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CreatePostService implements CreatePostUseCase {

  private final PostPersistencePort postPersistencePort;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  public Long createPost(CreatePostCommand command) {
    command.validate();

    Post post =
        Post.builder()
            .userId(command.userId())
            .type(command.type())
            .title(command.title())
            .content(command.content())
            .reward(command.reward())
            .imageUrls(command.imageUrls())
            .build();

    Post savedPost = postPersistencePort.savePost(post);

    // 3. 이벤트 발행
    eventPublisher.publishEvent(
        new PostCreatedEvent(savedPost.getUserId(), savedPost.getId(), savedPost.getType()));

    return savedPost.getId();
  }
}
