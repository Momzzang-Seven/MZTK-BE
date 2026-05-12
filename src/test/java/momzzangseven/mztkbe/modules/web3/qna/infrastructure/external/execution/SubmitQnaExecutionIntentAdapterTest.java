package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.CreateExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionDraft;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaUnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.ManageQnaAnswerExecutionIntentRefPort;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.config.QnaEscrowProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubmitQnaExecutionIntentAdapterTest {

  @Mock private CreateExecutionIntentUseCase createExecutionIntentUseCase;
  @Mock private ManageQnaAnswerExecutionIntentRefPort refPersistencePort;

  private SubmitQnaExecutionIntentAdapter adapter;
  private QnaEscrowProperties qnaEscrowProperties;

  @BeforeEach
  void setUp() {
    qnaEscrowProperties = new QnaEscrowProperties();
    qnaEscrowProperties.setQnaContractAddress("0x3333333333333333333333333333333333333333");
    qnaEscrowProperties.setSigValidityDuration(900);

    adapter =
        new SubmitQnaExecutionIntentAdapter(
            createExecutionIntentUseCase,
            new ObjectMapper(),
            refPersistencePort,
            qnaEscrowProperties);
  }

  private CreateExecutionIntentResult intentResult() {
    return new CreateExecutionIntentResult(
        ExecutionResourceType.QUESTION,
        "101",
        ExecutionResourceStatus.PENDING_EXECUTION,
        "intent-1",
        ExecutionIntentStatus.EXPIRED,
        LocalDateTime.of(2026, 4, 14, 10, 0),
        ExecutionMode.EIP7702,
        2,
        null,
        false);
  }

  private QnaExecutionDraft draft(QnaExecutionActionType actionType, Long signedAt) {
    return new QnaExecutionDraft(
        QnaExecutionResourceType.QUESTION,
        "101",
        QnaExecutionResourceStatus.PENDING_EXECUTION,
        actionType,
        7L,
        22L,
        "root-key",
        "0x" + "a".repeat(64),
        "{\"actionType\":\"" + actionType.name() + "\",\"postId\":101,\"answerId\":null}",
        List.of(
            new QnaExecutionDraftCall(
                "0x3333333333333333333333333333333333333333", BigInteger.ZERO, "0x1234")),
        true,
        "0x2222222222222222222222222222222222222222",
        1L,
        "0x4444444444444444444444444444444444444444",
        "0x" + "b".repeat(64),
        new QnaUnsignedTxSnapshot(
            11155111L,
            "0x2222222222222222222222222222222222222222",
            "0x3333333333333333333333333333333333333333",
            BigInteger.ZERO,
            "0x1234",
            1L,
            BigInteger.valueOf(180_000),
            BigInteger.valueOf(2_000_000_000L),
            BigInteger.valueOf(40_000_000_000L)),
        "0x" + "c".repeat(64),
        signedAt,
        LocalDateTime.of(2026, 4, 14, 10, 5));
  }

  @Test
  void submit_serverSigDraft_returnsSignatureMetaWithExpectedExpiry() {
    when(createExecutionIntentUseCase.execute(any(CreateExecutionIntentCommand.class)))
        .thenReturn(intentResult());

    QnaExecutionIntentResult result =
        adapter.submit(draft(QnaExecutionActionType.QNA_QUESTION_CREATE, 1_768_224_000L));

    assertThat(result.signatureMeta()).isNotNull();
    assertThat(result.signatureMeta().signedAt()).isEqualTo(1_768_224_000L);
    assertThat(result.signatureMeta().signatureExpiresAt()).isEqualTo(1_768_224_900L);
  }

  @Test
  void submit_adminDraft_yieldsNullSignatureMeta() {
    when(createExecutionIntentUseCase.execute(any(CreateExecutionIntentCommand.class)))
        .thenReturn(intentResult());

    QnaExecutionIntentResult result =
        adapter.submit(draft(QnaExecutionActionType.QNA_ADMIN_SETTLE, null));

    assertThat(result.signatureMeta()).isNull();
  }
}
