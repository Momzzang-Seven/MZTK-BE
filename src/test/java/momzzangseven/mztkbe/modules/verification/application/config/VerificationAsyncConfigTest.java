package momzzangseven.mztkbe.modules.verification.application.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class VerificationAsyncConfigTest {

  @Test
  void createsExecutorWithVerificationSpecificSettings() {
    VerificationAsyncConfig config = new VerificationAsyncConfig();

    ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.verificationTaskExecutor();

    assertThat(executor.getThreadNamePrefix()).isEqualTo("verification-");
    assertThat(executor.getCorePoolSize()).isEqualTo(2);
    assertThat(executor.getMaxPoolSize()).isEqualTo(4);
    assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(100);
  }
}
