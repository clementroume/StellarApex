package apex.stellar.aldebaran.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import apex.stellar.aldebaran.config.BaseIntegrationTest;
import apex.stellar.aldebaran.dto.WodMovementRequest;
import apex.stellar.aldebaran.dto.WodRequest;
import apex.stellar.aldebaran.model.entities.ComponentTechnique;
import apex.stellar.aldebaran.model.entities.DumbbellBase;
import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.MovementComponent;
import apex.stellar.aldebaran.model.entities.MovementMuscle;
import apex.stellar.aldebaran.model.entities.MovementMuscle.MuscleRole;
import apex.stellar.aldebaran.model.entities.MovementRoleAdjustment;
import apex.stellar.aldebaran.model.entities.MovementRoleAdjustment.TriggerKind;
import apex.stellar.aldebaran.model.entities.MovementUsualName;
import apex.stellar.aldebaran.model.entities.Muscle;
import apex.stellar.aldebaran.model.entities.Muscle.MuscleGroup;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodStructure;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.entities.WodMovement.Segment;
import apex.stellar.aldebaran.model.entities.WodMovement.TeamSplit;
import apex.stellar.aldebaran.model.enums.Category;
import apex.stellar.aldebaran.model.enums.Equipment;
import apex.stellar.aldebaran.model.enums.RequiredRule;
import apex.stellar.aldebaran.model.enums.Technique;
import apex.stellar.aldebaran.repository.MovementRepository;
import apex.stellar.aldebaran.repository.MuscleRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Integration tests of the movement catalog model: line rules, usual names, inherited records. */
class CatalogModelIT extends BaseIntegrationTest {

  private static final long GYM = 101L;
  private static final long ANNE = 100L;
  private static final String COACH = GYM + ":COACH:WOD_WRITE";

  @Autowired private MovementRepository movementRepository;
  @Autowired private MuscleRepository muscleRepository;
  @Autowired private WodRepository wodRepository;

  private Movement clean;
  private Movement jerk;
  private Movement chain;

  @BeforeEach
  void setUp() {
    clean = movement("Clean", Set.of(Technique.MUSCLE, Technique.POWER, Technique.SQUAT));
    jerk = movement("Jerk", Set.of(Technique.POWER, Technique.SPLIT));
    clean.getUsualNames().add(usual(clean, "Power Clean", "POWER"));
    clean = movementRepository.save(clean);
    chain = movement("Clean and Jerk", Set.of());
    chain.getComponents().add(step(chain, clean, 1));
    chain.getComponents().add(step(chain, jerk, 2));
    chain = movementRepository.save(chain);
  }

  private Movement movement(String name, Set<Technique> techniques) {
    return movementRepository.save(
        Movement.builder()
            .name(name)
            .category(Category.CLEAN)
            .equipment(new HashSet<>(Set.of(Equipment.BARBELL, Equipment.DUMBBELL)))
            .techniques(new HashSet<>(techniques))
            .impliedEquipment(Equipment.BARBELL)
            .build());
  }

  private static MovementUsualName usual(Movement m, String name, String technique) {
    return MovementUsualName.builder()
        .movement(m)
        .nameEn(name)
        .searchTerms("épaulé debout")
        .techniques(new HashSet<>(Set.of(technique)))
        .build();
  }

  private static MovementComponent step(Movement parent, Movement component, int order) {
    return MovementComponent.builder()
        .chain(parent)
        .component(component)
        .componentOrder(order)
        .countsForRecords(true)
        .build();
  }

  private Wod lift(String title, Movement movement, Set<String> techniques, Set<ComponentTechnique> parts) {
    Wod wod =
        Wod.builder()
            .title(title)
            .wodType(WodType.FOR_LOAD)
            .structure(WodStructure.MAX)
            .scoreType(ScoreType.WEIGHT)
            .repMax(1)
            .gymId(GYM)
            .movements(new ArrayList<>())
            .build();
    wod.getMovements()
        .add(
            WodMovement.builder()
                .wod(wod)
                .movement(movement)
                .orderIndex(1)
                .techniques(new HashSet<>(techniques))
                .componentTechniques(new HashSet<>(parts))
                .build());
    return wodRepository.save(wod);
  }

