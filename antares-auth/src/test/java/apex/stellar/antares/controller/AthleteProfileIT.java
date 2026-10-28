package apex.stellar.antares.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import apex.stellar.antares.config.BaseIntegrationTest;
import apex.stellar.antares.dto.AthleteProfileRequest;
import apex.stellar.antares.dto.BodyWeightRequest;
import apex.stellar.antares.model.Division;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/** Athlete profile: division (shared with Aldebaran), private birth year, height and weights. */
class AthleteProfileIT extends BaseIntegrationTest {

  private static final String PASSWORD = "password123";

  private Cookie[] cookies;

  @BeforeEach
  void setUp() throws Exception {
    userRepository.deleteAll(
        userRepository.findAll().stream()
            .filter(u -> !u.getPlatformRole().name().equals("ADMIN"))
            .toList());
    cookies = registerAndLogin("athlete.profile@example.com", PASSWORD);
  }

  @Test
  @DisplayName("profile: division, birth year and height are saved, the division reaches Aldebaran")
  void updateProfile() throws Exception {
    // No division yet: empty header
    mockMvc
        .perform(get("/antares/auth/verify/api").cookie(cookies))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Auth-User-Division", ""));

    putProfile(new AthleteProfileRequest(Division.WOMEN, 1992, 168))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.division").value("WOMEN"))
        .andExpect(jsonPath("$.birthYear").value(1992))
        .andExpect(jsonPath("$.heightCm").value(168));

    mockMvc
        .perform(get("/antares/users/me").cookie(cookies))
        .andExpect(jsonPath("$.division").value("WOMEN"));
    mockMvc
        .perform(get("/antares/auth/verify/api").cookie(cookies))
        .andExpect(header().string("X-Auth-User-Division", "WOMEN"));

    // Null clears
    putProfile(new AthleteProfileRequest(null, null, null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.division").doesNotExist())
        .andExpect(jsonPath("$.heightCm").doesNotExist());
  }

  @Test
  @DisplayName("profile: out-of-range values -> 400")
  void invalidProfile() throws Exception {
    putProfile(new AthleteProfileRequest(null, LocalDate.now().getYear() + 1, null))
        .andExpect(status().isBadRequest());
    putProfile(new AthleteProfileRequest(null, null, 40)).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("body weights: one per day (replaced), most recent first, own measures only")
  void bodyWeights() throws Exception {
    // The server's day is UTC; a client's local day may already be tomorrow
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    recordWeight(new BodyWeightRequest(new BigDecimal("80.4"), today.minusDays(7)))
        .andExpect(status().isOk());
    recordWeight(new BodyWeightRequest(new BigDecimal("79"), null)).andExpect(status().isOk());
    recordWeight(new BodyWeightRequest(new BigDecimal("78.6"), null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.measuredOn").value(today.toString()));
    recordWeight(new BodyWeightRequest(new BigDecimal("78"), today.plusDays(2)))
        .andExpect(status().isBadRequest());
    recordWeight(new BodyWeightRequest(new BigDecimal("5"), null))
        .andExpect(status().isBadRequest());

    String body =
        mockMvc
            .perform(get("/antares/users/me/body-weights").cookie(cookies))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].weightKg").value(78.6))
            .andExpect(jsonPath("$[1].weightKg").value(80.4))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long oldest = jsonMapper.readTree(body).get(1).get("id").asLong();

    // Another user cannot delete it
    Cookie[] other = registerAndLogin("other.athlete@example.com", PASSWORD);
    mockMvc
        .perform(delete("/antares/users/me/body-weights/" + oldest).with(csrf()).cookie(other))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(delete("/antares/users/me/body-weights/" + oldest).with(csrf()).cookie(cookies))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get("/antares/users/me/body-weights").cookie(cookies))
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  @DisplayName("internal API: a user's division only, with the secret; 404 for an unknown user")
  void internalDivision() throws Exception {
    putProfile(new AthleteProfileRequest(Division.MEN, null, null)).andExpect(status().isOk());
    String me =
        mockMvc
            .perform(get("/antares/users/me").cookie(cookies))
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode user = jsonMapper.readTree(me);
    String url = "/antares/internal/users/" + user.get("id").asLong() + "/division";

    mockMvc
        .perform(get(url).header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.division").value("MEN"))
        .andExpect(jsonPath("$.email").doesNotExist());
    mockMvc.perform(get(url)).andExpect(status().isNotFound());
    mockMvc
        .perform(
            get("/antares/internal/users/999999/division")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isNotFound());
  }

  private ResultActions putProfile(
      AthleteProfileRequest request) throws Exception {
    return mockMvc.perform(
        put("/antares/users/me/athlete-profile")
            .with(csrf())
            .cookie(cookies)
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonMapper.writeValueAsString(request)));
  }

  private ResultActions recordWeight(
      BodyWeightRequest request) throws Exception {
    return mockMvc.perform(
        post("/antares/users/me/body-weights")
            .with(csrf())
            .cookie(cookies)
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonMapper.writeValueAsString(request)));
  }
}
