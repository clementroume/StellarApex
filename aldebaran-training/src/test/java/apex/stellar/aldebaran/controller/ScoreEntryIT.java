package apex.stellar.aldebaran.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import apex.stellar.aldebaran.config.BaseIntegrationTest;
import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.enums.Category;
import apex.stellar.aldebaran.model.enums.Equipment;
import apex.stellar.aldebaran.repository.MovementRepository;
import tools.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Integration tests of the score entry of lot S: the official Scaled version of a WOD, lines done
 * otherwise than prescribed (RX refused, Rx+, official Scaled), leaderboards, sets per line and
 * the LOAD score, records of every strength line and of a substitute, the round as done and the
 * unit checks.
 */
class ScoreEntryIT extends BaseIntegrationTest {

  private static final long GYM = 701L;
  private static final long COACH = 7000L;
  private static final String STAFF = GYM + ":COACH:WOD_WRITE";
  private static final String MEMBER = GYM + ":ATHLETE:";

  @Autowired private MovementRepository movementRepository;

  private Movement thruster;
  private Movement pullUp;
  private Movement ringRow;
  private Movement squat;
  private Movement press;
  private Movement frontSquat;

  @BeforeEach
  void setUp() {
    thruster = movement("Thruster S", Category.SQUAT, Set.of(Equipment.BARBELL));
    pullUp = movement("Pull-up S", Category.PULLING, Set.of());
    ringRow = movement("Ring Row S", Category.PULLING, Set.of());
    squat = movement("Back Squat S", Category.SQUAT, Set.of(Equipment.BARBELL));
    press = movement("Shoulder Press S", Category.PRESS_AND_JERK, Set.of(Equipment.BARBELL));
    frontSquat = movement("Front Squat S", Category.SQUAT, Set.of(Equipment.BARBELL));
  }

  // --- Fixtures ---

  private Movement movement(String name, Category category, Set<Equipment> equipment) {
    return movementRepository.save(
        Movement.builder()
            .name(name)
            .category(category)
            .equipment(new HashSet<>(equipment))
            .techniques(new HashSet<>())
            .impliedEquipment(equipment.contains(Equipment.BARBELL) ? Equipment.BARBELL : null)
            .targetedMuscles(new HashSet<>())
            .build());
  }

  private static MockHttpServletRequestBuilder as(
      MockHttpServletRequestBuilder request, long userId, String memberships, String division) {
    return request
        .header("X-Auth-User-Id", String.valueOf(userId))
        .header("X-Auth-User-Role", "USER")
        .header("X-Auth-User-Division", division)
        .header("X-Auth-Memberships", memberships)
        .header("X-Internal-Secret", "test-internal-secret")
        .contentType(MediaType.APPLICATION_JSON);
  }

  private JsonNode created(MockHttpServletRequestBuilder request) throws Exception {
    String json =
        mockMvc
            .perform(request)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(json);
  }

  /** A box Fran with an official Scaled version: 65/45 lb thrusters, ring rows. */
  private JsonNode fran() throws Exception {
    String body =
        """
        {"title": "Box Fran", "wodType": "FOR_TIME", "structure": "SCHEME", "scoreType": "TIME",
         "gymId": %d, "isPublic": false, "competition": false,
         "movements": [
           {"movementId": %d, "orderIndex": 1, "repsScheme": "21-15-9", "weight": 95,
            "weightFemale": 65, "weightUnit": "LBS",
            "scaled": {"movementId": %d, "weight": 65, "weightFemale": 45, "weightUnit": "LBS"}},
           {"movementId": %d, "orderIndex": 2, "repsScheme": "21-15-9",
            "scaled": {"movementId": %d}}]}
        """
            .formatted(GYM, thruster.getId(), thruster.getId(), pullUp.getId(), ringRow.getId());
    return created(as(post("/aldebaran/wods"), COACH, STAFF, "MEN").content(body));
  }

