package apex.stellar.aldebaran.controller;

import static org.hamcrest.Matchers.hasSize;
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
import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.Score;
import apex.stellar.aldebaran.model.entities.Score.ScalingLevel;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodCategory;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.model.enums.Category;
import apex.stellar.aldebaran.model.enums.Equipment;
import apex.stellar.aldebaran.model.enums.Technique;
import apex.stellar.aldebaran.model.enums.Unit;
import apex.stellar.aldebaran.repository.MovementRepository;
import apex.stellar.aldebaran.repository.ScoreRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** Integration tests for {@link WodController}. */
class WodControllerIT extends BaseIntegrationTest {

  @Autowired private WodRepository wodRepository;
  @Autowired private MovementRepository movementRepository;
  @Autowired private ScoreRepository scoreRepository;

  private Movement pullUp;

  @BeforeEach
  void setUp() {
    scoreRepository.deleteAll();
    wodRepository.deleteAll();
    movementRepository.deleteAll();

    // Seed Movement
    pullUp = Movement.builder().name("Pull-up").category(Category.PULLING).build();
    movementRepository.save(pullUp);

    // Seed Initial WOD (Created by Gym 101)
    Wod fran =
        Wod.builder()
            .title("Fran")
            .wodType(WodType.FOR_TIME)
            .scoreType(ScoreType.TIME)
            .descriptionEn("21-15-9 Thrusters and Pull-ups")
            .isPublic(true)
            .gymId(101L) // Important for security checks
            .authorId(50L)
            .build();
    wodRepository.save(fran);
  }

  @Test
  @DisplayName("GET /wods/facets: should return the types, gyms and authors of readable WODs")
  void testGetFacets_Success() throws Exception {
    mockMvc
        .perform(
            get("/aldebaran/wods/facets")
                .param("scope", "PUBLIC")
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "USER")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.wodTypes", hasSize(1)))
        .andExpect(jsonPath("$.wodTypes[0]").value("FOR_TIME"))
        .andExpect(jsonPath("$.gymIds[0]").value(101))
        .andExpect(jsonPath("$.authorIds", hasSize(0)));
  }

