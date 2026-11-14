package apex.stellar.aldebaran.controller;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import apex.stellar.aldebaran.config.BaseIntegrationTest;
import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.MovementMuscle.MuscleRole;
import apex.stellar.aldebaran.model.entities.MovementMuscle;
import apex.stellar.aldebaran.model.entities.Muscle.MuscleRegion;
import apex.stellar.aldebaran.model.entities.Muscle;
import apex.stellar.aldebaran.model.entities.PerSet;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodStructure;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.enums.Category;
import apex.stellar.aldebaran.repository.MovementRepository;
import apex.stellar.aldebaran.repository.MuscleRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import apex.stellar.aldebaran.service.WeeklyBalanceService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Integration tests of the weekly balance (lot D): computed on first display, rebuilt after a
 * write, the athlete's own data only, the admin recompute.
 */
class BalanceIT extends BaseIntegrationTest {

  private static final long GYM = 101L;
  private static final long ANNE = 100L;
  private static final long BOB = 200L;

  @Autowired private MuscleRepository muscleRepository;
  @Autowired private MovementRepository movementRepository;
  @Autowired private WodRepository wodRepository;

  private Wod wod;
  private LocalDate monday;

  @BeforeEach
  void setUp() {
    Muscle quads =
        muscleRepository.save(
            Muscle.builder().medicalName("Quadriceps Test").region(MuscleRegion.THIGH_LEG).build());
    Muscle abs =
        muscleRepository.save(
            Muscle.builder().medicalName("Abdominals Test").region(MuscleRegion.ABDOMEN_PELVIS).build());
    Movement squat =
        Movement.builder()
            .name("Balance Squat")
            .category(Category.LUNGES)
            .perSet(new PerSet(10, null, 60, null))
            .equipment(new HashSet<>())
            .techniques(new HashSet<>())
            .build();
    squat.getTargetedMuscles().add(MovementMuscle.builder().movement(squat).muscle(quads).role(MuscleRole.AGONIST).build());
    squat.getTargetedMuscles().add(MovementMuscle.builder().movement(squat).muscle(abs).role(MuscleRole.STABILIZER).build());
    movementRepository.save(squat);

    Wod w =
        Wod.builder()
            .title("Balance For Time")
            .wodType(WodType.FOR_TIME)
            .structure(WodStructure.SINGLE)
            .scoreType(ScoreType.TIME)
            .gymId(GYM)
            .movements(new ArrayList<>())
            .build();
    w.getMovements().add(WodMovement.builder().wod(w).movement(squat).orderIndex(1).repsScheme("20").build());
    wod = wodRepository.save(w);
    monday = WeeklyBalanceService.monday(LocalDate.now().minusDays(7));
  }

  private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, long userId, String role) {
    return request
        .header("X-Auth-User-Id", String.valueOf(userId))
        .header("X-Auth-User-Role", role)
        .header("X-Auth-Memberships", GYM + ":ATHLETE:")
        .header("X-Internal-Secret", "test-internal-secret")
        .contentType(MediaType.APPLICATION_JSON);
  }

  private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, long userId) {
    return as(request, userId, "USER");
  }

  private long logScore(long userId, LocalDate date, int seconds) throws Exception {
    String body =
        """
        {"date": "%s", "wodId": %d, "timeSeconds": %d, "scaling": "RX", "timeCapped": false,
         "verificationRequested": false}
        """
            .formatted(date, wod.getId(), seconds);
    String json =
        mockMvc
            .perform(as(post("/aldebaran/scores"), userId).content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(json).get("id").asLong();
  }

  @Test
  @DisplayName("A score counts on first display: muscles, volume, minutes, modality, the 4-week window")
  void firstDisplay() throws Exception {
    logScore(ANNE, monday.plusDays(2), 300);

    mockMvc
        .perform(as(get("/aldebaran/balance/me").param("week", monday.plusDays(4).toString()), ANNE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.weekStart").value(monday.toString()))
        .andExpect(jsonPath("$.windowStart").value(monday.minusWeeks(3).toString()))
        .andExpect(jsonPath("$.muscles", hasSize(2)))
        .andExpect(jsonPath("$.muscles[?(@.medicalName == 'Quadriceps Test')].movingSets").value(2.0))
        .andExpect(jsonPath("$.muscles[?(@.medicalName == 'Abdominals Test')].holdingSets").value(2.0))
        .andExpect(jsonPath("$.volume", hasSize(8)))
        .andExpect(jsonPath("$.volume[7].sets").value(closeTo(2.0, 1e-9)))
        .andExpect(jsonPath("$.minutes.SHORT").value(5))
        .andExpect(jsonPath("$.modalities.GYMNASTICS").value(closeTo(2.0, 1e-9)))
        .andExpect(jsonPath("$.lifts", hasSize(0)));
  }

  @Test
  @DisplayName("A deleted score leaves the stored week: it is rebuilt on the next display")
  void rebuiltAfterWrite() throws Exception {
    long scoreId = logScore(ANNE, monday, 300);
    String week = monday.toString();
    mockMvc
        .perform(as(get("/aldebaran/balance/me").param("week", week), ANNE))
        .andExpect(jsonPath("$.muscles", hasSize(2)));

    mockMvc.perform(as(delete("/aldebaran/scores/" + scoreId), ANNE)).andExpect(status().isNoContent());

    mockMvc
        .perform(as(get("/aldebaran/balance/me").param("week", week), ANNE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.muscles", hasSize(0)))
        .andExpect(jsonPath("$.volume[7].sets").value(0.0));
  }

  @Test
  @DisplayName("Only one's own balance: another athlete sees nothing of it")
  void ownOnly() throws Exception {
    logScore(ANNE, monday, 300);

    mockMvc
        .perform(as(get("/aldebaran/balance/me").param("week", monday.toString()), BOB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.muscles", hasSize(0)));
  }

  @Test
  @DisplayName("The recompute is for platform admins only")
  void recomputeAdminOnly() throws Exception {
    mockMvc.perform(as(post("/aldebaran/balance/recompute"), ANNE)).andExpect(status().isForbidden());
    mockMvc
        .perform(as(post("/aldebaran/balance/recompute"), 1L, "ADMIN"))
        .andExpect(status().isNoContent());
  }
}
