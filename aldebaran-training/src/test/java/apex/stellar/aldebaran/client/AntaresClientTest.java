package apex.stellar.aldebaran.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import apex.stellar.aldebaran.exception.AntaresUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AntaresClientTest {

  private static final String URL = "http://antares/antares/internal/gyms/100/members/99";

  private MockRestServiceServer server;
  private AntaresClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    client = new AntaresClient(builder, "http://antares", "s3cret");
  }

  @Test
  @DisplayName("isGymMember: sends the internal secret and reads the answer")
  void isGymMember_ReadsAnswer() {
    server
        .expect(requestTo(URL))
        .andExpect(header("X-Internal-Secret", "s3cret"))
        .andRespond(withSuccess("{\"member\":true}", MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(URL))
        .andRespond(withSuccess("{\"member\":false}", MediaType.APPLICATION_JSON));

    assertTrue(client.isGymMember(100L, 99L));
    assertFalse(client.isGymMember(100L, 99L));
    server.verify();
  }

  @Test
  @DisplayName("isGymMember: an error from Antares is never read as an answer")
  void isGymMember_Error_Throws() {
    server.expect(requestTo(URL)).andRespond(withServerError());

    assertThrows(AntaresUnavailableException.class, () -> client.isGymMember(100L, 99L));
  }

  @Test
  @DisplayName("isProgrammingGym: reads the gym type, an unknown gym is an error")
  void isProgrammingGym() {
    server
        .expect(requestTo("http://antares/antares/internal/gyms/100"))
        .andExpect(header("X-Internal-Secret", "s3cret"))
        .andRespond(withSuccess("{\"programming\":true}", MediaType.APPLICATION_JSON));
    server
        .expect(requestTo("http://antares/antares/internal/gyms/101"))
        .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
            .withResourceNotFound());

    assertTrue(client.isProgrammingGym(100L));
    assertThrows(AntaresUnavailableException.class, () -> client.isProgrammingGym(101L));
  }
}
