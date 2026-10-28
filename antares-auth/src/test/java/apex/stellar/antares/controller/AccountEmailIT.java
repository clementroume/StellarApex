package apex.stellar.antares.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import apex.stellar.antares.config.BaseIntegrationTest;
import apex.stellar.antares.dto.AuthenticationRequest;
import apex.stellar.antares.dto.ForgotPasswordRequest;
import apex.stellar.antares.dto.GymRequest;
import apex.stellar.antares.dto.JoinGymRequest;
import apex.stellar.antares.dto.ProfileUpdateRequest;
import apex.stellar.antares.dto.RegisterRequest;
import apex.stellar.antares.dto.ResetPasswordRequest;
import apex.stellar.antares.dto.VerifyEmailRequest;
import apex.stellar.antares.model.Gym;
import apex.stellar.antares.model.Gym.GymStatus;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.jpa.GymRepository;
import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Password reset and e-mail verification flows, with the mails captured from the mock sender. */
class AccountEmailIT extends BaseIntegrationTest {

  private static final String PASSWORD = "password123";
  private static final Pattern TOKEN = Pattern.compile("#token=([A-Za-z0-9_-]+)");

  @Autowired private GymRepository gymRepository;

  @BeforeEach
  void cleanUsers() {
    userRepository.deleteAll(
        userRepository.findAll().stream()
            .filter(u -> !u.getPlatformRole().name().equals("ADMIN"))
            .toList());
  }

  // --- Password reset ---

  @Test
  @DisplayName("forgot: unknown address -> 202 and no mail (no account enumeration)")
  void forgot_unknownAddress() throws Exception {
    forgot("nobody@example.com").andExpect(status().isAccepted());

    verify(mailSender, after(500).never()).send(any(MimeMessage.class));
  }

  @Test
  @DisplayName("reset: mailed link sets the password once, ends sessions and verifies the address")
  void reset_fullFlow() throws Exception {
    registerUnverified("reset.me@example.com", PASSWORD);
    Cookie[] oldSession = login("reset.me@example.com", PASSWORD);
    // A token issued in the same second as the reset stays valid (JWT iat has 1 s precision)
    Thread.sleep(1100);

    forgot("reset.me@example.com").andExpect(status().isAccepted());
    // Verification mail at registration, then the reset mail
    String token = tokenOf(mail(2, "Reset your StellarApex password"));

    resetPassword(token, "brandNewPass1", "brandNewPass1").andExpect(status().isNoContent());

    // Old session: access token refused, refresh token deleted
    mockMvc
        .perform(get("/antares/users/me").cookie(oldSession))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(post("/antares/auth/refresh-token").cookie(oldSession))
        .andExpect(status().isNotFound());

    // New password works, old one does not, the address is now verified
    login("reset.me@example.com", "brandNewPass1");
    mockMvc
        .perform(
            post("/antares/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    jsonMapper.writeValueAsString(
                        new AuthenticationRequest("reset.me@example.com", PASSWORD))))
        .andExpect(status().isUnauthorized());
    assertThat(userRepository.findByEmail("reset.me@example.com").orElseThrow().isEmailVerified())
        .isTrue();

