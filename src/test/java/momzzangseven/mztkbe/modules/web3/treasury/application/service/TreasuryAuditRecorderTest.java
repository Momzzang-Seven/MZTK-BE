package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.RecordTreasuryAuditUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.RecordTreasuryProvisionAuditPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.RecordTreasuryProvisionAuditPort.AuditCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unit tests for {@link TreasuryAuditRecorder} — covers [M-95].
 *
 * <p>Verifies (a) port delegation with the supplied fields (including the alias-level {@code
 * walletAlias}), (b) port-thrown exceptions are swallowed so the original business error is not
 * masked, and (c) the {@code record} method carries {@code @Transactional(propagation =
 * REQUIRES_NEW)} (covered by reflection — Spring AOP behaviour is exercised in the persistence
 * integration test).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TreasuryAuditRecorder 단위 테스트")
class TreasuryAuditRecorderTest {

  private static final String ALIAS = "reward-treasury";

  @Mock private RecordTreasuryProvisionAuditPort port;

  @InjectMocks private TreasuryAuditRecorder recorder;

  @Nested
  @DisplayName("A. 포트 위임")
  class PortDelegation {

    @Test
    @DisplayName("[M-95a] record — AuditCommand 필드를 그대로 포트에 전달")
    void record_delegatesToPort_withMappedFields() {
      recorder.record(7L, ALIAS, "0x" + "a".repeat(40), true, null);

      ArgumentCaptor<AuditCommand> captor = ArgumentCaptor.forClass(AuditCommand.class);
      verify(port).record(captor.capture());

      AuditCommand cmd = captor.getValue();
      assertThat(cmd.operatorId()).isEqualTo(7L);
      assertThat(cmd.walletAlias()).isEqualTo(ALIAS);
      assertThat(cmd.treasuryAddress()).isEqualTo("0x" + "a".repeat(40));
      assertThat(cmd.success()).isTrue();
      assertThat(cmd.failureReason()).isNull();
    }

    @Test
    @DisplayName("[M-95b] record — failure 분기는 failureReason과 함께 전달")
    void record_failureBranch_propagatesFailureReason() {
      recorder.record(7L, ALIAS, null, false, "RuntimeException");

      ArgumentCaptor<AuditCommand> captor = ArgumentCaptor.forClass(AuditCommand.class);
      verify(port).record(captor.capture());

      AuditCommand cmd = captor.getValue();
      assertThat(cmd.success()).isFalse();
      assertThat(cmd.failureReason()).isEqualTo("RuntimeException");
      assertThat(cmd.walletAlias()).isEqualTo(ALIAS);
      assertThat(cmd.treasuryAddress()).isNull();
    }

    @Test
    @DisplayName("[M-95e] record — walletAlias 가 null 이어도 그대로 포트에 전달")
    void record_allowsNullWalletAlias() {
      recorder.record(7L, null, null, false, "ADDRESS_MISMATCH");

      ArgumentCaptor<AuditCommand> captor = ArgumentCaptor.forClass(AuditCommand.class);
      verify(port).record(captor.capture());

      assertThat(captor.getValue().walletAlias()).isNull();
    }
  }

  @Nested
  @DisplayName("B. 예외 흡수")
  class ExceptionSwallowing {

    @Test
    @DisplayName("[M-95c] record — 포트 예외는 흡수되어 호출자에게 전파되지 않음")
    void record_swallowsPortException() {
      doThrow(new RuntimeException("DB down")).when(port).record(any());

      assertThatCode(() -> recorder.record(7L, ALIAS, null, false, "AwsServiceException"))
          .doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("C. @Transactional(REQUIRES_NEW) 어노테이션 검증")
  class TransactionPropagation {

    @Test
    @DisplayName("[M-95d] record 메서드는 Propagation.REQUIRES_NEW 보유")
    void record_methodIsAnnotatedRequiresNew() throws NoSuchMethodException {
      Method m =
          TreasuryAuditRecorder.class.getMethod(
              "record", Long.class, String.class, String.class, boolean.class, String.class);

      Transactional tx = m.getAnnotation(Transactional.class);
      assertThat(tx).isNotNull();
      assertThat(tx.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
  }

  @Nested
  @DisplayName("D. 입력 포트 구현")
  class InputPortImplementation {

    @Test
    @DisplayName("[M-55] TreasuryAuditRecorder 는 RecordTreasuryAuditUseCase 를 구현")
    void implementsRecordTreasuryAuditUseCase() {
      assertThat(RecordTreasuryAuditUseCase.class.isAssignableFrom(TreasuryAuditRecorder.class))
          .isTrue();
    }
  }
}
