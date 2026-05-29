package momzzangseven.mztkbe.modules.level.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.comment.domain.event.CommentCreatedEvent;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpCommand;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpResult;
import momzzangseven.mztkbe.modules.level.application.port.in.GrantXpUseCase;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import momzzangseven.mztkbe.modules.location.domain.event.LocationVerifiedEvent;
import momzzangseven.mztkbe.modules.post.domain.event.PostCreatedEvent;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("XpGrantEventHandler unit test")
class XpGrantEventHandlerTest {

  @Mock private GrantXpUseCase grantXpUseCase;

  private XpGrantEventHandler handler;

  @BeforeEach
  void setUp() {
    handler = new XpGrantEventHandler(grantXpUseCase);
  }

  @Test
  @DisplayName("onCommentCreated grants COMMENT XP with byte-identical idempotency key")
  void onCommentCreated_grantsCommentXp() {
    given(grantXpUseCase.execute(any())).willReturn(grantedResult());
    LocalDateTime occurredAt = LocalDateTime.of(2026, 5, 29, 10, 0);

    handler.onCommentCreated(new CommentCreatedEvent(7L, 42L, occurredAt));

    GrantXpCommand command = captureCommand();
    assertThat(command.userId()).isEqualTo(7L);
    assertThat(command.xpType()).isEqualTo(XpType.COMMENT);
    assertThat(command.occurredAt()).isEqualTo(occurredAt);
    assertThat(command.idempotencyKey()).isEqualTo("comment:create:42");
    assertThat(command.sourceRef()).isEqualTo("comment-creation:42");
  }

  @Test
  @DisplayName("onPostCreated grants POST XP with byte-identical idempotency key")
  void onPostCreated_grantsPostXp() {
    given(grantXpUseCase.execute(any())).willReturn(grantedResult());
    LocalDateTime occurredAt = LocalDateTime.of(2026, 5, 29, 11, 0);

    handler.onPostCreated(new PostCreatedEvent(3L, 20L, PostType.QUESTION, occurredAt));

    GrantXpCommand command = captureCommand();
    assertThat(command.userId()).isEqualTo(3L);
    assertThat(command.xpType()).isEqualTo(XpType.POST);
    assertThat(command.occurredAt()).isEqualTo(occurredAt);
    assertThat(command.idempotencyKey()).isEqualTo("post:create:20");
    assertThat(command.sourceRef()).isEqualTo("post-creation:20");
  }

  @Test
  @DisplayName("onLocationVerified grants WORKOUT XP with date-scoped idempotency key")
  void onLocationVerified_grantsWorkoutXp() {
    given(grantXpUseCase.execute(any())).willReturn(grantedResult());
    LocalDateTime verifiedAt = LocalDateTime.of(2026, 5, 29, 12, 30);

    handler.onLocationVerified(new LocationVerifiedEvent(9L, 5L, verifiedAt));

    GrantXpCommand command = captureCommand();
    assertThat(command.userId()).isEqualTo(9L);
    assertThat(command.xpType()).isEqualTo(XpType.WORKOUT);
    assertThat(command.occurredAt()).isEqualTo(verifiedAt);
    assertThat(command.idempotencyKey()).isEqualTo("workout:location-verify:9:5:20260529");
    assertThat(command.sourceRef()).isEqualTo("location-verification:5");
  }

  @Test
  @DisplayName("handler never rethrows when XP grant fails (catch-and-log)")
  void grantFailure_isSwallowed() {
    given(grantXpUseCase.execute(any())).willThrow(new IllegalStateException("xp system down"));

    assertThatCode(
            () ->
                handler.onCommentCreated(
                    new CommentCreatedEvent(7L, 42L, LocalDateTime.of(2026, 5, 29, 10, 0))))
        .doesNotThrowAnyException();
  }

  private GrantXpCommand captureCommand() {
    ArgumentCaptor<GrantXpCommand> captor = ArgumentCaptor.forClass(GrantXpCommand.class);
    verify(grantXpUseCase).execute(captor.capture());
    return captor.getValue();
  }

  private static GrantXpResult grantedResult() {
    return GrantXpResult.granted(10, 5, 1, LocalDate.of(2026, 5, 29));
  }
}
