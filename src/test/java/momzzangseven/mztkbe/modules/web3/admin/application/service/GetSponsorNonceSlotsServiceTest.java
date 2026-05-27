package momzzangseven.mztkbe.modules.web3.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetSponsorNonceSlotsQuery;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetSponsorNonceSlotsResult;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.SponsorNonceSlotAdminView;
import momzzangseven.mztkbe.modules.web3.admin.application.port.out.LoadSponsorNonceSlotReviewPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetSponsorNonceSlotsServiceTest {

  private static final String SPONSOR = "0x" + "a".repeat(40);

  @Mock private LoadSponsorNonceSlotReviewPort loadSponsorNonceSlotReviewPort;

  private GetSponsorNonceSlotsService service;

  @BeforeEach
  void setUp() {
    service = new GetSponsorNonceSlotsService(loadSponsorNonceSlotReviewPort);
  }

  @Test
  void execute_throws_whenQueryNull() {
    assertThatThrownBy(() -> service.execute(null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("query is required");
  }

  @Test
  void execute_normalizesAddressAndDelegatesToPort() {
    SponsorNonceSlotAdminView view =
        new SponsorNonceSlotAdminView(
            84532L,
            SPONSOR,
            51L,
            "STUCK",
            1,
            100L,
            200L,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            0,
            null,
            null,
            null,
            null,
            0,
            LocalDateTime.parse("2026-05-25T12:00:00"),
            LocalDateTime.parse("2026-05-25T12:00:00"));
    when(loadSponsorNonceSlotReviewPort.loadSlots(84532L, SPONSOR, 0, 100))
        .thenReturn(List.of(view));

    GetSponsorNonceSlotsResult result =
        service.execute(
            new GetSponsorNonceSlotsQuery(9L, 84532L, SPONSOR.toUpperCase(), null, null));

    assertThat(result.fromAddress()).isEqualTo(SPONSOR);
    assertThat(result.page()).isZero();
    assertThat(result.size()).isEqualTo(100);
    assertThat(result.hasNext()).isFalse();
    assertThat(result.slots()).containsExactly(view);
    verify(loadSponsorNonceSlotReviewPort).loadSlots(84532L, SPONSOR, 0, 100);
  }
}
