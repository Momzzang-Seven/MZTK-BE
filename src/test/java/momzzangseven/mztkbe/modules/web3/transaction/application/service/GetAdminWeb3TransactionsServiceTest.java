package momzzangseven.mztkbe.modules.web3.transaction.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.GetAdminWeb3TransactionsCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.ManageTransactionRecoveryPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

@ExtendWith(MockitoExtension.class)
class GetAdminWeb3TransactionsServiceTest {

  @Mock private ManageTransactionRecoveryPort manageTransactionRecoveryPort;

  private GetAdminWeb3TransactionsService service;

  @BeforeEach
  void setUp() {
    service = new GetAdminWeb3TransactionsService(manageTransactionRecoveryPort);
  }

  @Test
  void execute_passesRawFailureReasonWithoutDefaultStatus() {
    when(manageTransactionRecoveryPort.loadPage(any())).thenReturn(Page.empty());

    service.execute(
        new GetAdminWeb3TransactionsCommand(
            9L, "RECEIPT_TIMEOUT_60S", null, null, null, null, null, null));

    ArgumentCaptor<ManageTransactionRecoveryPort.RecoveryQuery> captor =
        ArgumentCaptor.forClass(ManageTransactionRecoveryPort.RecoveryQuery.class);
    verify(manageTransactionRecoveryPort).loadPage(captor.capture());
    assertThat(captor.getValue().failureReason()).isEqualTo("RECEIPT_TIMEOUT_60S");
    assertThat(captor.getValue().status()).isNull();
    assertThat(captor.getValue().page()).isZero();
    assertThat(captor.getValue().size()).isEqualTo(50);
  }

  @Test
  void execute_parsesCompositeFilters() {
    when(manageTransactionRecoveryPort.loadPage(any())).thenReturn(Page.empty());

    service.execute(
        new GetAdminWeb3TransactionsCommand(
            9L,
            "KMS_DESCRIBE_TERMINAL",
            "CREATED",
            "LEVEL_UP_REWARD",
            "reward-1",
            "EIP1559",
            1,
            20));

    ArgumentCaptor<ManageTransactionRecoveryPort.RecoveryQuery> captor =
        ArgumentCaptor.forClass(ManageTransactionRecoveryPort.RecoveryQuery.class);
    verify(manageTransactionRecoveryPort).loadPage(captor.capture());
    assertThat(captor.getValue().status()).isEqualTo(Web3TxStatus.CREATED);
    assertThat(captor.getValue().failureReason()).isEqualTo("KMS_DESCRIBE_TERMINAL");
    assertThat(captor.getValue().referenceType()).isEqualTo(Web3ReferenceType.LEVEL_UP_REWARD);
    assertThat(captor.getValue().referenceId()).isEqualTo("reward-1");
    assertThat(captor.getValue().txType()).isEqualTo(Web3TxType.EIP1559);
    assertThat(captor.getValue().page()).isEqualTo(1);
    assertThat(captor.getValue().size()).isEqualTo(20);
  }

  @Test
  void execute_rejectsInvalidEnumFilter() {
    assertThatThrownBy(
            () ->
                service.execute(
                    new GetAdminWeb3TransactionsCommand(
                        9L, null, "created", null, null, null, null, null)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("status is invalid");

    verifyNoInteractions(manageTransactionRecoveryPort);
  }
}
