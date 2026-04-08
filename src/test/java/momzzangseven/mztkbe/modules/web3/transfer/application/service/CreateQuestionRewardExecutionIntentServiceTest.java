package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.RegisterQuestionRewardIntentCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferExecutionDraft;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferSignRequestBundle;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferUnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.BuildQuestionRewardExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.SubmitExecutionDraftPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateQuestionRewardExecutionIntentServiceTest {

  @Mock private BuildQuestionRewardExecutionDraftPort buildQuestionRewardExecutionDraftPort;
  @Mock private SubmitExecutionDraftPort submitExecutionDraftPort;

  private CreateQuestionRewardExecutionIntentService service;

  @BeforeEach
  void setUp() {
    service =
        new CreateQuestionRewardExecutionIntentService(
            buildQuestionRewardExecutionDraftPort, submitExecutionDraftPort);
  }

  @Test
  void execute_buildsDraftAndDelegates() {
    RegisterQuestionRewardIntentCommand command =
        new RegisterQuestionRewardIntentCommand(101L, 201L, 7L, 22L, BigInteger.TEN);
    TransferExecutionDraft draft =
        new TransferExecutionDraft(
            "QUESTION",
            "101",
            "PENDING_EXECUTION",
            "QNA_ANSWER_ACCEPT",
            7L,
            22L,
            "domain:QUESTION_REWARD:101:7",
            "0xhash",
            "{}",
            List.of(
                new TransferExecutionDraftCall("0x" + "1".repeat(40), BigInteger.ZERO, "0x1234")),
            true,
            "0x" + "2".repeat(40),
            3L,
            "0x" + "3".repeat(40),
            "0x" + "4".repeat(64),
            new TransferUnsignedTxSnapshot(
                11155111L,
                "0x" + "2".repeat(40),
                "0x" + "1".repeat(40),
                BigInteger.ZERO,
                "0x1234",
                5L,
                BigInteger.valueOf(80_000),
                BigInteger.valueOf(2_000_000_000L),
                BigInteger.valueOf(50_000_000_000L)),
            "0x" + "5".repeat(64),
            LocalDateTime.now().plusMinutes(5));
    TransferExecutionIntentResult expected =
        new TransferExecutionIntentResult(
            "QUESTION",
            "101",
            "PENDING_EXECUTION",
            "intent-1",
            "AWAITING_SIGNATURE",
            LocalDateTime.now().plusMinutes(5),
            "EIP7702",
            2,
            TransferSignRequestBundle.forEip7702(
                new TransferSignRequestBundle.AuthorizationSignRequest(
                    11155111L, "0x" + "3".repeat(40), 3L, "0x" + "4".repeat(64)),
                new TransferSignRequestBundle.SubmitSignRequest(
                    "0x" + "5".repeat(64),
                    LocalDateTime.now().plusMinutes(5).toEpochSecond(ZoneOffset.UTC))),
            false,
            null,
            null,
            null);

    when(buildQuestionRewardExecutionDraftPort.build(command)).thenReturn(draft);
    when(submitExecutionDraftPort.submit(draft)).thenReturn(expected);

    TransferExecutionIntentResult result = service.execute(command);

    assertThat(result.executionIntentId()).isEqualTo("intent-1");
    verify(buildQuestionRewardExecutionDraftPort).build(command);
    verify(submitExecutionDraftPort).submit(draft);
  }
}