  @Test
  @DisplayName("GET /wods?authorId: should return only that author's personal WODs")
  void testGetWods_ByAuthor() throws Exception {
    wodRepository.save(
        Wod.builder()
            .title("Personal")
            .wodType(WodType.AMRAP)
            .scoreType(ScoreType.REPS)
            .isPublic(true)
            .authorId(50L)
            .build());

    mockMvc
        .perform(
            get("/aldebaran/wods")
                .param("authorId", "50")
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "ADMIN")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].title").value("Personal"));
  }

  @Test
  @DisplayName("GET /wods: should return list of summaries")
  void testGetAllWods_Success() throws Exception {
    // Public access allows reading
    mockMvc
        .perform(
            get("/aldebaran/wods")
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "USER")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].title").value("Fran"))
        .andExpect(jsonPath("$.content[0].wodType").value("FOR_TIME"));
  }

  @Test
  @DisplayName("GET /wods: should filter by title")
  void testGetWods_FilterByTitle() throws Exception {
    // Given: Another WOD "Grace"
    wodRepository.save(
        Wod.builder()
            .title("Grace")
            .wodType(WodType.FOR_TIME)
            .scoreType(ScoreType.TIME)
            .isPublic(true)
            .build());

    mockMvc
        .perform(
            get("/aldebaran/wods")
                .param("search", "Grace")
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "USER")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].title").value("Grace"));
  }

  @Test
  @DisplayName("GET /wods: should filter by type")
  void testGetWods_FilterByType() throws Exception {
    mockMvc
        .perform(
            get("/aldebaran/wods")
                .param("type", "FOR_TIME")
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "USER")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].title").value("Fran"));
  }

  @Test
  @DisplayName("GET /wods/{id}: should return detailed WOD")
  void testGetWod_Success() throws Exception {
    Wod fran = wodRepository.findAll().getFirst();

    mockMvc
        .perform(
            get("/aldebaran/wods/" + fran.getId())
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "USER")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Fran"));
  }

  @Test
  @DisplayName("GET /wods/{id}: should return 404 if not found")
  void testGetWod_NotFound() throws Exception {
    mockMvc
        .perform(
            get("/aldebaran/wods/99999")
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "USER")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("GET /wods/{id}: Private WOD should be accessible only by Author")
  void testGetWod_Private_AccessControl() throws Exception {
    // Given: Private WOD by User 100
    Wod privateWod =
        Wod.builder()
            .title("Secret WOD")
            .wodType(WodType.FOR_TIME)
            .scoreType(ScoreType.TIME)
            .isPublic(false)
            .authorId(100L)
            .build();
    wodRepository.save(privateWod);

    // When: Author requests -> OK
    mockMvc
        .perform(
            get("/aldebaran/wods/" + privateWod.getId())
                .header("X-Auth-User-Id", "100")
                .header("X-Auth-User-Role", "USER")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isOk());

    // When: Other user requests -> Forbidden
    mockMvc
        .perform(
            get("/aldebaran/wods/" + privateWod.getId())
                .header("X-Auth-User-Id", "200")
                .header("X-Auth-User-Role", "USER")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("GET /wods/{id}: Gym WOD should be accessible only by Gym Members")
  void testGetWod_Gym_AccessControl() throws Exception {
    // Given: Gym WOD for Gym 101 (seeded in setUp as 'fran' but let's make a private one)
    Wod gymWod =
        Wod.builder()
            .title("Gym Exclusive")
            .wodType(WodType.AMRAP)
            .scoreType(ScoreType.REPS)
            .isPublic(false)
            .gymId(101L)
            .build();
    wodRepository.save(gymWod);

    // When: Member of Gym 101 requests -> OK
    mockMvc
        .perform(
            get("/aldebaran/wods/" + gymWod.getId())
                .header("X-Auth-User-Id", "50")
                .header("X-Auth-User-Role", "USER")
                .header("X-Auth-Memberships", "101:ATHLETE:")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isOk());

    // When: Member of Gym 102 requests -> Forbidden
    mockMvc
        .perform(
            get("/aldebaran/wods/" + gymWod.getId())
                .header("X-Auth-User-Id", "60")
                .header("X-Auth-User-Role", "USER")
                .header("X-Auth-Memberships", "102:ATHLETE:")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("POST/PUT /wods: the category is saved with the format and can be cleared")
  void testWodCategory_SavedAndCleared() throws Exception {
    WodMovementRequest movement =
        new WodMovementRequest(
            pullUp.getId(), 1, "21-15-9", 43.0, 30.0, Unit.KG, null, null, null, null, null,
            null, 60.0, 50.0, Unit.CENTIMETERS, null, null, "Wide grip", "Prise large", null,
            null);
    WodRequest girls =
        new WodRequest(
            "Girls Test", WodType.FOR_TIME, WodCategory.GIRLS, ScoreType.TIME, null, 101L, true,
            false,
            "21-15-9 reps", "21-15-9 répétitions", null, null, null, null, null, null, null, null, "21-15-9",
            List.of(movement));
    String body =
        mockMvc
            .perform(
                post("/aldebaran/wods")
                    .with(csrf())
                    .header("X-Auth-User-Id", "20")
                    .header("X-Auth-User-Role", "USER")
                    .header("X-Auth-Memberships", "101:COACH:WOD_WRITE")
                    .header("X-Internal-Secret", "test-internal-secret")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(girls)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.wodType").value("FOR_TIME"))
            .andExpect(jsonPath("$.category").value("GIRLS"))
            .andExpect(jsonPath("$.descriptionFr").value("21-15-9 répétitions"))
            .andExpect(jsonPath("$.movements[0].weightFemale").value(30.0))
            .andExpect(jsonPath("$.movements[0].notesFr").value("Prise large"))
            .andExpect(jsonPath("$.movements[0].heightFemale").value(50.0))
            .andExpect(jsonPath("$.movements[0].heightUnit").value("CENTIMETERS"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long id = objectMapper.readTree(body).get("id").asLong();

    WodRequest uncategorized =
        new WodRequest(
            "Girls Test", WodType.FOR_TIME, null, ScoreType.TIME, null, 101L, true, false, null, null, null,
            null, null, null,
            null, null, null, null, "21-15-9", List.of(movement));
    mockMvc
        .perform(
            put("/aldebaran/wods/" + id)
                .with(csrf())
                .header("X-Auth-User-Id", "20")
                .header("X-Auth-User-Role", "USER")
                .header("X-Auth-Memberships", "101:COACH:WOD_WRITE")
                .header("X-Internal-Secret", "test-internal-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(uncategorized)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.category").isEmpty());
  }

  @Test
  @DisplayName("GET/PUT /wods/{id}: a movement with several variants is returned and merged once")
  void testWodMovementWithSeveralVariants_NotDuplicated() throws Exception {
    pullUp.getEquipment().add(Equipment.RINGS);
    pullUp.getTechniques().addAll(Set.of(Technique.STRICT, Technique.KIPPING));
    movementRepository.save(pullUp);
    WodRequest request =
        new WodRequest(
            "Ring Pull-ups",
            WodType.FOR_TIME,
            null,
            ScoreType.TIME,
            null,
            101L,
            true,
            false,
            "Desc",
            null, null, null, null, null,
            600, null,
            0,
            0,
            "50",
            List.of(
                new WodMovementRequest(
                    pullUp.getId(),
                    1,
                    "50",
                    null, null, null, null, null, null, null, null, null, null, null, null,
                    Set.of("RINGS"),
                    Set.of("STRICT", "KIPPING"),
                    null, null,
                    null, null)));
    String body =
        mockMvc
            .perform(
                post("/aldebaran/wods")
                    .with(csrf())
                    .header("X-Auth-User-Id", "20")
                    .header("X-Auth-User-Role", "USER")
                    .header("X-Auth-Memberships", "101:COACH:WOD_WRITE")
                    .header("X-Internal-Secret", "test-internal-secret")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long id = objectMapper.readTree(body).get("id").asLong();

    mockMvc
        .perform(
            get("/aldebaran/wods/" + id)
                .header("X-Auth-User-Id", "20")
                .header("X-Auth-User-Role", "USER")
                .header("X-Auth-Memberships", "101:COACH:WOD_WRITE")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.movements", hasSize(1)))
        .andExpect(jsonPath("$.movements[0].techniques", hasSize(2)));

    mockMvc
        .perform(
            put("/aldebaran/wods/" + id)
                .with(csrf())
                .header("X-Auth-User-Id", "20")
                .header("X-Auth-User-Role", "USER")
                .header("X-Auth-Memberships", "101:COACH:WOD_WRITE")
                .header("X-Internal-Secret", "test-internal-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.movements", hasSize(1)));
  }

  @Test
  @DisplayName("POST /wods: should reject an equipment that the movement does not allow (400)")
  void testCreateWod_EquipmentNotAllowed() throws Exception {
    pullUp.getEquipment().add(Equipment.PULL_UP_BAR);
    movementRepository.save(pullUp);
    WodRequest request =
        new WodRequest(
            "Ring Pull-ups",
            WodType.FOR_TIME,
            null,
            ScoreType.TIME,
            null,
            101L,
            true,
            false,
            "Desc",
            null, null, null, null, null,
            600, null,
            0,
            0,
            "50",
            List.of(
                new WodMovementRequest(
                    pullUp.getId(),
                    1,
                    "50",
                    null, null, null, null, null, null, null, null, null, null, null, null,
                    Set.of("RINGS"),
                    null, null, null,
                    null, null)));
    mockMvc
        .perform(
            post("/aldebaran/wods")
                .with(csrf())
                .header("X-Auth-User-Id", "20")
                .header("X-Auth-User-Role", "USER")
                .header("X-Auth-Memberships", "101:COACH:WOD_WRITE")
                .header("X-Internal-Secret", "test-internal-secret")
                .locale(Locale.ENGLISH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Invalid WOD Movement"))
        .andExpect(
            jsonPath("$.detail").value("Equipment RINGS is not allowed for the movement Pull-up."));
  }

  @Test
  @DisplayName("POST /wods: should create WOD when Coach with WOD_WRITE permission")
  void testCreateWod_Success() throws Exception {
    WodRequest request =
        new WodRequest(
            "Murph",
            WodType.FOR_TIME,
            null,
            ScoreType.TIME,
            null,
            101L,
            true,
            false,
            "Hero Wod",
            null,
            "Wear a vest",
            null, null, null,
            3600, null,
            0,
            0,
            "Chipper",
            List.of(
                new WodMovementRequest(
                    pullUp.getId(),
                    1,
                    "100",
                    0.0,
                    null, null,
                    0,
                    null,
                    0.0,
                    null,
                    0,
                    null, null, null, null, null, null, null, null,
                    null, null)));
    mockMvc
        .perform(
            post("/aldebaran/wods")
                .with(csrf())
                // Simulation des headers Traefik pour un Coach autorisé
                .header("X-Auth-User-Id", "20")
                .header("X-Auth-User-Role", "USER")
                .header("X-Auth-Memberships", "101:COACH:WOD_WRITE")
                .header("X-Internal-Secret", "test-internal-secret") // Permission requise
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("Murph"))
        .andExpect(jsonPath("$.movements", hasSize(1)))
        .andExpect(jsonPath("$.capabilities.canModify").value(true))
        .andExpect(jsonPath("$.capabilities.locked").value(false));
  }

  @Test
  @DisplayName("POST /wods: structure fields and buy-in / buy-out segments are saved")
  void testCreateWod_StructureAndSegments() throws Exception {
    String body =
        """
        {"title": "Murph", "wodType": "CHIPPER", "scoreType": "TIME", "isPublic": false,
         "competition": false, "authorId": 20,
         "rounds": 20, "restSeconds": 0, "vestWeight": 20, "vestWeightFemale": 14,
         "vestWeightUnit": "LBS",
         "movements": [
           {"movementId": %d, "orderIndex": 1, "distance": 1600, "distanceUnit": "METERS",
            "segment": "BUY_IN"},
           {"movementId": %d, "orderIndex": 2, "repsScheme": "5"},
           {"movementId": %d, "orderIndex": 3, "distance": 1600, "distanceUnit": "METERS",
            "segment": "BUY_OUT"}]}
        """
            .formatted(pullUp.getId(), pullUp.getId(), pullUp.getId());
    mockMvc
        .perform(
            post("/aldebaran/wods")
                .with(csrf())
                .header("X-Auth-User-Id", "20")
                .header("X-Auth-User-Role", "USER")
                .header("X-Internal-Secret", "test-internal-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.wodType").value("CHIPPER"))
        .andExpect(jsonPath("$.rounds").value(20))
        .andExpect(jsonPath("$.vestWeight").value(20.0))
        .andExpect(jsonPath("$.vestWeightUnit").value("LBS"))
        .andExpect(jsonPath("$.movements[0].segment").value("BUY_IN"))
        .andExpect(jsonPath("$.movements[1].segment").value("MAIN"))
        .andExpect(jsonPath("$.movements[2].segment").value("BUY_OUT"));
  }

  @Test
  @DisplayName("POST /wods: Personal WOD creation should be allowed for any User")
  void testCreateWod_Personal_Success() throws Exception {
    WodRequest request =
        new WodRequest(
            "My Custom WOD",
            WodType.EMOM,
            null,
            ScoreType.NONE,
            100L, // authorId matches User
            null, // No Gym
            false, false, // Private
            null, null, null, null, null, null, null, null,
            60,
            10,
            null,
            List.of(
                new WodMovementRequest(
                    pullUp.getId(),
                    1,
                    "10",
                    0.0,
                    null, null,
                    0,
                    null,
                    0.0,
                    null,
                    0,
                    null, null, null, null, null, null, null, null,
                    null, null)));
    mockMvc
        .perform(
            post("/aldebaran/wods")
                .with(csrf())
                .header("X-Auth-User-Id", "100")
                .header("X-Auth-User-Role", "USER")
                .header("X-Internal-Secret", "test-internal-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("POST /wods: Gym Owner should be able to create Gym WOD")
  void testCreateWod_GymOwner_Success() throws Exception {
    WodRequest request =
        new WodRequest(
            "Owner WOD",
            WodType.FOR_TIME,
            null,
            ScoreType.TIME,
            null,
            101L, // Gym 101
            false,
            false,
            null, null, null, null, null, null, null, null, null, null, null,
            List.of(
                new WodMovementRequest(
                    pullUp.getId(),
                    1,
                    "50",
                    0.0,
                    null, null,
                    0,
                    null,
                    0.0,
                    null,
                    0,
                    null, null, null, null, null, null, null, null,
                    null, null)));
    mockMvc
        .perform(
            post("/aldebaran/wods")
                .with(csrf())
                .header("X-Auth-User-Id", "10")
                .header("X-Auth-User-Role", "USER")
                .header("X-Auth-Memberships", "101:OWNER:WOD_WRITE+SCORE_VERIFY")
                .header("X-Internal-Secret", "test-internal-secret") // Owner
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("POST /wods: Coach WITHOUT permission should be Forbidden")
  void testCreateWod_CoachNoPerm_Forbidden() throws Exception {
    WodRequest request =
        new WodRequest(
            "Unauthorized WOD",
            WodType.FOR_TIME,
            null,
            ScoreType.TIME,
            null,
            101L, // Gym 101
            false,
            false,
            null, null, null, null, null, null, null, null, null, null, null,
            List.of(
                new WodMovementRequest(
                    pullUp.getId(),
                    1,
                    "50",
                    0.0,
                    null, null,
                    0,
                    null,
                    0.0,
                    null,
                    0,
                    null, null, null, null, null, null, null, null,
                    null, null)));
    mockMvc
        .perform(
            post("/aldebaran/wods")
                .with(csrf())
                .header("X-Auth-User-Id", "20")
                .header("X-Auth-User-Role", "USER")
                .header("X-Auth-Memberships", "101:COACH:")
                .header("X-Internal-Secret", "test-internal-secret")
                // No Permissions header
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("POST /wods: Cross-Gym creation should be Forbidden")
  void testCreateWod_CrossGym_Forbidden() throws Exception {
    WodRequest request =
        new WodRequest(
            "Cross Gym WOD",
            WodType.FOR_TIME,
            null,
            ScoreType.TIME,
            null,
            101L, // Target Gym 101
            false,
            false,
            null, null, null, null, null, null, null, null, null, null, null,
            List.of(
                new WodMovementRequest(
                    pullUp.getId(),
                    1,
                    "50",
                    0.0,
                    null, null,
                    0,
                    null,
                    0.0,
                    null,
                    0,
                    null, null, null, null, null, null, null, null,
                    null, null)));
    mockMvc
        .perform(
            post("/aldebaran/wods")
                .with(csrf())
                .header("X-Auth-User-Id", "10")
                .header("X-Auth-User-Role", "USER")
                .header("X-Auth-Memberships", "102:OWNER:WOD_WRITE+SCORE_VERIFY")
                .header("X-Internal-Secret", "test-internal-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("POST /wods: should return 400 Bad Request for invalid input")
  void testCreateWod_ValidationFailure() throws Exception {
    WodRequest invalidRequest =
        new WodRequest(
            "", // Empty title
            null, null, // Null Type
            ScoreType.TIME,
            null, null,
            true,
            false,
            null, null, null, null, null, null,
            -10, null, // Negative time cap
            0,
            0,
            null,
            List.of()); // Empty movements

    mockMvc
        .perform(
            post("/aldebaran/wods")
                .with(csrf())
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "ADMIN")
                .header("X-Internal-Secret", "test-internal-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Validation Error"));
  }

  @Test
  @DisplayName("PUT /wods/{id}: should update WOD when Coach of same Gym")
  void testUpdateWod_Success() throws Exception {
    Wod existing = wodRepository.findAll().getFirst(); // Gym ID 101

    WodRequest updateRequest =
        new WodRequest(
            "Fran Modified",
            WodType.FOR_TIME,
            null,
            ScoreType.TIME,
            null,
            null, // GymId ignored in update
            true,
            false,
            "Harder version",
            null,
            "Scale if needed",
            null, null, null,
            1200, null,
            0,
            0,
            "21-15-9",
            List.of(
                new WodMovementRequest(
                    pullUp.getId(),
                    1,
                    "21-15-9",
                    40.0,
                    null, null,
                    0,
                    null,
                    0.0,
                    null,
                    0,
                    null, null, null, null, null, null, null, null,
                    null, null)));
    mockMvc
        .perform(
            put("/aldebaran/wods/" + existing.getId())
                .with(csrf())
                // Coach du gym 101
                .header("X-Auth-User-Id", "20")
                .header("X-Auth-User-Role", "USER")
                .header("X-Auth-Memberships", "101:COACH:WOD_WRITE")
                .header("X-Internal-Secret", "test-internal-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Fran Modified"))
        .andExpect(jsonPath("$.capabilities.canModify").value(true));

    Wod updated = wodRepository.findByIdWithMovements(existing.getId()).orElseThrow();
    assert (updated.getTitle().equals("Fran Modified"));
  }

  @Test
  @DisplayName("PUT /wods/{id}: should return 403 Forbidden for simple User")
  void testUpdateWod_Forbidden() throws Exception {
    Wod existing = wodRepository.findAll().getFirst();

    WodRequest request =
        new WodRequest(
            "Hacked Wod",
            WodType.AMRAP,
            null,
            ScoreType.REPS,
            null, null,
            false,
            false,
            null, null, null, null, null, null,
            0, null,
            0,
            0,
            null,
            List.of(
                new WodMovementRequest(
                    pullUp.getId(),
                    1,
                    "10",
                    0.0,
                    null, null,
                    0,
                    null,
                    0.0,
                    null,
                    0,
                    null, null, null, null, null, null, null, null,
                    null, null)));
    mockMvc
        .perform(
            put("/aldebaran/wods/" + existing.getId())
                .with(csrf())
                .header("X-Auth-User-Id", "99")
                .header("X-Auth-User-Role", "USER")
                .header("X-Internal-Secret", "test-internal-secret") // Simple User
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("PUT /wods/{id}: Author can update Personal WOD")
  void testUpdateWod_Personal_Success() throws Exception {
    Wod personalWod =
        Wod.builder()
            .title("My WOD")
            .wodType(WodType.FOR_TIME)
            .scoreType(ScoreType.TIME)
            .authorId(100L)
            .build();
    wodRepository.save(personalWod);

    WodRequest updateRequest =
        new WodRequest(
            "My WOD Updated",
            WodType.FOR_TIME,
            null,
            ScoreType.TIME,
            null, null,
            false,
            false,
            null, null, null, null, null, null, null, null, null, null, null,
            List.of(
                new WodMovementRequest(
                    pullUp.getId(),
                    1,
                    "10",
                    0.0,
                    null, null,
                    0,
                    null,
                    0.0,
                    null,
                    0,
                    null, null, null, null, null, null, null, null,
                    null, null)));
    mockMvc
        .perform(
            put("/aldebaran/wods/" + personalWod.getId())
                .with(csrf())
                .header("X-Auth-User-Id", "100") // Author
                .header("X-Auth-User-Role", "USER")
                .header("X-Internal-Secret", "test-internal-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("PUT /wods/{id}: Non-Author cannot update Personal WOD")
  void testUpdateWod_Personal_Forbidden() throws Exception {
    Wod personalWod =
        Wod.builder()
            .title("My WOD")
            .wodType(WodType.FOR_TIME)
            .scoreType(ScoreType.TIME)
            .authorId(100L)
            .build();
    wodRepository.save(personalWod);

    WodRequest updateRequest =
        new WodRequest(
            "Hacked",
            WodType.FOR_TIME,
            null,
            ScoreType.TIME,
            null, null,
            false,
            false,
            null, null, null, null, null, null, null, null, null, null, null,
            List.of(
                new WodMovementRequest(
                    pullUp.getId(),
                    1,
                    "10",
                    0.0,
                    null, null,
                    0,
                    null,
                    0.0,
                    null,
                    0,
                    null, null, null, null, null, null, null, null,
                    null, null)));
    mockMvc
        .perform(
            put("/aldebaran/wods/" + personalWod.getId())
                .with(csrf())
                .header("X-Auth-User-Id", "200") // Not Author
                .header("X-Auth-User-Role", "USER")
                .header("X-Internal-Secret", "test-internal-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("PUT /wods/{id}: should return 404 if WOD not found")
  void testUpdateWod_NotFound() throws Exception {
    WodRequest request =
        new WodRequest(
            "Ghost Wod",
            WodType.FOR_TIME,
            null,
            ScoreType.TIME,
            null, null,
            true,
            false,
            null, null, null, null, null, null,
            0, null,
            0,
            0,
            null,
            List.of(
                new WodMovementRequest(
                    pullUp.getId(),
                    1,
                    "10",
                    0.0,
                    null, null,
                    0,
                    null,
                    0.0,
                    null,
                    0,
                    null, null, null, null, null, null, null, null,
                    null, null)));
    mockMvc
        .perform(
            put("/aldebaran/wods/99999")
                .with(csrf())
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "ADMIN")
                .header("X-Internal-Secret", "test-internal-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("PUT /wods/{id}: should return 409 Conflict if WOD is locked (has scores)")
  void testUpdateWod_Locked() throws Exception {
    Wod existing = wodRepository.findAll().getFirst();

    // Create a score to lock the WOD
    Score score =
        Score.builder()
            .wod(existing)
            .userId(100L)
            .date(LocalDate.now())
            .scaling(ScalingLevel.RX)
            .timeSeconds(300)
            .build();
    scoreRepository.save(score);

    WodRequest updateRequest =
        new WodRequest(
            "Fran Modified",
            WodType.FOR_TIME,
            null,
            ScoreType.TIME,
            null, null,
            true,
            false,
            null, null, null, null, null, null,
            0, null,
            0,
            0,
            null,
            List.of(
                new WodMovementRequest(
                    pullUp.getId(),
                    1,
                    "21-15-9",
                    0.0,
                    null, null,
                    0,
                    null,
                    0.0,
                    null,
                    0,
                    null, null, null, null, null, null, null, null,
                    null, null)));
    mockMvc
        .perform(
            put("/aldebaran/wods/" + existing.getId())
                .with(csrf())
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "ADMIN")
                .header("X-Internal-Secret", "test-internal-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Resource Locked"));
  }

  // -------------------------------------------------------------------------
  // DELETE Operations
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("DELETE /wods/{id}: should delete WOD when Admin")
  void testDeleteWod_Success() throws Exception {
    Wod existing = wodRepository.findAll().getFirst();

    mockMvc
        .perform(
            delete("/aldebaran/wods/" + existing.getId())
                .with(csrf())
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "ADMIN")
                .header("X-Internal-Secret", "test-internal-secret")) // Global Admin
        .andExpect(status().isNoContent());

    assert (wodRepository.findById(existing.getId()).isEmpty());
  }

  @Test
  @DisplayName("DELETE /wods/{id}: should return 403 Forbidden for simple User")
  void testDeleteWod_Forbidden() throws Exception {
    Wod existing = wodRepository.findAll().getFirst();

    mockMvc
        .perform(
            delete("/aldebaran/wods/" + existing.getId())
                .with(csrf())
                .header("X-Auth-User-Id", "99")
                .header("X-Auth-User-Role", "USER")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isForbidden());

    assert (wodRepository.findById(existing.getId()).isPresent());
  }

  // -------------------------------------------------------------------------
  // Capabilities, multi-gym, scope and support session
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("GET /wods/{id}: Owner of the gym gets canModify=true")
  void testGetWod_Capabilities_Owner() throws Exception {
    Wod fran = wodRepository.findAll().getFirst(); // Gym 101

    mockMvc
        .perform(
            get("/aldebaran/wods/" + fran.getId())
                .header("X-Auth-User-Id", "10")
                .header("X-Auth-User-Role", "USER")
                .header("X-Auth-Memberships", "101:OWNER:WOD_WRITE+SCORE_VERIFY")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.capabilities.canModify").value(true))
        .andExpect(jsonPath("$.capabilities.locked").value(false));
  }

  @Test
  @DisplayName("GET /wods/{id}: Coach without WOD_WRITE gets canModify=false")
  void testGetWod_Capabilities_CoachWithoutPermission() throws Exception {
    Wod fran = wodRepository.findAll().getFirst(); // Gym 101

    mockMvc
        .perform(
            get("/aldebaran/wods/" + fran.getId())
                .header("X-Auth-User-Id", "20")
                .header("X-Auth-User-Role", "USER")
                .header("X-Auth-Memberships", "101:COACH:SCORE_VERIFY")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.capabilities.canModify").value(false));
  }

  @Test
  @DisplayName("GET /wods/{id}: WOD with scores is reported as locked")
  void testGetWod_Capabilities_Locked() throws Exception {
    Wod fran = wodRepository.findAll().getFirst();
    scoreRepository.save(
        Score.builder()
            .wod(fran)
            .userId(100L)
            .date(LocalDate.now())
            .scaling(ScalingLevel.RX)
            .timeSeconds(300)
            .build());

    mockMvc
        .perform(
            get("/aldebaran/wods/" + fran.getId())
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "ADMIN")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.capabilities.canModify").value(true))
        .andExpect(jsonPath("$.capabilities.locked").value(true));
  }

  @Test
  @DisplayName("GET /wods/{id}: Rights evaluated on the WOD's gym (read, canModify, outsider)")
  void testGetWod_MultiGymRead() throws Exception {
    Wod virtualWod =
        wodRepository.save(
            Wod.builder()
                .title("Virtual Programming")
                .wodType(WodType.AMRAP)
                .scoreType(ScoreType.REPS)
                .isPublic(false)
                .gymId(300L)
                .authorId(77L)
                .build());

    // Coach in 101, athlete in 300: read allowed, no write on gym 300
    mockMvc
        .perform(
            get("/aldebaran/wods/" + virtualWod.getId())
                .header("X-Auth-User-Id", "20")
                .header("X-Auth-User-Role", "USER")
                .header("X-Auth-Memberships", "101:COACH:WOD_WRITE,300:ATHLETE:")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.capabilities.canModify").value(false));

    // WOD_WRITE in gym 300 itself: canModify computed on the WOD's gym
    mockMvc
        .perform(
            get("/aldebaran/wods/" + virtualWod.getId())
                .header("X-Auth-User-Id", "20")
                .header("X-Auth-User-Role", "USER")
                .header("X-Auth-Memberships", "101:ATHLETE:,300:PROGRAMMER:WOD_WRITE+SCORE_VERIFY")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.capabilities.canModify").value(true));

    // Not a member of gym 300 (outsider): denied
    mockMvc
        .perform(
            get("/aldebaran/wods/" + virtualWod.getId())
                .header("X-Auth-User-Id", "20")
                .header("X-Auth-User-Role", "USER")
                .header("X-Auth-Memberships", "101:COACH:")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("DELETE /wods/{id}: Admin who is an athlete of another gym stays admin")
  void testDeleteWod_AdminWithMembership() throws Exception {
    Wod fran = wodRepository.findAll().getFirst(); // Gym 101

    mockMvc
        .perform(
            delete("/aldebaran/wods/" + fran.getId())
                .with(csrf())
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "ADMIN")
                .header("X-Auth-Memberships", "999:ATHLETE:")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("GET /wods?scope=GYM: lists only the member gyms' WODs, narrowed by gymId")
  void testGetWods_ScopeGym() throws Exception {
    wodRepository.save(
        Wod.builder()
            .title("Virtual")
            .wodType(WodType.AMRAP)
            .scoreType(ScoreType.REPS)
            .gymId(300L)
            .authorId(77L)
            .build());
    wodRepository.save(
        Wod.builder()
            .title("Someone's public WOD")
            .wodType(WodType.AMRAP)
            .scoreType(ScoreType.REPS)
            .isPublic(true)
            .authorId(88L)
            .build());

    mockMvc
        .perform(
            get("/aldebaran/wods")
                .param("scope", "GYM")
                .param("sort", "title")
                .header("X-Auth-User-Id", "20")
                .header("X-Auth-User-Role", "USER")
                .header("X-Auth-Memberships", "101:ATHLETE:,300:ATHLETE:")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(2)))
        .andExpect(jsonPath("$.content[0].title").value("Fran"))
        .andExpect(jsonPath("$.content[0].gymId").value(101))
        .andExpect(jsonPath("$.content[0].authorId").value(50))
        .andExpect(jsonPath("$.content[0].isPublic").value(true))
        .andExpect(jsonPath("$.content[1].title").value("Virtual"));

    mockMvc
        .perform(
            get("/aldebaran/wods")
                .param("scope", "GYM")
                .param("gymId", "300")
                .header("X-Auth-User-Id", "20")
                .header("X-Auth-User-Role", "USER")
                .header("X-Auth-Memberships", "101:ATHLETE:,300:ATHLETE:")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].title").value("Virtual"));
  }

  @Test
  @DisplayName("Support session: rights are those of the target user, not of the admin actor")
  void testSupportSession_UsesTargetRights() throws Exception {
    Wod fran = wodRepository.findAll().getFirst();

    // Admin (id 1) acting as an athlete of gym 101: no WOD_WRITE, so the delete is refused
    mockMvc
        .perform(
            delete("/aldebaran/wods/" + fran.getId())
                .with(csrf())
                .header("X-Auth-User-Id", "20")
                .header("X-Auth-User-Role", "USER")
                .header("X-Auth-Memberships", "101:ATHLETE:")
                .header("X-Auth-Actor-Id", "1")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isForbidden());

    // Admin acting as the gym owner: capabilities follow the owner's rights
    mockMvc
        .perform(
            get("/aldebaran/wods/" + fran.getId())
                .header("X-Auth-User-Id", "50")
                .header("X-Auth-User-Role", "USER")
                .header("X-Auth-Memberships", "101:OWNER:WOD_WRITE+SCORE_VERIFY")
                .header("X-Auth-Actor-Id", "1")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.capabilities.canModify").value(true));
  }
}
