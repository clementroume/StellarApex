package apex.stellar.aldebaran.controller;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import apex.stellar.aldebaran.config.BaseIntegrationTest;
import apex.stellar.aldebaran.controller.Exporter.CsvExportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Integration tests for {@link Exporter} security.
 *
 * <p>The export service is mocked so the tests never overwrite the developer's local seed CSV files.
 */
class ExporterIT extends BaseIntegrationTest {

  @MockitoBean private CsvExportService exportService;

  @Test
  @DisplayName("POST /export/muscles: should export when ADMIN")
  void testExportMuscles_AsAdmin_Success() throws Exception {
    mockMvc
        .perform(
            post("/aldebaran/export/muscles")
                .with(csrf())
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "ADMIN")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isOk());

    verify(exportService).exportMuscles();
  }

  @Test
  @DisplayName("POST /export/movements: should export when ADMIN")
  void testExportMovements_AsAdmin_Success() throws Exception {
    mockMvc
        .perform(
            post("/aldebaran/export/movements")
                .with(csrf())
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "ADMIN")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isOk());

    verify(exportService).exportMovements();
  }

  @Test
  @DisplayName("POST /export/muscles: should return 403 Forbidden when simple User")
  void testExportMuscles_AsUser_Forbidden() throws Exception {
    mockMvc
        .perform(
            post("/aldebaran/export/muscles")
                .with(csrf())
                .header("X-Auth-User-Id", "2")
                .header("X-Auth-User-Role", "USER")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isForbidden());

    verify(exportService, never()).exportMuscles();
  }

  @Test
  @DisplayName("POST /export/movements: should return 403 Forbidden for gym staff")
  void testExportMovements_AsGymOwner_Forbidden() throws Exception {
    mockMvc
        .perform(
            post("/aldebaran/export/movements")
                .with(csrf())
                .header("X-Auth-User-Id", "3")
                .header("X-Auth-User-Role", "USER")
                .header("X-Auth-Memberships", "12:OWNER:WOD_WRITE+SCORE_VERIFY")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isForbidden());

    verify(exportService, never()).exportMovements();
  }

  @Test
  @DisplayName("POST /export/wods: should export when ADMIN")
  void testExportWods_AsAdmin_Success() throws Exception {
    mockMvc
        .perform(
            post("/aldebaran/export/wods")
                .with(csrf())
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "ADMIN")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isOk());

    verify(exportService).exportWods();
  }

  @Test
  @DisplayName("POST /export/wods: should return 403 Forbidden for gym staff")
  void testExportWods_AsGymOwner_Forbidden() throws Exception {
    mockMvc
        .perform(
            post("/aldebaran/export/wods")
                .with(csrf())
                .header("X-Auth-User-Id", "3")
                .header("X-Auth-User-Role", "USER")
                .header("X-Auth-Memberships", "12:OWNER:WOD_WRITE+SCORE_VERIFY")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isForbidden());

    verify(exportService, never()).exportWods();
  }

  @Test
  @DisplayName("POST /export/wods: should return 403 Forbidden when admin acts as a user")
  void testExportWods_InSupportSession_Forbidden() throws Exception {
    mockMvc
        .perform(
            post("/aldebaran/export/wods")
                .with(csrf())
                .header("X-Auth-User-Id", "2")
                .header("X-Auth-User-Role", "USER")
                .header("X-Auth-Actor-Id", "1")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isForbidden());

    verify(exportService, never()).exportWods();
  }
}
