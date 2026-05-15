package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.LockModeType;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

class WalletRegistrationSessionJpaRepositoryContractTest {

  @Test
  void findByPublicIdForUpdate_declaresPessimisticWriteLock() throws NoSuchMethodException {
    Method method =
        WalletRegistrationSessionJpaRepository.class.getMethod(
            "findByPublicIdForUpdate", String.class);

    Lock lock = method.getAnnotation(Lock.class);

    assertThat(lock).isNotNull();
    assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
  }
}