  private static String time(long wodId, String scaling, int seconds, String lines) {
    return """
        {"date": "%s", "wodId": %d, "timeMinutes": 0, "timeSeconds": %d, "scaling": "%s",
         "weightUnit": "KG", "timeCapped": false, "verificationRequested": false, "lines": [%s]}
        """
        .formatted(LocalDate.now(), wodId, seconds, scaling, lines);
  }

  // --- Tests ---

  @Test
  @DisplayName("Official Scaled version: saved, returned with every value and the equivalents")
  void officialVersion() throws Exception {
    JsonNode wod = fran();
    long id = wod.get("id").asLong();

    mockMvc
        .perform(as(get("/aldebaran/wods/" + id), COACH, STAFF, "MEN"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.movements[0].scaled.weight").value(65.0))
        .andExpect(jsonPath("$.movements[0].scaled.weightFemale").value(45.0))
        .andExpect(jsonPath("$.movements[0].scaled.repsScheme").value("21-15-9"))
        .andExpect(jsonPath("$.movements[0].scaled.equivalents.weightKg").value(30.0))
        .andExpect(jsonPath("$.movements[0].equivalents.weightKg").value(40.0))
        .andExpect(jsonPath("$.movements[0].equivalents.weightLb").value(95.0))
        .andExpect(jsonPath("$.movements[1].scaled.movement.name").value("Ring Row S"));
  }

  @Test
  @DisplayName("RX changes nothing; official Scaled and RX are ranked, a free Scaled and Rx+ never")
  void levelsAndLeaderboards() throws Exception {
    JsonNode wod = fran();
    long id = wod.get("id").asLong();
    long thrusterLine = wod.get("movements").get(0).get("id").asLong();
    long pullLine = wod.get("movements").get(1).get("id").asLong();
    String ringRows =
        "{\"wodMovementId\": %d, \"movementId\": %d}".formatted(pullLine, ringRow.getId());
    String light =
        "{\"wodMovementId\": %d, \"movementId\": %d, \"weight\": 30}"
            .formatted(thrusterLine, thruster.getId());
    String heavy =
        "{\"wodMovementId\": %d, \"movementId\": %d, \"weight\": 50}"
            .formatted(thrusterLine, thruster.getId());

    // RX with a change: refused
    mockMvc
        .perform(as(post("/aldebaran/scores"), 1L, MEMBER, "MEN").content(time(id, "RX", 300, ringRows)))
        .andExpect(status().isBadRequest());
    // RX at 40 kg on a 95 lb line: the box value, still RX
    JsonNode rx =
        created(
            as(post("/aldebaran/scores"), 1L, MEMBER, "MEN")
                .content(
                    time(
                        id,
                        "RX",
                        250,
                        "{\"wodMovementId\": %d, \"movementId\": %d, \"weight\": 40}"
                            .formatted(thrusterLine, thruster.getId()))));
    JsonNode official =
        created(
            as(post("/aldebaran/scores"), 2L, MEMBER, "MEN")
                .content(time(id, "SCALED", 300, light + "," + ringRows)));
    JsonNode free =
        created(as(post("/aldebaran/scores"), 3L, MEMBER, "MEN").content(time(id, "SCALED", 200, ringRows)));
    JsonNode rxPlus =
        created(as(post("/aldebaran/scores"), 4L, MEMBER, "MEN").content(time(id, "SCALED", 180, heavy)));

    assertFalse(rx.get("scaledOfficial").asBoolean());
    assertTrue(official.get("scaledOfficial").asBoolean());
    assertFalse(free.get("scaledOfficial").asBoolean());
    assertFalse(free.get("rxPlus").asBoolean());
    assertTrue(rxPlus.get("rxPlus").asBoolean());
    assertEquals(
        "Ring Row S", official.get("lines").get(1).get("movement").get("name").asText(""),
        official.get("lines").toString());

    mockMvc
        .perform(as(get("/aldebaran/scores/leaderboard/" + id), 1L, MEMBER, "MEN").param("scaling", "RX"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].userId").value(1));
    mockMvc
        .perform(
            as(get("/aldebaran/scores/leaderboard/" + id), 1L, MEMBER, "MEN").param("scaling", "SCALED"))
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].userId").value(2));
    // A free Scaled is never ranked
    mockMvc
        .perform(as(get("/aldebaran/scores/" + free.get("id").asLong() + "/compare"), 3L, MEMBER, "MEN"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Level preview: tells Rx+ or the official version before saving, and saves nothing")
  void levelPreview() throws Exception {
    JsonNode wod = fran();
    long id = wod.get("id").asLong();
    long thrusterLine = wod.get("movements").get(0).get("id").asLong();
    long pullLine = wod.get("movements").get(1).get("id").asLong();
    String heavy =
        "{\"wodMovementId\": %d, \"movementId\": %d, \"weight\": 50}".formatted(thrusterLine, thruster.getId());
    String light =
        "{\"wodMovementId\": %d, \"movementId\": %d, \"weight\": 30}".formatted(thrusterLine, thruster.getId());
    String ringRows = "{\"wodMovementId\": %d, \"movementId\": %d}".formatted(pullLine, ringRow.getId());

    mockMvc
        .perform(
            as(post("/aldebaran/scores/level-preview"), 30L, MEMBER, "MEN")
                .content(time(id, "SCALED", 0, heavy)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rxPlus").value(true))
        .andExpect(jsonPath("$.asPrescribed").value(false))
        .andExpect(jsonPath("$.official").value(false));
    mockMvc
        .perform(
            as(post("/aldebaran/scores/level-preview"), 30L, MEMBER, "MEN")
                .content(time(id, "SCALED", 0, light + "," + ringRows)))
        .andExpect(jsonPath("$.official").value(true))
        .andExpect(jsonPath("$.rxPlus").value(false));
    mockMvc
        .perform(
            as(post("/aldebaran/scores/level-preview"), 30L, MEMBER, "MEN").content(time(id, "RX", 0, "")))
        .andExpect(jsonPath("$.asPrescribed").value(true));
    // Nothing saved
    mockMvc
        .perform(as(get("/aldebaran/scores/me"), 30L, MEMBER, "MEN"))
        .andExpect(jsonPath("$.content", hasSize(0)));
  }

  @Test
  @DisplayName("CrossFit Total: sets per line, LOAD = sum of the bests, each lift and a substitute fed")
  void crossFitTotal() throws Exception {
    String body =
        """
        {"title": "Box Total", "wodType": "FOR_LOAD", "structure": "MAX", "scoreType": "LOAD",
         "gymId": %d, "isPublic": false, "competition": false,
         "movements": [
           {"movementId": %d, "orderIndex": 1, "repsScheme": "1"},
           {"movementId": %d, "orderIndex": 2, "repsScheme": "1"}]}
        """
            .formatted(GYM, squat.getId(), press.getId());
    JsonNode wod = created(as(post("/aldebaran/wods"), COACH, STAFF, "MEN").content(body));
    long id = wod.get("id").asLong();
    long squatLine = wod.get("movements").get(0).get("id").asLong();
    long pressLine = wod.get("movements").get(1).get("id").asLong();

    // A set without its line on a WOD with several movements: refused
    String unnamed =
        """
        {"date": "%s", "wodId": %d, "scaling": "RX", "weightUnit": "KG", "timeCapped": false,
         "verificationRequested": false, "sets": [{"reps": 1, "weight": 100}]}
        """
            .formatted(LocalDate.now(), id);
    mockMvc
        .perform(as(post("/aldebaran/scores"), 10L, MEMBER, "MEN").content(unnamed))
        .andExpect(status().isBadRequest());

    String sets =
        """
        {"date": "%s", "wodId": %d, "scaling": "RX", "weightUnit": "KG", "timeCapped": false,
         "verificationRequested": false, "sets": [
           {"reps": 1, "weight": 120, "wodMovementId": %d},
           {"reps": 1, "weight": 130, "wodMovementId": %d},
           {"reps": 1, "weight": 135, "completed": false, "wodMovementId": %d},
           {"reps": 1, "weight": 60, "wodMovementId": %d},
           {"reps": 1, "weight": 65, "wodMovementId": %d}]}
        """
            .formatted(LocalDate.now(), id, squatLine, squatLine, squatLine, pressLine, pressLine);
    JsonNode score = created(as(post("/aldebaran/scores"), 10L, MEMBER, "MEN").content(sets));
    assertEquals(195.0, score.get("totalLoad").asDouble());
    assertEquals(
        squatLine, score.get("sets").get(0).get("wodMovementId").asLong());

    mockMvc
        .perform(as(get("/aldebaran/records/me"), 10L, MEMBER, "MEN"))
        .andExpect(jsonPath("$", hasSize(2)));

    // Front squats done in place of the back squats: the front squat's record, a Scaled score
    String substitute =
        """
        {"date": "%s", "wodId": %d, "scaling": "SCALED", "weightUnit": "KG", "timeCapped": false,
         "verificationRequested": false,
         "lines": [{"wodMovementId": %d, "movementId": %d}],
         "sets": [{"reps": 1, "weight": 100, "wodMovementId": %d},
                  {"reps": 1, "weight": 50, "wodMovementId": %d}]}
        """
            .formatted(LocalDate.now(), id, squatLine, frontSquat.getId(), squatLine, pressLine);
    created(as(post("/aldebaran/scores"), 11L, MEMBER, "MEN").content(substitute));
    mockMvc
        .perform(
            as(get("/aldebaran/records/me"), 11L, MEMBER, "MEN")
                .param("movementId", frontSquat.getId().toString()))
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].movementName").value("Front Squat S"))
        .andExpect(jsonPath("$[0].variants[0].records[0].weightKg").value(100.0));
  }

  @Test
  @DisplayName("Reduced reps: rounds + reps use the round as done; a unit of the wrong kind is refused")
  void roundAsDone() throws Exception {
    String body =
        """
        {"title": "Box Cindy", "wodType": "AMRAP", "structure": "ROUNDS", "scoreType": "REPS",
         "gymId": %d, "isPublic": false, "competition": false, "timeCapSeconds": 1200,
         "repsPerRound": 30,
         "movements": [
           {"movementId": %d, "orderIndex": 1, "repsScheme": "5"},
           {"movementId": %d, "orderIndex": 2, "repsScheme": "25"}]}
        """
            .formatted(GYM, pullUp.getId(), squat.getId());
    JsonNode wod = created(as(post("/aldebaran/wods"), COACH, STAFF, "MEN").content(body));
    long id = wod.get("id").asLong();
    long pullLine = wod.get("movements").get(0).get("id").asLong();

    String score =
        """
        {"date": "%s", "wodId": %d, "rounds": 10, "reps": 5, "scaling": "SCALED",
         "timeCapped": false, "verificationRequested": false,
         "lines": [{"wodMovementId": %d, "movementId": %d, "repsScheme": "3"}]}
        """
            .formatted(LocalDate.now(), id, pullLine, pullUp.getId());
    JsonNode saved = created(as(post("/aldebaran/scores"), 20L, MEMBER, "MEN").content(score));
    assertEquals(285, saved.get("reps").asInt());
    assertEquals(28, saved.get("roundReps").asInt());

    String badUnit =
        """
        {"date": "%s", "wodId": %d, "reps": 100, "scaling": "RX", "distanceUnit": "KG",
         "timeCapped": false, "verificationRequested": false}
        """
            .formatted(LocalDate.now(), id);
    mockMvc
        .perform(as(post("/aldebaran/scores"), 20L, MEMBER, "MEN").content(badUnit))
        .andExpect(status().isBadRequest());
  }
}
