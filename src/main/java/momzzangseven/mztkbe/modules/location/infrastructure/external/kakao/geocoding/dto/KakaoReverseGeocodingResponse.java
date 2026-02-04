package momzzangseven.mztkbe.modules.location.infrastructure.external.kakao.geocoding.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

/**
 * Kakao Reverse Geocoding API Response DTO (GPS coordinates → Address) API:
 * https://dapi.kakao.com/v2/local/geo/coord2address.json
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KakaoReverseGeocodingResponse {
  @JsonProperty("documents")
  private List<Document> documents;

  @JsonProperty("meta")
  private Meta meta;

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Document {
    @JsonProperty("address")
    private Address address;

    @JsonProperty("road_address")
    private RoadAddress roadAddress;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Address {
    /*전체 지번 주소*/
    @JsonProperty("address_name")
    private String addressName;

    /*지역 1 Depth, 시도 단위*/
    @JsonProperty("region_1depth_name")
    private String region1depthName;

    /*지역 2 Depth, 구 단위*/
    @JsonProperty("region_2depth_name")
    private String region2depthName;

    /*지역 3 Depth, 동 단위*/
    @JsonProperty("region_3depth_name")
    private String region3depthName;

    /*산 여부, Y 또는 N*/
    @JsonProperty("mountain_yn")
    private String mountainYn;

    /*지번 주 번지*/
    @JsonProperty("main_address_no")
    private String mainAddressNo;

    /*지번 부번지, 없을 경우 빈 문자열("") 반환*/
    @JsonProperty("sub_address_no")
    private String subAddressNo;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class RoadAddress {
    /*전체 도로명 주소*/
    @JsonProperty("address_name")
    private String addressName;

    /*지역명1*/
    @JsonProperty("region_1depth_name")
    private String region1depthName;

    /*지역명2*/
    @JsonProperty("region_2depth_name")
    private String region2depthName;

    /*지역명3*/
    @JsonProperty("region_3depth_name")
    private String region3depthName;

    /*도로명*/
    @JsonProperty("road_name")
    private String roadName;

    /*지하 여부, Y 또는 N*/
    @JsonProperty("underground_yn")
    private String undergroundYn;

    /*건물 본번*/
    @JsonProperty("main_building_no")
    private String mainBuildingNo;

    /*건물 부번, 없을 경우 빈 문자열("") 반환*/
    @JsonProperty("sub_building_no")
    private String subBuildingNo;

    /*건물 이름*/
    @JsonProperty("building_name")
    private String buildingName;

    /*우편번호(5자리)*/
    @JsonProperty("zone_no")
    private String zoneNo;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Meta {
    @JsonProperty("total_count")
    private Integer totalCount;
  }

  public boolean hasDocuments() {
    if (getDocuments() == null || getDocuments().isEmpty()) return false;
    return true;
  }
}
