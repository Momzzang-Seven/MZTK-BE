package momzzangseven.mztkbe.modules.post.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;
import momzzangseven.mztkbe.global.error.post.PostPublicationStateException;
import momzzangseven.mztkbe.modules.post.application.dto.RecoverQuestionPostEscrowCommand;
import momzzangseven.mztkbe.modules.post.application.port.out.CountAnswersPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LinkTagPort;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadQuestionPublicationEvidencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionLifecycleExecutionPort;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionPublicationEvidence;
import momzzangseven.mztkbe.modules.post.application.port.out.UpdatePostImagesPort;
import momzzangseven.mztkbe.modules.post.application.port.out.ValidatePostImagesPort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostModerationStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostPublicationStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecoverQuestionPostEscrowService unit test")
class RecoverQuestionPostEscrowServiceTest {

  @Mock private PostPersistencePort postPersistencePort;
  @Mock private QuestionLifecycleExecutionPort questionLifecycleExecutionPort;
  @Mock private LoadQuestionPublicationEvidencePort loadQuestionPublicationEvidencePort;
  @Mock private CountAnswersPort countAnswersPort;
  @Mock private ValidatePostImagesPort validatePostImagesPort;
  @Mock private UpdatePostImagesPort updatePostImagesPort;
  @Mock private LinkTagPort linkTagPort;
  @Spy private PostVisibilityPolicy postVisibilityPolicy = new PostVisibilityPolicy();

  @InjectMocks private RecoverQuestionPostEscrowService service;

  @Test
  @DisplayName(
      "managed no-body recovery marks failed question pending and retries existing content")
  void managedNoBodyRecoveryMarksPendingAndRetriesExistingContent() {
    Long ownerId = 7L;
    Long postId = 90L;
    Post post = failedQuestion(ownerId, postId);
    RecoverQuestionPostEscrowCommand command =
        new RecoverQuestionPostEscrowCommand(ownerId, postId);

    when(postPersistencePort.loadPostForUpdate(postId)).thenReturn(Optional.of(post));
    when(questionLifecycleExecutionPort.managesQuestionCreateLifecycle()).thenReturn(true);
    when(loadQuestionPublicationEvidencePort.loadEvidence(postId, ownerId))
        .thenReturn(new QuestionPublicationEvidence(true, false, false, true, "EXPIRED"));

    service.recoverQuestionCreate(command);

    ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
    verify(postPersistencePort).savePost(postCaptor.capture());
    assertThat(postCaptor.getValue().getPublicationStatus())
        .isEqualTo(PostPublicationStatus.PENDING);
    verify(questionLifecycleExecutionPort).recoverQuestionCreate(postId, ownerId, "질문 내용", 50L);
    verifyNoInteractions(
        countAnswersPort, validatePostImagesPort, linkTagPort, updatePostImagesPort);
  }

  @Test
  @DisplayName("managed edit recovery saves edited pending post and syncs explicit tags and images")
  void managedEditRecoverySavesEditedPendingPostAndSyncsTagsAndImages() {
    Long ownerId = 7L;
    Long postId = 91L;
    Post post = failedQuestion(ownerId, postId);
    RecoverQuestionPostEscrowCommand command =
        new RecoverQuestionPostEscrowCommand(
            ownerId, postId, "수정 제목", "수정 내용", List.of(), List.of());

    when(postPersistencePort.loadPostForUpdate(postId)).thenReturn(Optional.of(post));
    when(questionLifecycleExecutionPort.managesQuestionCreateLifecycle()).thenReturn(true);
    when(loadQuestionPublicationEvidencePort.loadEvidence(postId, ownerId))
        .thenReturn(new QuestionPublicationEvidence(true, false, false, true, "EXPIRED"));
    when(countAnswersPort.countAnswers(postId)).thenReturn(0L);

    service.recoverQuestionCreate(command);

    ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
    verify(postPersistencePort).savePost(postCaptor.capture());
    Post saved = postCaptor.getValue();
    assertThat(saved.getTitle()).isEqualTo("수정 제목");
    assertThat(saved.getContent()).isEqualTo("수정 내용");
    assertThat(saved.getTags()).isEmpty();
    assertThat(saved.getPublicationStatus()).isEqualTo(PostPublicationStatus.PENDING);
    verifyNoInteractions(validatePostImagesPort);
    verify(linkTagPort).updateTags(postId, List.of());
    verify(updatePostImagesPort).updateImages(ownerId, postId, PostType.QUESTION, List.of());
    verify(questionLifecycleExecutionPort).recoverQuestionCreate(postId, ownerId, "수정 내용", 50L);
  }

