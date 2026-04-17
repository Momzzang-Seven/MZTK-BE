package momzzangseven.mztkbe.modules.marketplace.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.marketplace.application.dto.GetClassDetailResult;
import momzzangseven.mztkbe.modules.marketplace.application.dto.GetClassesResult;
import momzzangseven.mztkbe.modules.marketplace.application.dto.GetTrainerClassesResult;
import momzzangseven.mztkbe.modules.marketplace.application.dto.RegisterClassResult;
import momzzangseven.mztkbe.modules.marketplace.application.dto.ToggleClassStatusResult;
import momzzangseven.mztkbe.modules.marketplace.application.dto.UpdateClassResult;
import momzzangseven.mztkbe.modules.marketplace.application.port.in.GetClassDetailUseCase;
import momzzangseven.mztkbe.modules.marketplace.application.port.in.GetClassesUseCase;
import momzzangseven.mztkbe.modules.marketplace.application.port.in.GetTrainerClassesUseCase;
import momzzangseven.mztkbe.modules.marketplace.application.port.in.RegisterClassUseCase;
import momzzangseven.mztkbe.modules.marketplace.application.port.in.ToggleClassStatusUseCase;
import momzzangseven.mztkbe.modules.marketplace.application.port.in.UpdateClassUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * ClassController 통합 테스트 (MockMVC + H2 인메모리).
 *
 * <p>UseCase 를 MockitoBean 으로 교체하고 SecurityConfig 기반 인증 + Spring MVC 레이어 테스트를 수행합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("ClassController 통합 테스트 (MockMVC + H2)")
class ClassControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private RegisterClassUseCase registerClassUseCase;
  @MockitoBean private UpdateClassUseCase updateClassUseCase;
  @MockitoBean private ToggleClassStatusUseCase toggleClassStatusUseCase;
  @MockitoBean private GetClassesUseCase getClassesUseCase;
  @MockitoBean private GetClassDetailUseCase getClassDetailUseCase;
  @MockitoBean private GetTrainerClassesUseCase getTrainerClassesUseCase;
  @MockitoBean private MarkTransactionSucceededUseCase txMarkTransactionSucceededUseCase;

  // ============================================
  // Test Helpers
  // ============================================

  /** Injects an authenticated principal (trainerId as Long) with ROLE_TRAINER authority. */
  private static org.springframework.test.web.servlet.request.RequestPostProcessor trainerPrincipal(
      Long trainerId) {
    return authentication(
        new UsernamePasswordAuthenticationToken(
            trainerId,
            null,
            List.of(
                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                    "ROLE_TRAINER"))));
  }

  // ============================================
  // Test Fixtures
  // ============================================

  private static final Long CLASS_ID = 10L;

  private static Map<String, Object> validRegisterBody() {
    return Map.of(
        "title",
        "PT 60분 기초체력",
        "category",
        "PT",
        "description",
        "기초 체력 향상을 위한 PT 클래스",
        "priceAmount",
        50000,
        "durationMinutes",
        60,
        "classTimes",
        List.of(Map.of("daysOfWeek", List.of("MONDAY"), "startTime", "10:00:00", "capacity", 5)));
  }

  private static Map<String, Object> validUpdateBody() {
    return Map.of(
        "title",
        "PT 90분 고급",
        "category",
        "PT",
        "description",
        "고급 체력 향상 PT 클래스",
        "priceAmount",
        80000,
        "durationMinutes",
        90,
        "classTimes",
        List.of(Map.of("daysOfWeek", List.of("TUESDAY"), "startTime", "14:00:00", "capacity", 3)));
  }

  // ============================================
  // POST /marketplace/trainer/classes — 클래스 등록
  // ============================================

  @Nested
  @DisplayName("POST /marketplace/trainer/classes — 클래스 등록")
  class RegisterClassTests {

    @Test
    @DisplayName("[C-1] 유효한 요청으로 클래스 등록 시 201 및 classId 반환")
    void registerClass_validRequest_returns201() throws Exception {
      // given
      given(registerClassUseCase.execute(any())).willReturn(RegisterClassResult.of(CLASS_ID));

      // when & then
      mockMvc
          .perform(
              post("/marketplace/trainer/classes")
                  .with(trainerPrincipal(1L))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(validRegisterBody())))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data.classId").value(CLASS_ID));
    }

    @Test
    @DisplayName("[C-2] 인증 없이 클래스 등록 시 401 반환")
    void registerClass_withoutAuth_returns401() throws Exception {
      mockMvc
          .perform(
              post("/marketplace/trainer/classes")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(validRegisterBody())))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[C-3] title 누락 시 400 반환")
    void registerClass_missingTitle_returns400() throws Exception {
      Map<String, Object> body =
          Map.of(
              "category",
              "PT",
              "description",
              "설명",
              "priceAmount",
              50000,
              "durationMinutes",
              60,
              "classTimes",
              List.of(
                  Map.of("daysOfWeek", List.of("MONDAY"), "startTime", "10:00:00", "capacity", 5)));

      mockMvc
          .perform(
              post("/marketplace/trainer/classes")
                  .with(trainerPrincipal(1L))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(body)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("[C-4] classTimes 누락 시 400 반환")
    void registerClass_missingClassTimes_returns400() throws Exception {
      Map<String, Object> body =
          Map.of(
              "title", "PT 60분",
              "category", "PT",
              "description", "설명",
              "priceAmount", 50000,
              "durationMinutes", 60);

      mockMvc
          .perform(
              post("/marketplace/trainer/classes")
                  .with(trainerPrincipal(1L))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(body)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("[C-5] priceAmount=0 (비양수) 시 400 반환")
    void registerClass_zeroPriceAmount_returns400() throws Exception {
      var body = new java.util.LinkedHashMap<>(validRegisterBody());
      body.put("priceAmount", 0);

      mockMvc
          .perform(
              post("/marketplace/trainer/classes")
                  .with(trainerPrincipal(1L))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(body)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("[C-6] durationMinutes=0 시 400 반환")
    void registerClass_zeroDuration_returns400() throws Exception {
      var body = new java.util.LinkedHashMap<>(validRegisterBody());
      body.put("durationMinutes", 0);

      mockMvc
          .perform(
              post("/marketplace/trainer/classes")
                  .with(trainerPrincipal(1L))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(body)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("[C-7] durationMinutes=1441 (최대 초과) 시 400 반환")
    void registerClass_durationExceedsMax_returns400() throws Exception {
      var body = new java.util.LinkedHashMap<>(validRegisterBody());
      body.put("durationMinutes", 1441);

      mockMvc
          .perform(
              post("/marketplace/trainer/classes")
                  .with(trainerPrincipal(1L))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(body)))
          .andExpect(status().isBadRequest());
    }
  }

  // ============================================
  // PUT /marketplace/trainer/classes/{classId} — 클래스 수정
  // ============================================

  @Nested
  @DisplayName("PUT /marketplace/trainer/classes/{classId} — 클래스 수정")
  class UpdateClassTests {

    @Test
    @DisplayName("[C-8] 유효한 요청으로 클래스 수정 시 200 및 classId 반환")
    void updateClass_validRequest_returns200() throws Exception {
      // given
      given(updateClassUseCase.execute(any())).willReturn(UpdateClassResult.of(CLASS_ID));

      // when & then
      mockMvc
          .perform(
              put("/marketplace/trainer/classes/{classId}", CLASS_ID)
                  .with(trainerPrincipal(1L))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(validUpdateBody())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data.classId").value(CLASS_ID));
    }

    @Test
    @DisplayName("[C-9] 인증 없이 클래스 수정 시 401 반환")
    void updateClass_withoutAuth_returns401() throws Exception {
      mockMvc
          .perform(
              put("/marketplace/trainer/classes/{classId}", CLASS_ID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(validUpdateBody())))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[C-10] category 누락 시 400 반환")
    void updateClass_missingCategory_returns400() throws Exception {
      Map<String, Object> body =
          Map.of(
              "title",
              "수정",
              "description",
              "설명",
              "priceAmount",
              50000,
              "durationMinutes",
              60,
              "classTimes",
              List.of(
                  Map.of("daysOfWeek", List.of("MONDAY"), "startTime", "10:00:00", "capacity", 5)));

      mockMvc
          .perform(
              put("/marketplace/trainer/classes/{classId}", CLASS_ID)
                  .with(trainerPrincipal(1L))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(body)))
          .andExpect(status().isBadRequest());
    }
  }

  // ============================================
  // PATCH /marketplace/trainer/classes/{classId}/status — 상태 토글
  // ============================================

  @Nested
  @DisplayName("PATCH /marketplace/trainer/classes/{classId}/status — 상태 토글")
  class ToggleClassStatusTests {

    @Test
    @DisplayName("[C-11] 인증된 TRAINER가 상태를 토글하면 200 및 active 상태 반환")
    void toggleClassStatus_validRequest_returns200() throws Exception {
      // given
      given(toggleClassStatusUseCase.execute(any()))
          .willReturn(ToggleClassStatusResult.of(CLASS_ID, false));

      // when & then
      mockMvc
          .perform(
              patch("/marketplace/trainer/classes/{classId}/status", CLASS_ID)
                  .with(trainerPrincipal(1L)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data.classId").value(CLASS_ID))
          .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    @DisplayName("[C-12] 인증 없이 상태 토글 시 401 반환")
    void toggleClassStatus_withoutAuth_returns401() throws Exception {
      mockMvc
          .perform(patch("/marketplace/trainer/classes/{classId}/status", CLASS_ID))
          .andExpect(status().isUnauthorized());
    }
  }

  // ============================================
  // GET /marketplace/trainer/classes — 트레이너 클래스 목록
  // ============================================

  @Nested
  @DisplayName("GET /marketplace/trainer/classes — 트레이너 클래스 목록")
  class GetTrainerClassesTests {

    @Test
    @DisplayName("[C-13] 인증된 TRAINER가 조회하면 200 및 클래스 목록 반환")
    void getTrainerClasses_validRequest_returns200() throws Exception {
      // given
      given(getTrainerClassesUseCase.execute(any()))
          .willReturn(GetTrainerClassesResult.of(List.of(), 0, 0, 0L));

      // when & then
      mockMvc
          .perform(get("/marketplace/trainer/classes").with(trainerPrincipal(1L)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("[C-14] 인증 없이 트레이너 클래스 조회 시 401 반환")
    void getTrainerClasses_withoutAuth_returns401() throws Exception {
      mockMvc.perform(get("/marketplace/trainer/classes")).andExpect(status().isUnauthorized());
    }
  }

  // ============================================
  // GET /marketplace/classes — 공개 클래스 목록
  // ============================================

  @Nested
  @DisplayName("GET /marketplace/classes — 공개 클래스 목록 (인증 불필요)")
  class GetClassesTests {

    @Test
    @DisplayName("[C-15] 인증 없이 공개 클래스 목록 조회 시 200 반환")
    void getClasses_withoutAuth_returns200() throws Exception {
      // given
      given(getClassesUseCase.execute(any())).willReturn(GetClassesResult.of(List.of(), 0, 0, 0L));

      // when & then
      mockMvc
          .perform(get("/marketplace/classes"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("[C-16] 카테고리 필터와 정렬 함께 조회 시 200 반환")
    void getClasses_withCategoryFilter_returns200() throws Exception {
      // given
      given(getClassesUseCase.execute(any())).willReturn(GetClassesResult.of(List.of(), 0, 0, 0L));

      // when & then
      mockMvc
          .perform(get("/marketplace/classes").param("category", "PT").param("sort", "RATING"))
          .andExpect(status().isOk());
    }
  }

  // ============================================
  // GET /marketplace/classes/{classId} — 공개 클래스 상세
  // ============================================

  @Nested
  @DisplayName("GET /marketplace/classes/{classId} — 공개 클래스 상세 (인증 불필요)")
  class GetClassDetailTests {

    @Test
    @DisplayName("[C-17] 존재하는 classId 조회 시 200 및 클래스 상세 반환")
    void getClassDetail_existingClass_returns200() throws Exception {
      // given
      GetClassDetailResult result =
          new GetClassDetailResult(
              CLASS_ID,
              1L,
              new GetClassDetailResult.StoreInfo(100L, "테스트 스토어", "서울 강남구", "101호", 37.5, 127.0),
              "PT 60분 기초",
              "PT",
              "기초 체력 향상 PT 클래스",
              50000,
              null,
              List.of(),
              List.of("다이어트"),
              List.of("1:1 맞춤"),
              60,
              null,
              List.of());
      given(getClassDetailUseCase.execute(any())).willReturn(result);

      // when & then
      mockMvc
          .perform(get("/marketplace/classes/{classId}", CLASS_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data.classId").value(CLASS_ID))
          .andExpect(jsonPath("$.data.title").value("PT 60분 기초"))
          .andExpect(jsonPath("$.data.priceAmount").value(50000));
    }

    @Test
    @DisplayName("[C-18] 인증 없이 공개 클래스 상세 조회 시 200 반환 (공개 API)")
    void getClassDetail_withoutAuth_returns200() throws Exception {
      // given
      GetClassDetailResult result =
          new GetClassDetailResult(
              CLASS_ID,
              1L,
              new GetClassDetailResult.StoreInfo(null, null, null, null, null, null),
              "수정",
              "YOGA",
              "설명",
              30000,
              null,
              List.of(),
              List.of(),
              List.of(),
              45,
              null,
              List.of());
      given(getClassDetailUseCase.execute(any())).willReturn(result);

      mockMvc.perform(get("/marketplace/classes/{classId}", CLASS_ID)).andExpect(status().isOk());
    }
  }
}
