package momzzangseven.mztkbe.modules.web3.wallet.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.global.error.GlobalExceptionHandler;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RegisterWalletCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RegisterWalletResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.UnlinkWalletCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.RegisterWalletUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.UnlinkWalletUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.filter.OncePerRequestFilter;

@ExtendWith(MockitoExtension.class)
@DisplayName("WalletController 컨트롤러 계약 테스트")
class WalletControllerTest {

  private static final String AUTH_ATTR = WalletControllerTest.class.getName() + ".authentication";

  @Mock private RegisterWalletUseCase registerWalletUseCase;
  @Mock private UnlinkWalletUseCase unlinkWalletUseCase;

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();

    WalletController controller = new WalletController(registerWalletUseCase, unlinkWalletUseCase);

    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setValidator(validator)
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .addFilters(new SecurityContextInjectionFilter())
            .build();
  }

  @Test
  @DisplayName("POST /web3/wallets 성공")
  void registerWallet_success() throws Exception {
    given(registerWalletUseCase.execute(any(RegisterWalletCommand.class)))
        .willReturn(
            new RegisterWalletResult(
                1L, "0x1111111111111111111111111111111111111111", Instant.now()));

    mockMvc
        .perform(
            post("/web3/wallets")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "walletAddress", "0x1111111111111111111111111111111111111111",
                            "signature",
                                "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                            "nonce", "nonce-1"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.id").value(1))
        .andExpect(
            jsonPath("$.data.walletAddress").value("0x1111111111111111111111111111111111111111"));

    verify(registerWalletUseCase).execute(any(RegisterWalletCommand.class));
  }

  @Test
  @DisplayName("POST /web3/wallets nonce 공백이면 400")
  void registerWallet_blankNonce_returns400() throws Exception {
    mockMvc
        .perform(
            post("/web3/wallets")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "walletAddress", "0x1111111111111111111111111111111111111111",
                            "signature", "sig",
                            "nonce", " "))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(registerWalletUseCase);
  }

  @Test
  @DisplayName("POST /web3/wallets walletAddress 공백이면 400")
  void registerWallet_blankWalletAddress_returns400() throws Exception {
    mockMvc
        .perform(
            post("/web3/wallets")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "walletAddress",
                            " ",
                            "signature",
                            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                                + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                            "nonce",
                            "nonce-1"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(registerWalletUseCase);
  }

  @Test
  @DisplayName("POST /web3/wallets signature 공백이면 400")
  void registerWallet_blankSignature_returns400() throws Exception {
    mockMvc
        .perform(
            post("/web3/wallets")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "walletAddress", "0x1111111111111111111111111111111111111111",
                            "signature", " ",
                            "nonce", "nonce-1"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(registerWalletUseCase);
  }

  @Test
  @DisplayName("POST /web3/wallets walletAddress 형식 오류면 400")
  void registerWallet_invalidWalletAddressFormat_returns400() throws Exception {
    given(registerWalletUseCase.execute(any(RegisterWalletCommand.class)))
        .willThrow(new IllegalArgumentException("Invalid Ethereum address format"));

    mockMvc
        .perform(
            post("/web3/wallets")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "walletAddress",
                            "0x1234",
                            "signature",
                            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                                + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                            "nonce",
                            "nonce-1"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));
  }

  @Test
  @DisplayName("POST /web3/wallets signature 형식 오류면 400")
  void registerWallet_invalidSignatureFormat_returns400() throws Exception {
    given(registerWalletUseCase.execute(any(RegisterWalletCommand.class)))
        .willThrow(new IllegalArgumentException("Invalid signature format"));

    mockMvc
        .perform(
            post("/web3/wallets")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "walletAddress", "0x1111111111111111111111111111111111111111",
                            "signature", "0x1234",
                            "nonce", "nonce-1"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));
  }

  @Test
  @DisplayName("POST /web3/wallets principal이 null이면 401")
  void registerWallet_nullPrincipal_returns401() throws Exception {
    mockMvc
        .perform(
            post("/web3/wallets")
                .with(nullUserPrincipal())
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "walletAddress",
                            "0x1111111111111111111111111111111111111111",
                            "signature",
                            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                                + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                            "nonce",
                            "nonce-1"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value("FAIL"))
        .andExpect(jsonPath("$.code").value("AUTH_006"));

    verifyNoInteractions(registerWalletUseCase);
  }

  @Test
  @DisplayName("POST /web3/wallets 인증 없으면 401")
  void registerWallet_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(
            post("/web3/wallets")
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "walletAddress", "0x1111111111111111111111111111111111111111",
                            "signature",
                                "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                                    + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                            "nonce", "nonce-1"))))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(registerWalletUseCase);
  }

  @Test
  @DisplayName("DELETE /web3/wallets/{walletAddress} 성공")
  void unlinkWallet_success() throws Exception {
    mockMvc
        .perform(
            delete("/web3/wallets/0x1111111111111111111111111111111111111111")
                .with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"));

    verify(unlinkWalletUseCase).execute(any(UnlinkWalletCommand.class));
  }

  @Test
  @DisplayName("DELETE /web3/wallets/{walletAddress} principal이 null이면 401")
  void unlinkWallet_nullPrincipal_returns401() throws Exception {
    mockMvc
        .perform(
            delete("/web3/wallets/0x1111111111111111111111111111111111111111")
                .with(nullUserPrincipal()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value("FAIL"))
        .andExpect(jsonPath("$.code").value("AUTH_006"));

    verifyNoInteractions(unlinkWalletUseCase);
  }

  @Test
  @DisplayName("DELETE /web3/wallets/{walletAddress} 주소 형식이 잘못되면 400")
  void unlinkWallet_invalidAddress_returns400() throws Exception {
    doThrow(new IllegalArgumentException("Invalid Ethereum address format"))
        .when(unlinkWalletUseCase)
        .execute(any(UnlinkWalletCommand.class));

    mockMvc
        .perform(delete("/web3/wallets/not-an-address").with(userPrincipal(1L)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));
  }

  @Test
  @DisplayName("DELETE /web3/wallets/{walletAddress} 인증 없으면 401")
  void unlinkWallet_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(delete("/web3/wallets/0x1111111111111111111111111111111111111111"))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(unlinkWalletUseCase);
  }

  private RequestPostProcessor userPrincipal(Long userId) {
    return authentication(userId, "ROLE_USER");
  }

  private RequestPostProcessor nullUserPrincipal() {
    return authentication(null, "ROLE_USER");
  }

  private RequestPostProcessor authentication(Long userId, String... authorities) {
    return request -> {
      List<SimpleGrantedAuthority> grantedAuthorities =
          Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
      Authentication authentication =
          new UsernamePasswordAuthenticationToken(userId, null, grantedAuthorities);
      request.setAttribute(AUTH_ATTR, authentication);
      return request;
    };
  }

  private String json(Object value) throws JsonProcessingException {
    return objectMapper.writeValueAsString(value);
  }

  private static final class SecurityContextInjectionFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
      SecurityContext context = SecurityContextHolder.createEmptyContext();
      Authentication authentication = (Authentication) request.getAttribute(AUTH_ATTR);
      if (authentication != null) {
        context.setAuthentication(authentication);
      }
      SecurityContextHolder.setContext(context);
      try {
        filterChain.doFilter(request, response);
      } finally {
        SecurityContextHolder.clearContext();
      }
    }
  }
}
