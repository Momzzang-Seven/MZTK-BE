package momzzangseven.mztkbe.modules.location.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.location.application.dto.DeleteLocationCommand;
import momzzangseven.mztkbe.modules.location.application.dto.DeleteLocationResult;
import momzzangseven.mztkbe.modules.location.application.dto.GetMyLocationsResult;
import momzzangseven.mztkbe.modules.location.application.dto.LocationItem;
import momzzangseven.mztkbe.modules.location.application.dto.RegisterLocationCommand;
import momzzangseven.mztkbe.modules.location.application.dto.RegisterLocationResult;
import momzzangseven.mztkbe.modules.location.application.dto.VerifyLocationCommand;
import momzzangseven.mztkbe.modules.location.application.dto.VerifyLocationResult;
import momzzangseven.mztkbe.modules.location.application.port.in.DeleteLocationUseCase;
import momzzangseven.mztkbe.modules.location.application.port.in.GetMyLocationsUseCase;
import momzzangseven.mztkbe.modules.location.application.port.in.RegisterLocationUseCase;
import momzzangseven.mztkbe.modules.location.application.port.in.VerifyLocationUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

@DisplayName("LocationController 컨트롤러 계약 테스트 (MockMvc + H2)")
@org.springframework.boot.test.context.SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class LocationControllerTest {

  @org.springframework.beans.factory.annotation.Autowired
  protected org.springframework.test.web.servlet.MockMvc mockMvc;

  @org.springframework.beans.factory.annotation.Autowired
  protected com.fasterxml.jackson.databind.ObjectMapper objectMapper;

  @org.springframework.boot.test.mock.mockito.MockBean
  private momzzangseven.mztkbe.modules.web3.transaction.application.port.in
          .MarkTransactionSucceededUseCase
      txMarkTransactionSucceededUseCase;

  @org.springframework.boot.test.mock.mockito.MockBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionReceiptWorker
      txTransactionReceiptWorker;

  @org.springframework.boot.test.mock.mockito.MockBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionIssuerWorker
      txTransactionIssuerWorker;

  @org.springframework.boot.test.mock.mockito.MockBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .SignedRecoveryWorker
      txSignedRecoveryWorker;

  @MockBean private RegisterLocationUseCase registerLocationUseCase;
  @MockBean private VerifyLocationUseCase verifyLocationUseCase;
  @MockBean private DeleteLocationUseCase deleteLocationUseCase;
  @MockBean private GetMyLocationsUseCase getMyLocationsUseCase;

  @Test
  @DisplayName("GET /users/me/locations 성공")
  void getMyLocations_success() throws Exception {
    LocationItem item =
        LocationItem.builder()
            .locationId(10L)
            .locationName("헬스장")
            .postalCode("12345")
            .address("서울시")
            .detailAddress("101호")
            .latitude(37.55)
            .longitude(126.98)
            .registeredAt(Instant.parse("2026-03-01T00:00:00Z"))
            .build();
    given(getMyLocationsUseCase.execute(1L)).willReturn(GetMyLocationsResult.from(List.of(item)));

    mockMvc
        .perform(get("/users/me/locations").with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.totalCount").value(1))
        .andExpect(jsonPath("$.data.locations[0].locationId").value(10));
  }

  @Test
  @DisplayName("GET /users/me/locations 인증 없으면 401")
  void getMyLocations_unauthenticated_returns401() throws Exception {
    mockMvc.perform(get("/users/me/locations")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("GET /users/me/locations principal이 null이면 401")
  void getMyLocations_nullPrincipal_returns401() throws Exception {
    mockMvc
        .perform(get("/users/me/locations").with(nullUserPrincipal()))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(getMyLocationsUseCase);
  }

  @Test
  @DisplayName("POST /users/me/locations/register 성공")
  void registerLocation_success() throws Exception {
    given(registerLocationUseCase.execute(any(RegisterLocationCommand.class)))
        .willReturn(
            RegisterLocationResult.builder()
                .locationId(99L)
                .userId(1L)
                .locationName("집")
                .postalCode("04524")
                .address("서울시 중구 세종대로 110")
                .detailAddress("11층")
                .latitude(37.5665)
                .longitude(126.9780)
                .registeredAt(Instant.parse("2026-03-01T01:00:00Z"))
                .build());

    mockMvc
        .perform(
            post("/users/me/locations/register")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "locationName", "집",
                            "postalCode", "04524",
                            "address", "서울시 중구 세종대로 110",
                            "detailAddress", "11층"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.locationId").value(99))
        .andExpect(jsonPath("$.data.locationName").value("집"));
  }

  @Test
  @DisplayName("POST /users/me/locations/register 인증 없으면 401")
  void registerLocation_unauthenticated_returns401() throws Exception {
    mockMvc.perform(post("/users/me/locations/register")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("POST /users/me/locations/register 인증 principal이 null이면 401")
  void registerLocation_nullPrincipal_returns401() throws Exception {
    mockMvc
        .perform(
            post("/users/me/locations/register")
                .with(nullUserPrincipal())
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("locationName", "집"))))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(registerLocationUseCase);
  }

  @Test
  @DisplayName("POST /users/me/locations/register 위치 이름 공백이면 400")
  void registerLocation_blankName_returns400() throws Exception {
    mockMvc
        .perform(
            post("/users/me/locations/register")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("locationName", "   "))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(registerLocationUseCase);
  }

  @Test
  @DisplayName("POST /users/me/locations/register 위치 이름이 100자 초과면 400")
  void registerLocation_nameTooLong_returns400() throws Exception {
    mockMvc
        .perform(
            post("/users/me/locations/register")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("locationName", "a".repeat(101)))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(registerLocationUseCase);
  }

  @Test
  @DisplayName("POST /users/me/locations/register postalCode가 10자 초과면 400")
  void registerLocation_postalCodeTooLong_returns400() throws Exception {
    mockMvc
        .perform(
            post("/users/me/locations/register")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("locationName", "집", "postalCode", "1".repeat(11)))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(registerLocationUseCase);
  }

  @Test
  @DisplayName("POST /users/me/locations/register address가 255자 초과면 400")
  void registerLocation_addressTooLong_returns400() throws Exception {
    mockMvc
        .perform(
            post("/users/me/locations/register")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("locationName", "집", "address", "a".repeat(256)))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(registerLocationUseCase);
  }

  @Test
  @DisplayName("POST /users/me/locations/register detailAddress가 255자 초과면 400")
  void registerLocation_detailAddressTooLong_returns400() throws Exception {
    mockMvc
        .perform(
            post("/users/me/locations/register")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("locationName", "집", "detailAddress", "a".repeat(256)))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(registerLocationUseCase);
  }

  @Test
  @DisplayName("POST /locations/verify 성공")
  void verifyLocation_success() throws Exception {
    given(verifyLocationUseCase.execute(any(VerifyLocationCommand.class)))
        .willReturn(
            new VerifyLocationResult(
                101L,
                1L,
                "집",
                1L,
                true,
                2.0,
                37.5665,
                126.9780,
                37.5666,
                126.9781,
                Instant.parse("2026-03-01T03:00:00Z"),
                true,
                10,
                "XP granted success"));

    mockMvc
        .perform(
            post("/locations/verify")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "locationId", 1,
                            "currentLatitude", 37.5666,
                            "currentLongitude", 126.9781))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.locationId").value(1))
        .andExpect(jsonPath("$.data.xpGranted").value(true));
  }

  @Test
  @DisplayName("POST /locations/verify 필수 값 누락이면 400")
  void verifyLocation_missingLocationId_returns400() throws Exception {
    mockMvc
        .perform(
            post("/locations/verify")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("currentLatitude", 37.5, "currentLongitude", 126.9))))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(verifyLocationUseCase);
  }

  @Test
  @DisplayName("POST /locations/verify currentLatitude 누락이면 400")
  void verifyLocation_missingLatitude_returns400() throws Exception {
    mockMvc
        .perform(
            post("/locations/verify")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("locationId", 1, "currentLongitude", 126.9))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(verifyLocationUseCase);
  }

  @Test
  @DisplayName("POST /locations/verify currentLongitude 누락이면 400")
  void verifyLocation_missingLongitude_returns400() throws Exception {
    mockMvc
        .perform(
            post("/locations/verify")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("locationId", 1, "currentLatitude", 37.5))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(verifyLocationUseCase);
  }

  @Test
  @DisplayName("POST /locations/verify 인증 없으면 401")
  void verifyLocation_unauthenticated_returns401() throws Exception {
    mockMvc.perform(post("/locations/verify")).andExpect(status().isUnauthorized());

    verifyNoInteractions(verifyLocationUseCase);
  }

  @Test
  @DisplayName("POST /locations/verify principal이 null이면 401")
  void verifyLocation_nullPrincipal_returns401() throws Exception {
    mockMvc
        .perform(
            post("/locations/verify")
                .with(nullUserPrincipal())
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "locationId", 1,
                            "currentLatitude", 37.5666,
                            "currentLongitude", 126.9781))))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(verifyLocationUseCase);
  }

  @Test
  @DisplayName("DELETE /users/me/locations/{locationId} 성공")
  void deleteLocation_success() throws Exception {
    given(deleteLocationUseCase.execute(any(DeleteLocationCommand.class)))
        .willReturn(
            DeleteLocationResult.builder()
                .locationId(1L)
                .locationName("집")
                .deletedAt(Instant.parse("2026-03-01T02:00:00Z"))
                .build());

    mockMvc
        .perform(delete("/users/me/locations/1").with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.locationId").value(1))
        .andExpect(jsonPath("$.data.locationName").value("집"));
  }

  @Test
  @DisplayName("DELETE /users/me/locations/{locationId} 인증 없으면 401")
  void deleteLocation_unauthenticated_returns401() throws Exception {
    mockMvc.perform(delete("/users/me/locations/1")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("DELETE /users/me/locations/{locationId} principal이 null이면 401")
  void deleteLocation_nullPrincipal_returns401() throws Exception {
    mockMvc
        .perform(delete("/users/me/locations/1").with(nullUserPrincipal()))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(deleteLocationUseCase);
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor userPrincipal(
      Long userId) {
    return authenticatedPrincipal(userId, "ROLE_USER");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor adminPrincipal(
      Long userId) {
    return authenticatedPrincipal(userId, "ROLE_ADMIN");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor stepUpPrincipal(
      Long userId) {
    return authenticatedPrincipal(userId, "ROLE_USER", "ROLE_STEP_UP");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor nullUserPrincipal() {
    return nullPrincipalWithRoles("ROLE_USER");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor nullAdminPrincipal() {
    return nullPrincipalWithRoles("ROLE_ADMIN");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor nullStepUpPrincipal() {
    return nullPrincipalWithRoles("ROLE_USER", "ROLE_STEP_UP");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor nullPrincipalWithRoles(
      String... authorities) {
    java.util.List<org.springframework.security.core.authority.SimpleGrantedAuthority>
        grantedAuthorities =
            java.util.Arrays.stream(authorities)
                .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                .toList();
    org.springframework.security.authentication.UsernamePasswordAuthenticationToken token =
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            null, null, grantedAuthorities);
    org.springframework.security.core.context.SecurityContext context =
        org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
    context.setAuthentication(token);
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.securityContext(context);
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor authenticatedPrincipal(
      Long userId, String... authorities) {
    java.util.Objects.requireNonNull(userId, "userId");
    java.util.List<org.springframework.security.core.authority.SimpleGrantedAuthority>
        grantedAuthorities =
            java.util.Arrays.stream(authorities)
                .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                .toList();
    org.springframework.security.authentication.UsernamePasswordAuthenticationToken token =
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            userId, null, grantedAuthorities);
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.authentication(token);
  }

  private String json(Object value) throws com.fasterxml.jackson.core.JsonProcessingException {
    return objectMapper.writeValueAsString(value);
  }
}
