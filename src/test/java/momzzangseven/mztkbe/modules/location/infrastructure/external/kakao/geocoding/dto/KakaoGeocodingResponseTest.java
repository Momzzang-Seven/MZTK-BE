package momzzangseven.mztkbe.modules.location.infrastructure.external.kakao.geocoding.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class KakaoGeocodingResponseTest {

  @Test
  void hasDocuments_shouldReturnFalseWhenNullOrEmpty() {
    KakaoGeocodingResponse response = new KakaoGeocodingResponse();

    assertThat(response.hasDocuments()).isFalse();

    response.setDocuments(List.of());
    assertThat(response.hasDocuments()).isFalse();
  }

  @Test
  void hasDocuments_shouldReturnTrueWhenDocumentsPresent() {
    KakaoGeocodingResponse response = new KakaoGeocodingResponse();
    KakaoGeocodingResponse.Document document = new KakaoGeocodingResponse.Document();
    response.setDocuments(List.of(document));

    assertThat(response.hasDocuments()).isTrue();
  }
}