  @Test
  @DisplayName("managed edit recovery validates non-empty image IDs before saving")
  void managedEditRecoveryValidatesNonEmptyImageIdsBeforeSaving() {
    Long ownerId = 7L;
    Long postId = 92L;
    Post post = failedQuestion(ownerId, postId);
    RecoverQuestionPostEscrowCommand command =
        new RecoverQuestionPostEscrowCommand(ownerId, postId, null, "수정 내용", List.of(1L), null);

    when(postPersistencePort.loadPostForUpdate(postId)).thenReturn(Optional.of(post));
    when(questionLifecycleExecutionPort.managesQuestionCreateLifecycle()).thenReturn(true);
    when(loadQuestionPublicationEvidencePort.loadEvidence(postId, ownerId))
        .thenReturn(new QuestionPublicationEvidence(true, false, false, true, "EXPIRED"));
    when(countAnswersPort.countAnswers(postId)).thenReturn(0L);

    service.recoverQuestionCreate(command);

    verify(validatePostImagesPort)
        .validateAttachableImages(ownerId, postId, PostType.QUESTION, List.of(1L));
    verify(updatePostImagesPort).updateImages(ownerId, postId, PostType.QUESTION, List.of(1L));
  }

  @Test
  @DisplayName("managed title-only recovery keeps content and skips tag and image sync")
  void managedTitleOnlyRecoveryKeepsContentAndSkipsTagAndImageSync() {
    Long ownerId = 7L;
    Long postId = 99L;
    Post post = failedQuestion(ownerId, postId);
    RecoverQuestionPostEscrowCommand command =
        new RecoverQuestionPostEscrowCommand(ownerId, postId, "제목만 수정", null, null, null);

    when(postPersistencePort.loadPostForUpdate(postId)).thenReturn(Optional.of(post));
    when(questionLifecycleExecutionPort.managesQuestionCreateLifecycle()).thenReturn(true);
    when(loadQuestionPublicationEvidencePort.loadEvidence(postId, ownerId))
        .thenReturn(new QuestionPublicationEvidence(true, false, false, true, "EXPIRED"));
    when(countAnswersPort.countAnswers(postId)).thenReturn(0L);

    service.recoverQuestionCreate(command);

    ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
    verify(postPersistencePort).savePost(postCaptor.capture());
    Post saved = postCaptor.getValue();
    assertThat(saved.getTitle()).isEqualTo("제목만 수정");
    assertThat(saved.getContent()).isEqualTo("질문 내용");
    assertThat(saved.getPublicationStatus()).isEqualTo(PostPublicationStatus.PENDING);
    verify(questionLifecycleExecutionPort).recoverQuestionCreate(postId, ownerId, "질문 내용", 50L);
    verifyNoInteractions(validatePostImagesPort, linkTagPort, updatePostImagesPort);
  }

  @Test
  @DisplayName("managed tags-only recovery syncs non-empty tags without image sync")
  void managedTagsOnlyRecoverySyncsNonEmptyTagsWithoutImageSync() {
    Long ownerId = 7L;
    Long postId = 100L;
    Post post = failedQuestion(ownerId, postId);
    RecoverQuestionPostEscrowCommand command =
        new RecoverQuestionPostEscrowCommand(
            ownerId, postId, null, null, null, List.of("java", "spring"));

    when(postPersistencePort.loadPostForUpdate(postId)).thenReturn(Optional.of(post));
    when(questionLifecycleExecutionPort.managesQuestionCreateLifecycle()).thenReturn(true);
    when(loadQuestionPublicationEvidencePort.loadEvidence(postId, ownerId))
        .thenReturn(new QuestionPublicationEvidence(true, false, false, true, "EXPIRED"));
    when(countAnswersPort.countAnswers(postId)).thenReturn(0L);

    service.recoverQuestionCreate(command);

    ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
    verify(postPersistencePort).savePost(postCaptor.capture());
    assertThat(postCaptor.getValue().getTags()).containsExactly("java", "spring");
    verify(linkTagPort).updateTags(postId, List.of("java", "spring"));
    verify(questionLifecycleExecutionPort).recoverQuestionCreate(postId, ownerId, "질문 내용", 50L);
    verifyNoInteractions(validatePostImagesPort, updatePostImagesPort);
  }

  @Test
  @DisplayName("managed recovery blocks projection evidence before local side effects")
  void managedRecoveryBlocksProjectionEvidenceBeforeSideEffects() {
    Long ownerId = 7L;
    Long postId = 93L;
    Post post = failedQuestion(ownerId, postId);
    RecoverQuestionPostEscrowCommand command =
        new RecoverQuestionPostEscrowCommand(
            ownerId, postId, "수정 제목", null, List.of(1L), List.of("tag"));

    when(postPersistencePort.loadPostForUpdate(postId)).thenReturn(Optional.of(post));
    when(questionLifecycleExecutionPort.managesQuestionCreateLifecycle()).thenReturn(true);
    when(loadQuestionPublicationEvidencePort.loadEvidence(postId, ownerId))
        .thenReturn(new QuestionPublicationEvidence(true, true, true, true, "PENDING"));

    assertThatThrownBy(() -> service.recoverQuestionCreate(command))
        .isInstanceOf(PostPublicationStateException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("POST_010"));

    verifyBlockedSideEffects();
  }