  private static MockHttpServletRequestBuilder as(
      MockHttpServletRequestBuilder request, long userId, String memberships) {
    return request
        .with(csrf())
        .header("X-Auth-User-Id", String.valueOf(userId))
        .header("X-Auth-User-Role", "USER")
        .header("X-Auth-Memberships", memberships)
        .header("X-Internal-Secret", "test-internal-secret")
        .contentType(MediaType.APPLICATION_JSON);
  }

  private void logLoad(Wod wod, double kg) throws Exception {
    String body =
        """
        {"date": "%s", "wodId": %d, "maxWeight": %s, "weightUnit": "KG", "scaling": "RX",
         "timeCapped": false, "verificationRequested": false}
        """
            .formatted(LocalDate.now().minusDays(1), wod.getId(), kg);
    mockMvc
        .perform(as(post("/aldebaran/scores"), ANNE, GYM + ":ATHLETE:").content(body))
        .andExpect(status().isCreated());
  }

  private WodRequest wodRequest(WodMovementRequest line) {
    return new WodRequest(
        "Catalog test", WodType.FOR_TIME, null, ScoreType.TIME, null, GYM, false, false, "Desc",
        null, null, null, null, null, 600, null, 0, 0, "50", List.of(line));
  }

  private WodMovementRequest line(Long movementId, Set<String> equipment, Set<String> techniques) {
    return new WodMovementRequest(
        movementId, 1, "50", null, null, null, null, null, null, null, null, null, null, null, null,
        equipment, techniques, null, null, null, null);
  }

