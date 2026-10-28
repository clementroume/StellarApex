package apex.stellar.antares.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Sends the account e-mails (password reset, e-mail verification) through the configured SMTP
 * server ({@code spring.mail.*}, Mailpit in local development).
 *
 * <p>Mails are {@link AccountMail} events: sent after the transaction commits and asynchronously,
 * so a request answers in the same time whether or not a mail goes out (no account enumeration by
 * timing) and an SMTP failure never fails the request. Failures are logged without the address or
 * the link. The token goes in the URL fragment ({@code #token=}), which browsers never send to a
 * server, so it stays out of proxy access logs and {@code Referer} headers.
 *
 * <p>Texts come from {@code messages*.properties} ({@code mail.*}), rendered in the recipient's
 * language into {@code templates/mail/account-action.html}, with a plain-text alternative. No
 * tracking: no image, no rewritten link.
 */
@Slf4j
@Service
public class AccountMailService {

  private final JavaMailSender mailSender;
  private final TemplateEngine templateEngine;
  private final MessageSource messageSource;
  private final String from;
  private final String frontendUrl;

  /**
   * Creates the service.
   *
   * @param mailSender The SMTP sender.
   * @param templateEngine Thymeleaf, for the HTML body.
   * @param messageSource The translated texts.
   * @param from The sender address ({@code application.mail.from}).
   * @param frontendUrl The Sirius base URL used in links.
   */
  public AccountMailService(
      JavaMailSender mailSender,
      TemplateEngine templateEngine,
      MessageSource messageSource,
      @Value("${application.mail.from}") String from,
      @Value("${application.frontend.url}") String frontendUrl) {
    this.mailSender = mailSender;
    this.templateEngine = templateEngine;
    this.messageSource = messageSource;
    this.from = from;
    this.frontendUrl = frontendUrl;
  }

  /**
   * Sends an account e-mail once the transaction that requested it commits (immediately outside a
   * transaction).
   *
   * @param mail The e-mail to send.
   */
  @Async
  @TransactionalEventListener(fallbackExecution = true)
  public void send(AccountMail mail) {
    try {
      mailSender.send(build(mail));
      log.info("Account e-mail {} sent", mail.kind());
    } catch (MailException | MessagingException e) {
      log.error("Account e-mail {} could not be sent: {}", mail.kind(), e.getMessage());
    }
  }

  /**
   * Builds the MIME message (HTML with a plain-text alternative).
   *
   * @param mail The e-mail to send.
   * @return The message.
   * @throws MessagingException If the message cannot be built.
   */
  MimeMessage build(AccountMail mail) throws MessagingException {
    Locale locale = Locale.forLanguageTag(mail.locale() == null ? "en" : mail.locale());
    String prefix = mail.kind().messagePrefix();
    String link = frontendUrl + mail.kind().path() + "#token=" + mail.token();
    long amount =
        mail.kind() == AccountMail.Kind.PASSWORD_RESET
            ? mail.validity().toMinutes()
            : mail.validity().toHours();
    String name = mail.firstName() == null ? "" : mail.firstName().trim();

    Context context = new Context(locale);
    context.setVariable("lang", locale.getLanguage());
    context.setVariable("subject", text(prefix + ".subject", locale));
    context.setVariable("greeting", text("mail.greeting", locale, name));
    context.setVariable("intro", text(prefix + ".intro", locale));
    context.setVariable("action", text(prefix + ".action", locale));
    context.setVariable("link", link);
    context.setVariable("expiry", text(prefix + ".expiry", locale, amount));
    context.setVariable("ignore", text(prefix + ".ignore", locale));
    context.setVariable("fallback", text("mail.linkFallback", locale));
    context.setVariable("signature", text("mail.signature", locale));

    String plain =
        String.join(
            "\n\n",
            (String) context.getVariable("greeting"),
            (String) context.getVariable("intro"),
            context.getVariable("action") + ":\n" + link,
            (String) context.getVariable("expiry"),
            (String) context.getVariable("ignore"),
            (String) context.getVariable("signature"));
    String html = templateEngine.process("mail/account-action", context);

    MimeMessage message = mailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
    helper.setFrom(from);
    helper.setTo(mail.to());
    helper.setSubject((String) context.getVariable("subject"));
    helper.setText(plain, html);
    return message;
  }

  private String text(String key, Locale locale, Object... args) {
    return messageSource.getMessage(key, args, locale);
  }
}
