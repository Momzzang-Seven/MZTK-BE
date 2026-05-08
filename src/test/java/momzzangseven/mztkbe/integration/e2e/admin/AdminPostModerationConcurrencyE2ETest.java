package momzzangseven.mztkbe.integration.e2e.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.post.application.dto.ModeratePostCommand;
import momzzangseven.mztkbe.modules.post.application.dto.ModeratePostResult;
import momzzangseven.mztkbe.modules.post.application.port.in.ModerateManagedPostUseCase;
import momzzangseven.mztkbe.modules.post.domain.model.PostModerationStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.PostEntity;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository.PostJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/** Verifies admin post moderation pessimistic locks against real PostgreSQL row contention. */
@DisplayName("[E2E] Admin post moderation DB lock concurrency")
class AdminPostModerationConcurrencyE2ETest extends E2ETestBase {

  private static final Duration WORKER_TIMEOUT = Duration.ofSeconds(10);

  @Autowired private ModerateManagedPostUseCase moderateManagedPostUseCase;
  @Autowired private PostJpaRepository postJpaRepository;
  @Autowired private TransactionTemplate txTemplate;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;

  @Test
  @DisplayName("[LOCK-1] 같은 post 동시 ban 2건은 직렬화되어 1건만 상태를 변경한다")
  void concurrentBlockSamePost_serializesAndOnlyOneChangesState() throws Exception {
    Long userId = signupAndLogin("Mod" + shortToken()).userId();
    Long postId = createFreePost(userId);

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      CyclicBarrier barrier = new CyclicBarrier(2);
      Future<ModeratePostResult> first = executor.submit(blockTask(101L, postId, barrier));
      Future<ModeratePostResult> second = executor.submit(blockTask(102L, postId, barrier));

      List<ModeratePostResult> results = List.of(await(first), await(second));

      assertThat(results)
          .extracting(ModeratePostResult::moderationStatus)
          .containsOnly(PostModerationStatus.BLOCKED);
      assertThat(results)
          .extracting(ModeratePostResult::moderated)
          .containsExactlyInAnyOrder(true, false);
      assertThat(loadModerationStatus(postId)).isEqualTo(PostModerationStatus.BLOCKED);
    } finally {
      shutdown(executor);
    }
  }

  @Test
  @DisplayName("[LOCK-2] 같은 post 동시 ban/unblock은 직렬화되어 일관된 최종 상태로 수렴한다")
  void concurrentBlockAndUnblockSamePost_serializesAndKeepsConsistentState() throws Exception {
    Long userId = signupAndLogin("Mod" + shortToken()).userId();
    Long postId = createFreePost(userId);

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      CyclicBarrier barrier = new CyclicBarrier(2);
      Future<ModeratePostResult> block = executor.submit(blockTask(201L, postId, barrier));
      Future<ModeratePostResult> unblock = executor.submit(unblockTask(202L, postId, barrier));

      List<ModeratePostResult> results = List.of(await(block), await(unblock));
      PostModerationStatus finalStatus = loadModerationStatus(postId);

      assertThat(results)
          .extracting(ModeratePostResult::moderationStatus)
          .allMatch(
              status ->
                  Set.of(PostModerationStatus.NORMAL, PostModerationStatus.BLOCKED)
                      .contains(status));
      assertThat(results).extracting(ModeratePostResult::moderated).contains(true);
      assertThat(finalStatus).isIn(PostModerationStatus.NORMAL, PostModerationStatus.BLOCKED);
    } finally {
      shutdown(executor);
    }
  }

  private Callable<ModeratePostResult> blockTask(
      Long operatorId, Long postId, CyclicBarrier barrier) {
    return () -> {
      barrier.await(5, TimeUnit.SECONDS);
      return moderateManagedPostUseCase.blockManagedPost(
          new ModeratePostCommand(operatorId, postId));
    };
  }

  private Callable<ModeratePostResult> unblockTask(
      Long operatorId, Long postId, CyclicBarrier barrier) {
    return () -> {
      barrier.await(5, TimeUnit.SECONDS);
      return moderateManagedPostUseCase.unblockManagedPost(
          new ModeratePostCommand(operatorId, postId));
    };
  }

  private ModeratePostResult await(Future<ModeratePostResult> future) throws Exception {
    return future.get(WORKER_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
  }

  private Long createFreePost(Long userId) {
    return txTemplate.execute(
        status ->
            postJpaRepository
                .save(
                    PostEntity.builder()
                        .userId(userId)
                        .type(PostType.FREE)
                        .title(null)
                        .content("moderation concurrency test")
                        .reward(0L)
                        .status(PostStatus.OPEN)
                        .build())
                .getId());
  }

  private PostModerationStatus loadModerationStatus(Long postId) {
    return txTemplate.execute(
        status -> postJpaRepository.findById(postId).orElseThrow().getModerationStatus());
  }

  private void shutdown(ExecutorService executor) throws InterruptedException {
    executor.shutdown();
    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
      executor.shutdownNow();
    }
    assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
  }

  private String shortToken() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 6);
  }
}