  @Test
  @DisplayName("managed recovery blocks active create intent before local side effects")
  void managedRecoveryBlocksActiveCreateIntentBeforeSideEffects() {
    Long ownerId = 7L;
    Long postId = 94L;
    Post post = failedQuestion(ownerId, postId);
    RecoverQuestionPostEscrowCommand command =
        new RecoverQuestionPostEscrowCommand(
            ownerId, postId, "수정 제목", null, List.of(1L), List.of("tag"));

    when(postPersistencePort.loadPostForUpdate(postId)).thenReturn(Optional.of(post));
    when(questionLifecycleExecutionPort.managesQuestionCreateLifecycle()).thenReturn(true);
    when(loadQuestionPublicationEvidencePort.loadEvidence(postId, ownerId))
        .thenReturn(new QuestionPublicationEvidence(true, false, true, false, "CREATED"));

    assertThatThrownBy(() -> service.recoverQuestionCreate(command))
        .isInstanceOf(PostPublicationStateException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("POST_008"));

    verifyBlockedSideEffects();
  }

  @Test
  @DisplayName("managed recovery ignores evidence lifecycle flag and uses lifecycle port source")
  void managedRecoveryUsesLifecyclePortEvenWhenEvidenceIsUnmanaged() {
    Long ownerId = 7L;
    Long postId = 101L;
    Post post = failedQuestion(ownerId, postId);
    RecoverQuestionPostEscrowCommand command =
        new RecoverQuestionPostEscrowCommand(ownerId, postId);

    when(postPersistencePort.loadPostForUpdate(postId)).thenReturn(Optional.of(post));
    when(questionLifecycleExecutionPort.managesQuestionCreateLifecycle()).thenReturn(true);
    when(loadQuestionPublicationEvidencePort.loadEvidence(postId, ownerId))
        .thenReturn(QuestionPublicationEvidence.unmanaged());

    assertThatThrownBy(() -> service.recoverQuestionCreate(command))
        .isInstanceOf(PostPublicationStateException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("POST_011"));

    verifyBlockedSideEffects();
  }

  @Test
  @DisplayName("managed recovery blocks missing terminal create intent before lower web3 recovery")
  void managedRecoveryBlocksMissingTerminalCreateIntentBeforeLowerRecovery() {
    Long ownerId = 7L;
    Long postId = 95L;
    Post post = failedQuestion(ownerId, postId);
    RecoverQuestionPostEscrowCommand command =
        new RecoverQuestionPostEscrowCommand(ownerId, postId);

    when(postPersistencePort.loadPostForUpdate(postId)).thenReturn(Optional.of(post));
    when(questionLifecycleExecutionPort.managesQuestionCreateLifecycle()).thenReturn(true);
    when(loadQuestionPublicationEvidencePort.loadEvidence(postId, ownerId))
        .thenReturn(new QuestionPublicationEvidence(true, false, false, false, null));

    assertThatThrownBy(() -> service.recoverQuestionCreate(command))
        .isInstanceOf(PostPublicationStateException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("POST_011"));

    verifyBlockedSideEffects();
  }

  @Test
  @DisplayName("unmanaged no-body recovery keeps compatibility without marking pending")
  void unmanagedNoBodyRecoveryKeepsCompatibilityWithoutPendingSave() {
    Long ownerId = 7L;
    Long postId = 96L;
    Post post = failedQuestion(ownerId, postId);
    RecoverQuestionPostEscrowCommand command =
        new RecoverQuestionPostEscrowCommand(ownerId, postId);

    when(postPersistencePort.loadPostForUpdate(postId)).thenReturn(Optional.of(post));
    when(questionLifecycleExecutionPort.managesQuestionCreateLifecycle()).thenReturn(false);

    service.recoverQuestionCreate(command);

    verify(postPersistencePort, never()).savePost(any(Post.class));
    verifyNoInteractions(loadQuestionPublicationEvidencePort, countAnswersPort);
    verify(questionLifecycleExecutionPort).recoverQuestionCreate(postId, ownerId, "질문 내용", 50L);
  }

