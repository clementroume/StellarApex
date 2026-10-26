package apex.stellar.antares.config;

import apex.stellar.antares.model.SupportSession;
import apex.stellar.antares.model.User;
import java.io.Serial;
import lombok.Getter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

/**
 * Authentication of a request made through an admin support session.
 *
 * <p>The principal (and authorities) are the <b>target</b> user's, so every rule applies exactly
 * as for that user; the acting admin and the session are kept for auditing, the gateway headers
 * and {@code /users/me}.
 */
@Getter
public class SupportSessionAuthentication extends UsernamePasswordAuthenticationToken {

  @Serial private static final long serialVersionUID = 1L;

  private final transient User actor;
  private final transient SupportSession session;

  /**
   * Creates an authenticated support session token.
   *
   * @param target the user the admin acts as.
   * @param actor the acting admin.
   * @param session the active support session.
   */
  public SupportSessionAuthentication(User target, User actor, SupportSession session) {
    super(target, null, target.getAuthorities());
    this.actor = actor;
    this.session = session;
  }
}