    // Single use
    resetPassword(token, "anotherPass1", "anotherPass1")
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail").value("This link is invalid or has expired. Ask for a new one."));
  }

  @Test
  @DisplayName("reset: a confirmation mismatch keeps the token usable")
  void reset_mismatchKeepsToken() throws Exception {
    register("typo@example.com", PASSWORD);
    forgot("typo@example.com").andExpect(status().isAccepted());
    // Verification mail at registration, then the reset mail
    String token = tokenOf(mail(2, "Reset your StellarApex password"));

    resetPassword(token, "brandNewPass1", "brandNewPass2").andExpect(status().isBadRequest());
    resetPassword(token, "brandNewPass1", "brandNewPass1").andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("reset: an unknown token -> 400")
  void reset_unknownToken() throws Exception {
    resetPassword("not-a-real-token", "brandNewPass1", "brandNewPass1")
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("forgot: the 6th request from one IP within the hour -> 429 with Retry-After")
  void forgot_throttledPerIp() throws Exception {
    for (int i = 0; i < 5; i++) {
      forgot("someone" + i + "@example.com").andExpect(status().isAccepted());
    }
    forgot("someone@example.com")
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"));
  }

  // --- E-mail verification ---

  @Test
  @DisplayName("verify: registration mails a link that verifies the address once")
  void verify_fullFlow() throws Exception {
    registerUnverified("new.athlete@example.com", PASSWORD);
    MimeMessage mail = lastMail(1);
    assertThat(mail.getSubject()).isEqualTo("Confirm your e-mail address");
    String token = tokenOf(mail);
    assertThat(isVerified("new.athlete@example.com")).isFalse();

    verifyEmail(token).andExpect(status().isNoContent());
    assertThat(isVerified("new.athlete@example.com")).isTrue();

    verifyEmail(token).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("register: the account and its verification mail take the language of sign-up")
  void register_inUserLanguage() throws Exception {
    mockMvc
        .perform(
            post("/antares/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    jsonMapper.writeValueAsString(
                        new RegisterRequest("Léa", "Martin", "lea@example.com", PASSWORD, "fr"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.locale").value("fr"));

    assertThat(lastMail(1).getSubject()).isEqualTo("Confirme ton adresse e-mail");

    mockMvc
        .perform(
            post("/antares/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    jsonMapper.writeValueAsString(
                        new RegisterRequest("X", "Y", "bad.locale@example.com", PASSWORD, "French"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("unverified: joining or requesting a gym -> 403 EMAIL_NOT_VERIFIED")
  void unverified_cannotJoinOrRequestGym() throws Exception {
    Gym gym =
        gymRepository.save(
            Gym.builder()
                .name("Verify Box")
                .status(GymStatus.ACTIVE)
                .enrollmentCode("VERIFY1")
                .isAutoSubscription(true)
                .build());
    registerUnverified("unverified@example.com", PASSWORD);
    Cookie[] cookies = login("unverified@example.com", PASSWORD);

    mockMvc
        .perform(
            post("/antares/gyms/join")
                .with(csrf())
                .cookie(cookies)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    jsonMapper.writeValueAsString(new JoinGymRequest(gym.getId(), "VERIFY1"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    mockMvc
        .perform(
            post("/antares/gyms")
                .with(csrf())
                .cookie(cookies)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(new GymRequest("Spam Box", "x", false))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    mockMvc
        .perform(get("/antares/users/me").cookie(cookies))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.emailVerified").value(false));
  }

  @Test
  @DisplayName("resend: a new link replaces the previous one; 4th resend in the hour -> 429")
  void resend_replacesAndThrottles() throws Exception {
    registerUnverified("resend@example.com", PASSWORD);
    String first = tokenOf(lastMail(1));
    Cookie[] cookies = login("resend@example.com", PASSWORD);

    resend(cookies).andExpect(status().isAccepted());
    String second = tokenOf(lastMail(2)); // sent after the first one was received
    resend(cookies).andExpect(status().isAccepted());
    resend(cookies).andExpect(status().isAccepted());
    resend(cookies).andExpect(status().isTooManyRequests());

    verifyEmail(first).andExpect(status().isBadRequest());
    assertThat(second).isNotEqualTo(first);
  }

  @Test
  @DisplayName("email change: the new address is unverified and gets its own link")
  void emailChange_requiresNewVerification() throws Exception {
    Cookie[] cookies = registerAndLogin("old.address@example.com", PASSWORD);
    assertThat(isVerified("old.address@example.com")).isTrue();

    mockMvc
        .perform(
            put("/antares/users/me/profile")
                .with(csrf())
                .cookie(cookies)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    jsonMapper.writeValueAsString(
                        new ProfileUpdateRequest("Test", "User", "new.address@example.com"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.emailVerified").value(false));

    // Registration mail to the old address, then this one (asynchronous: any order)
    ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender, timeout(5000).times(2)).send(captor.capture());
    MimeMessage mail =
        captor.getAllValues().stream()
            .filter(m -> recipient(m).equals("new.address@example.com"))
            .findFirst()
            .orElseThrow();
    verifyEmail(tokenOf(mail)).andExpect(status().isNoContent());
    assertThat(isVerified("new.address@example.com")).isTrue();
  }

  // --- Helpers ---

  private ResultActions forgot(String email) throws Exception {
    return mockMvc.perform(
        post("/antares/auth/password/forgot")
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonMapper.writeValueAsString(new ForgotPasswordRequest(email))));
  }

  private ResultActions resetPassword(
      String token, String password, String confirmation) throws Exception {
    return mockMvc.perform(
        post("/antares/auth/password/reset")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                jsonMapper.writeValueAsString(
                    new ResetPasswordRequest(token, password, confirmation))));
  }

  private ResultActions verifyEmail(String token)
      throws Exception {
    return mockMvc.perform(
        post("/antares/auth/email/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonMapper.writeValueAsString(new VerifyEmailRequest(token))));
  }

  private ResultActions resend(Cookie[] cookies)
      throws Exception {
    return mockMvc.perform(
        post("/antares/users/me/email/verification").with(csrf()).cookie(cookies));
  }

  private boolean isVerified(String email) {
    return userRepository.findByEmail(email).map(User::isEmailVerified).orElseThrow();
  }

  /** Waits for the n-th mail sent in this test and returns the last one. */
  private MimeMessage lastMail(int expectedCount) {
    ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender, timeout(5000).times(expectedCount)).send(captor.capture());
    return captor.getValue();
  }

  private static String recipient(MimeMessage message) {
    try {
      return message.getAllRecipients()[0].toString();
    } catch (MessagingException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Waits for the n mails of this test and returns the last one with this subject (mails are sent
   * asynchronously, so their order is not guaranteed).
   */
  private MimeMessage mail(int expectedCount, String subject) throws Exception {
    ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(mailSender, timeout(5000).times(expectedCount)).send(captor.capture());
    MimeMessage found = null;
    for (MimeMessage message : captor.getAllValues()) {
      if (subject.equals(message.getSubject())) {
        found = message;
      }
    }
    assertThat(found).as("mail '%s'", subject).isNotNull();
    return found;
  }

  private static String tokenOf(MimeMessage mail) throws Exception {
    Matcher matcher = TOKEN.matcher(textOf(mail));
    assertThat(matcher.find()).as("token link in the mail").isTrue();
    return matcher.group(1);
  }

  private static String textOf(Part part) throws Exception {
    Object content = part.getContent();
    if (content instanceof String text) {
      return text;
    }
    StringBuilder all = new StringBuilder();
    if (content instanceof Multipart multipart) {
      for (int i = 0; i < multipart.getCount(); i++) {
        BodyPart body = multipart.getBodyPart(i);
        all.append(textOf(body)).append('\n');
      }
    }
    return all.toString();
  }
}
