package momzzangseven.mztkbe.modules.location.infrastructure.external.kakao.geocoding.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class KakaoReverseGeocodingResponseTest {

  @Test
  void hasDocuments_shouldReturnFalseWhenNullOrEmpty() {
    KakaoReverseGeocodingResponse response = new KakaoReverseGeocodingResponse();

    assertThat(response.hasDocuments()).isFalse();

    response.setDocuments(List.of());
    assertThat(response.hasDocuments()).isFalse();
  }

  @Test
  void hasDocuments_shouldReturnTrueWhenDocumentsPresent() {
    KakaoReverseGeocodingResponse response = new KakaoReverseGeocodingResponse();
    KakaoReverseGeocodingResponse.Document document = new KakaoReverseGeocodingResponse.Document();
    response.setDocuments(List.of(document));

    assertThat(response.hasDocuments()).isTrue();
  }
}
