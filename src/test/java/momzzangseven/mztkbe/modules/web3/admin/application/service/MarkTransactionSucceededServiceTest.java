package momzzangseven.mztkbe.modules.web3.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarkTransactionSucceededCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarkTransactionSucceededResult;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.MarkTransactionSucceededPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarkTransactionSucceededServiceTest {

  @Mock private MarkTransactionSucceededPort markTransactionSucceededPort;

  private MarkTransactionSucceededService service;

  @BeforeEach
  void setUp() {
    service = new MarkTransactionSucceededService(markTransactionSucceededPort);
  }

  @Test
  void execute_throws_whenCommandNull() {
    assertThatThrownBy(() -> service.execute(null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("command is required");
  }

  @Test
  void execute_delegatesToPort_whenCommandValid() {
    MarkTransactionSucceededCommand command =
        new MarkTransactionSucceededCommand(
            1L,
            2L,
            "0x" + "a".repeat(64),
            "https://explorer/tx/2",
            "cs override",
            "support-ticket");
    MarkTransactionSucceededResult expected =
        new MarkTransactionSucceededResult(
            2L, "SUCCEEDED", "UNCONFIRMED", "0x" + "a".repeat(64), "https://explorer/tx/2");

    when(markTransactionSucceededPort.markSucceeded(
            1L,
            2L,
            "0x" + "a".repeat(64),
            "https://explorer/tx/2",
            "cs override",
            "support-ticket"))
        .thenReturn(expected);

    MarkTransactionSucceededResult result = service.execute(command);

    assertThat(result).isEqualTo(expected);
    verify(markTransactionSucceededPort)
        .markSucceeded(
            1L,
            2L,
            "0x" + "a".repeat(64),
            "https://explorer/tx/2",
            "cs override",
            "support-ticket");
  }
}
