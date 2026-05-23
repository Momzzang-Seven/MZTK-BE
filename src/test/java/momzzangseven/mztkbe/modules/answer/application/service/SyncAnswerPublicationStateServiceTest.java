package momzzangseven.mztkbe.modules.answer.application.service;

import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.answer.application.dto.SyncAnswerPublicationStateCommand;
import momzzangseven.mztkbe.modules.answer.application.port.out.SaveAnswerPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SyncAnswerPublicationStateService")
class SyncAnswerPublicationStateServiceTest {

  @Mock private SaveAnswerPort saveAnswerPort;

  @InjectMocks private SyncAnswerPublicationStateService service;

  @Test
  @DisplayName("confirmAnswerSubmitted confirms only the current create intent")
  void confirmAnswerSubmittedConfirmsCurrentCreateIntent() {
    service.confirmAnswerSubmitted(
        new SyncAnswerPublicationStateCommand(100L, "intent-create", null, null));

    verify(saveAnswerPort).confirmCreateIfCurrent(100L, "intent-create");
  }

  @Test
  @DisplayName("failAnswerSubmit fails only the current create intent")
  void failAnswerSubmitFailsCurrentCreateIntent() {
    service.failAnswerSubmit(
        new SyncAnswerPublicationStateCommand(
            100L, "intent-create", "FAILED_ONCHAIN", "RPC_UNAVAILABLE"));

    verify(saveAnswerPort)
        .markCreateFailedIfCurrent(100L, "intent-create", "FAILED_ONCHAIN", "RPC_UNAVAILABLE");
  }
}