  @Test
  @DisplayName("A record is read at the variants it proves, with its source")
  void inheritedRecord() throws Exception {
    logLoad(lift("Power Clean 1RM", clean, Set.of("POWER"), Set.of()), 100);

    mockMvc
        .perform(as(get("/aldebaran/records/me"), ANNE, GYM + ":ATHLETE:"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].variants", hasSize(2)))
        .andExpect(jsonPath("$[0].variants[0].base").value(true))
        .andExpect(jsonPath("$[0].variants[0].records[0].weightKg").value(100.0))
        .andExpect(jsonPath("$[0].variants[0].records[0].inheritedFrom.techniques[0]").value("POWER"))
        .andExpect(jsonPath("$[0].variants[1].records[0].inheritedFrom").doesNotExist());
  }

  @Test
  @DisplayName("A chain feeds its components, reception by reception")
  void chainRecord() throws Exception {
    logLoad(
        lift(
            "Power Clean and Split Jerk 1RM",
            chain,
            Set.of(),
            Set.of(new ComponentTechnique(1, "POWER"), new ComponentTechnique(2, "SPLIT"))),
        120);

    mockMvc
        .perform(as(get("/aldebaran/records/me"), ANNE, GYM + ":ATHLETE:"))
        .andExpect(jsonPath("$", hasSize(3)))
        .andExpect(jsonPath("$[0].movementName").value("Clean"))
        .andExpect(jsonPath("$[0].variants[0].records[0].inheritedFrom.movementName").value("Clean and Jerk"))
        .andExpect(jsonPath("$[2].movementName").value("Jerk"));
  }

  @Test
  @DisplayName("The box board shows the inherited records with their source")
  void board() throws Exception {
    when(antaresClient.getGymMemberIds(GYM)).thenReturn(List.of(ANNE));
    logLoad(lift("Power Clean 1RM", clean, Set.of("POWER"), Set.of()), 100);

    mockMvc
        .perform(
            as(get("/aldebaran/records/gyms/" + GYM), ANNE, GYM + ":ATHLETE:")
                .param("movementId", clean.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].weightKg").value(100.0))
        .andExpect(jsonPath("$[0].inheritedFrom.techniques[0]").value("POWER"));
  }

  @Test
  @DisplayName("The movement search lists the usual names, also when read from the cache")
  void usualNames() throws Exception {
    for (int call = 0; call < 2; call++) {
      assertUsualNames();
    }
  }

  @Test
  @DisplayName("The movement list says what a chain is made of")
  void listComposition() throws Exception {
    mockMvc
        .perform(as(get("/aldebaran/movements"), ANNE, GYM + ":ATHLETE:"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name == 'Clean and Jerk')].componentNames[0]", hasItem("Clean")))
        .andExpect(jsonPath("$[?(@.name == 'Clean and Jerk')].componentNames[1]", hasItem("Jerk")));
  }

  private void assertUsualNames() throws Exception {
    mockMvc
        .perform(as(get("/aldebaran/movements/usual-names"), ANNE, GYM + ":ATHLETE:"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].nameEn", hasItem("Power Clean")))
        .andExpect(jsonPath("$[0].movementName").value("Clean"))
        .andExpect(jsonPath("$[0].searchTerms").value("épaulé debout"));
  }

  @Test
  @DisplayName("My records name each variant with its usual name")
  void recordsCarryUsualName() throws Exception {
    logLoad(lift("Power Clean 1RM", clean, Set.of("POWER"), Set.of()), 100);
    mockMvc
        .perform(as(get("/aldebaran/records/me"), ANNE, GYM + ":ATHLETE:"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].variants[0].usualNameEn").doesNotExist())
        .andExpect(jsonPath("$[0].variants[1].usualNameEn").value("Power Clean"));
  }

  @Test
  @DisplayName("A variant reads its usual name and its adjusted roles")
  void variant() throws Exception {
    Muscle hamstrings = roleFixture();
    String path = "/aldebaran/movements/" + clean.getId() + "/variant";

    mockMvc
        .perform(as(get(path).param("techniques", "POWER"), ANNE, GYM + ":ATHLETE:"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.usualNameEn").value("Power Clean"))
        .andExpect(jsonPath("$.variantKey").value(";POWER"))
        .andExpect(jsonPath("$.muscles", hasSize(1)))
        .andExpect(jsonPath("$.muscles[0].muscleId").value(hamstrings.getId()))
        .andExpect(jsonPath("$.muscles[0].role").value("SYNERGIST"));

    mockMvc
        .perform(as(get(path), ANNE, GYM + ":ATHLETE:"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.usualNameEn").doesNotExist())
        .andExpect(jsonPath("$.variantKey").value(""))
        .andExpect(jsonPath("$.muscles[0].role").value("AGONIST"));

    mockMvc
        .perform(as(get("/aldebaran/movements/999999/variant"), ANNE, GYM + ":ATHLETE:"))
        .andExpect(status().isNotFound());

    // A value the movement does not allow is ignored
    mockMvc
        .perform(as(get(path).param("techniques", "NOT_A_TECHNIQUE"), ANNE, GYM + ":ATHLETE:"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.variantKey").value(""));
  }

  @Test
  @DisplayName("A movement used by a chain cannot be deleted")
  void deleteComponent() throws Exception {
    mockMvc
        .perform(
            delete("/aldebaran/movements/" + jerk.getId())
                .with(csrf())
                .header("X-Auth-User-Id", String.valueOf(ANNE))
                .header("X-Auth-User-Role", "ADMIN")
                .header("X-Auth-Memberships", "")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("A muscle lists the movements using it, with what brings each role")
  void muscleUsage() throws Exception {
    Muscle hamstrings = roleFixture();
    mockMvc
        .perform(
            as(
                get("/aldebaran/muscles/" + hamstrings.getId() + "/movements"),
                ANNE,
                GYM + ":ATHLETE:"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].movementName").value("Clean"))
        .andExpect(jsonPath("$[0].role").value("AGONIST"))
        .andExpect(jsonPath("$[0].trigger").doesNotExist())
        .andExpect(jsonPath("$[1].role").value("SYNERGIST"))
        .andExpect(jsonPath("$[1].trigger").value("POWER"));
  }

  /** The Clean works the hamstrings as agonist, as synergist on a power reception. */
  private Muscle roleFixture() {
    Muscle hamstrings =
        muscleRepository.save(
            Muscle.builder().medicalName("Hamstrings").muscleGroup(MuscleGroup.LEGS).build());
    clean
        .getTargetedMuscles()
        .add(
            MovementMuscle.builder()
                .movement(clean)
                .muscle(hamstrings)
                .role(MuscleRole.AGONIST)
                .build());
    clean
        .getRoleAdjustments()
        .add(
            MovementRoleAdjustment.builder()
                .movement(clean)
                .triggerKind(TriggerKind.TECHNIQUE)
                .trigger("POWER")
                .muscle(hamstrings)
                .role(MuscleRole.SYNERGIST)
                .build());
    clean = movementRepository.save(clean);
    return hamstrings;
  }

  @Test
  @DisplayName("A saved line is canonical and reads through its usual name")
  void lineIsCanonical() throws Exception {
    clean.getDumbbellBases().add(new DumbbellBase("", 2));
    clean.getTechniques().add(Technique.DOUBLE);
    clean = movementRepository.save(clean);

    mockMvc
        .perform(
            as(post("/aldebaran/wods"), 20, COACH)
                .content(
                    objectMapper.writeValueAsString(
                        wodRequest(line(clean.getId(), Set.of("DUMBBELL"), Set.of("POWER", "DOUBLE"))))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.movements[0].techniques", hasSize(1)))
        .andExpect(jsonPath("$.movements[0].techniques[0]").value("POWER"))
        .andExpect(jsonPath("$.movements[0].display.usualNameEn").value("Power Clean"))
        .andExpect(jsonPath("$.movements[0].display.remainingEquipment[0]").value("DUMBBELL"))
        .andExpect(jsonPath("$.movements[0].display.implementCount").value(2));
  }

  @Test
  @DisplayName("A line missing a required value, or with two exclusive values, is refused")
  void lineRules() throws Exception {
    clean.getRequiredRules().add(RequiredRule.EQUIPMENT);
    clean = movementRepository.save(clean);

    mockMvc
        .perform(
            as(post("/aldebaran/wods"), 20, COACH)
                .content(objectMapper.writeValueAsString(wodRequest(line(clean.getId(), Set.of(), Set.of())))))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            as(post("/aldebaran/wods"), 20, COACH)
                .content(
                    objectMapper.writeValueAsString(
                        wodRequest(line(clean.getId(), Set.of("BARBELL"), Set.of("POWER", "SQUAT"))))))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Updating a WOD keeps the segment, the complex group and the team split of a line")
  void updateKeepsLineFields() throws Exception {
    WodMovementRequest created = line(clean.getId(), Set.of(), Set.of());
    String body =
        mockMvc
            .perform(as(post("/aldebaran/wods"), 20, COACH).content(objectMapper.writeValueAsString(wodRequest(created))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long id = objectMapper.readTree(body).get("id").asLong();

    WodMovementRequest updated =
        new WodMovementRequest(
            clean.getId(), 1, "50", null, null, null, null, null, null, null, null, null, null, null,
            null, Set.of(), Set.of(), null, null, null, null, null, Segment.BUY_IN, 2, TeamSplit.EACH);
    mockMvc
        .perform(as(put("/aldebaran/wods/" + id), 20, COACH).content(objectMapper.writeValueAsString(wodRequest(updated))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.movements[0].segment").value("BUY_IN"))
        .andExpect(jsonPath("$.movements[0].complexGroup").value(2))
        .andExpect(jsonPath("$.movements[0].teamSplit").value("EACH"));
  }
}
