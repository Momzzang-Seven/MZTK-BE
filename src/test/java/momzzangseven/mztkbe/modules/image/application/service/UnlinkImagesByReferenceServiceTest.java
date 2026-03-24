package momzzangseven.mztkbe.modules.image.application.service;

import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.image.application.dto.UnlinkImagesByReferenceCommand;
import momzzangseven.mztkbe.modules.image.application.port.out.DeleteImagePort;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UnlinkImagesByReferenceService unit test")
class UnlinkImagesByReferenceServiceTest {

  @Mock private DeleteImagePort deleteImagePort;

  @InjectMocks private UnlinkImagesByReferenceService service;

  @Test
  @DisplayName("expands virtual reference type before unlinking")
  void executeExpandsReferenceType() {
    service.execute(new UnlinkImagesByReferenceCommand(ImageReferenceType.MARKET, 10L));

    verify(deleteImagePort).unlinkImagesByReference(ImageReferenceType.MARKET.expand(), 10L);
  }
}
