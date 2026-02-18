package momzzangseven.mztkbe.modules.location.infrastructure.external.kakao.geocoding.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

/**
 * Kakao Geocoding API response DTO (address -> GPS coordinates) API:
 * https://dapi.kakao.com/v2/local/search/address.json
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KakaoGeocodingResponse {
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

    @JsonProperty("x") // 경도
    private String x;

    @JsonProperty("y") // 위도
    private String y;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Address {
    /*전체 지번 주소*/
    @JsonProperty("address_name")
    private String addressName;

    /*법정 코드*/
    @JsonProperty("b_code")
    private String bCode;

    /*행정 코드*/
    @JsonProperty("h_code")
    private String hCode;

    /*지번 주번지*/
    @JsonProperty("main_address_no")
    private String mainAddressNo;

    /*산 여부, Y 또는 N*/
    @JsonProperty("mountain_yn")
    private String mountainYn;

    /*지역 1 Depth, 시도 단위*/
    @JsonProperty("region_1depth_name")
    private String region1depthName;

    /*지역 2 Depth, 구 단위*/
    @JsonProperty("region_2depth_name")
    private String region2depthName;

    /*지역 3 Depth, 동 단위*/
    @JsonProperty("region_3depth_name")
    private String region3depthName;

    /*지번 부번지, 없을 경우 빈 문자열("") 반환*/
    @JsonProperty("sub_address_no")
    private String subAddressNo;

    /*X 좌표값, 경위도인 경우 경도(longitude)*/
    @JsonProperty("x")
    private String x;

    /*Y 좌표값, 경위도인 경우 위도(latitude)*/
    @JsonProperty("y")
    private String y;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class RoadAddress {
    /*전체 도로명 주소*/
    @JsonProperty("address_name")
    private String addressName;

    /*건물 이름*/
    @JsonProperty("building_name")
    private String buildingName;

    /*건물 본번*/
    @JsonProperty("main_building_no")
    private String mainBuildingNo;

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

    /*건물 부번, 없을 경우 빈 문자열("") 반환*/
    @JsonProperty("sub_building_no")
    private String subBuildingNo;

    /*지하 여부, Y 또는 N*/
    @JsonProperty("underground_yn")
    private String undergroundYn;

    /*X 좌표값, 경위도인 경우 경도(longitude)*/
    @JsonProperty("x")
    private String x;

    /*Y 좌표값, 경위도인 경우 위도(latitude)*/
    @JsonProperty("y")
    private String y;

    /*우편번호(5자리)*/
    @JsonProperty("zone_no")
    private String zoneNo;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Meta {
    @JsonProperty("total_count")
    private Integer totalCount;

    @JsonProperty("pageable_count")
    private Integer pageableCount;

    @JsonProperty("is_end")
    private Boolean isEnd;
  }

  public boolean hasDocuments() {
    if (documents == null || documents.isEmpty()) return false;
    return true;
  }
}