  @Test
  @DisplayName("managed edit recovery with active answers stops before save and lower recovery")
  void managedEditRecoveryWithAnswersStopsBeforeSaveAndLowerRecovery() {
    Long ownerId = 7L;
    Long postId = 102L;
    Post post = failedQuestion(ownerId, postId);
    RecoverQuestionPostEscrowCommand command =
        new RecoverQuestionPostEscrowCommand(ownerId, postId, null, "수정 내용", null, null);

    when(postPersistencePort.loadPostForUpdate(postId)).thenReturn(Optional.of(post));
    when(questionLifecycleExecutionPort.managesQuestionCreateLifecycle()).thenReturn(true);
    when(loadQuestionPublicationEvidencePort.loadEvidence(postId, ownerId))
        .thenReturn(new QuestionPublicationEvidence(true, false, false, true, "EXPIRED"));
    when(countAnswersPort.countAnswers(postId)).thenReturn(1L);

    assertThatThrownBy(() -> service.recoverQuestionCreate(command))
        .isInstanceOf(PostInvalidInputException.class);

    verify(postPersistencePort, never()).savePost(any(Post.class));
    verify(questionLifecycleExecutionPort, never())
        .recoverQuestionCreate(any(), any(), any(), any());
    verifyNoInteractions(validatePostImagesPort, linkTagPort, updatePostImagesPort);
  }

  @Test
  @DisplayName("unmanaged edit recovery is unavailable before local side effects")
  void unmanagedEditRecoveryIsUnavailableBeforeSideEffects() {
    Long ownerId = 7L;
    Long postId = 97L;
    Post post = failedQuestion(ownerId, postId);
    RecoverQuestionPostEscrowCommand command =
        new RecoverQuestionPostEscrowCommand(ownerId, postId, "수정 제목", null, null, null);

    when(postPersistencePort.loadPostForUpdate(postId)).thenReturn(Optional.of(post));
    when(questionLifecycleExecutionPort.managesQuestionCreateLifecycle()).thenReturn(false);

    assertThatThrownBy(() -> service.recoverQuestionCreate(command))
        .isInstanceOf(PostPublicationStateException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("POST_011"));

    verifyBlockedSideEffects();
  }

  @Test
  @DisplayName("blocked failed question recovery stops before evidence and web3 calls")
  void blockedFailedQuestionRecoveryStopsBeforeEvidenceAndWeb3Calls() {
    Long ownerId = 7L;
    Long postId = 98L;
    Post post =
        failedQuestion(ownerId, postId).toBuilder()
            .moderationStatus(PostModerationStatus.BLOCKED)
            .build();
    RecoverQuestionPostEscrowCommand command =
        new RecoverQuestionPostEscrowCommand(ownerId, postId, "수정 제목", null, null, null);

    when(postPersistencePort.loadPostForUpdate(postId)).thenReturn(Optional.of(post));

    assertThatThrownBy(() -> service.recoverQuestionCreate(command))
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("Blocked posts");

    verifyNoInteractions(
        questionLifecycleExecutionPort,
        loadQuestionPublicationEvidencePort,
        countAnswersPort,
        validatePostImagesPort,
        linkTagPort,
        updatePostImagesPort);
    verify(postPersistencePort, never()).savePost(any(Post.class));
  }

  @Test
  @DisplayName("invalid recovery command stops before loading post")
  void invalidRecoveryCommandStopsBeforeLoadingPost() {
    RecoverQuestionPostEscrowCommand command = new RecoverQuestionPostEscrowCommand(0L, 99L);

    assertThatThrownBy(() -> service.recoverQuestionCreate(command))
        .isInstanceOf(PostInvalidInputException.class)
        .hasMessageContaining("requesterId must be positive");

    verifyNoInteractions(
        postPersistencePort,
        questionLifecycleExecutionPort,
        loadQuestionPublicationEvidencePort,
        countAnswersPort,
        validatePostImagesPort,
        linkTagPort,
        updatePostImagesPort);
  }

  private void verifyBlockedSideEffects() {
    verify(postPersistencePort, never()).savePost(any(Post.class));
    verify(questionLifecycleExecutionPort, never())
        .recoverQuestionCreate(any(), any(), any(), any());
    verifyNoInteractions(
        countAnswersPort, validatePostImagesPort, linkTagPort, updatePostImagesPort);
  }

  private Post failedQuestion(Long ownerId, Long postId) {
    LocalDateTime updatedAt = LocalDateTime.of(2026, 1, 1, 10, 0);
    return Post.builder()
        .id(postId)
        .userId(ownerId)
        .type(PostType.QUESTION)
        .title("질문 제목")
        .content("질문 내용")
        .reward(50L)
        .status(PostStatus.OPEN)
        .publicationStatus(PostPublicationStatus.FAILED)
        .createdAt(updatedAt.minusHours(1))
        .updatedAt(updatedAt)
        .build();
  }
}
