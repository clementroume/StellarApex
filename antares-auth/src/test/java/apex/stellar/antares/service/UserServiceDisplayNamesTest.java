package apex.stellar.antares.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;

import apex.stellar.antares.dto.DisplayNameResponse;
import apex.stellar.antares.model.Gym;
import apex.stellar.antares.model.Gym.GymStatus;
import apex.stellar.antares.model.GymRole;
import apex.stellar.antares.model.Membership;
import apex.stellar.antares.model.Membership.MembershipStatus;
import apex.stellar.antares.model.PlatformRole;
import apex.stellar.antares.model.User;
import apex.stellar.antares.repository.jpa.UserRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Visibility rules of {@link UserService#getDisplayNames}. */
@ExtendWith(MockitoExtension.class)
class UserServiceDisplayNamesTest {

  @Mock private UserRepository userRepository;
  @InjectMocks private UserService userService;

  private final Gym box = Gym.builder().id(10L).status(GymStatus.ACTIVE).build();
  private final Gym otherBox = Gym.builder().id(20L).status(GymStatus.ACTIVE).build();
  private final Map<Long, User> users = new java.util.HashMap<>();

  private User user(long id, String first, String last, Gym gym, MembershipStatus status) {
    User u =
        User.builder()
            .id(id)
            .firstName(first)
            .lastName(last)
            .platformRole(PlatformRole.USER)
            .memberships(new ArrayList<>())
            .build();
    if (gym != null) {
      u.getMemberships()
          .add(Membership.builder().user(u).gym(gym).gymRole(GymRole.ATHLETE).status(status).build());
    }
    users.put(id, u);
    return u;
  }

  @BeforeEach
  void repository() {
    lenient().when(userRepository.findWithMembershipsByIdIn(anyCollection()))
        .thenAnswer(
            inv -> {
              Collection<Long> ids = inv.getArgument(0);
              return ids.stream().map(users::get).filter(java.util.Objects::nonNull).toList();
            });
  }

  private Map<Long, String> names(User viewer, Long... ids) {
    List<DisplayNameResponse> result = userService.getDisplayNames(viewer, List.of(ids));
    return result.stream()
        .collect(Collectors.toMap(DisplayNameResponse::id, DisplayNameResponse::displayName));
  }

  @Test
  @DisplayName("Gym mates, opted-in users and oneself are visible, others are left out")
  void visibility() {
    User viewer = user(1, "Clément", "roume", box, MembershipStatus.ACTIVE);
    user(2, "Alice", "Martin", box, MembershipStatus.ACTIVE);
    user(3, "Bob", "Durand", otherBox, MembershipStatus.ACTIVE);
    User optedIn = user(4, "Chloé", "Petit", otherBox, MembershipStatus.ACTIVE);
    optedIn.setPublicLeaderboardName(true);
    user(5, "Dan", "Pending", box, MembershipStatus.PENDING);

    Map<Long, String> names = names(viewer, 1L, 2L, 3L, 4L, 5L, 99L);

    assertThat(names)
        .containsOnly(
            Map.entry(1L, "Clément R."), Map.entry(2L, "Alice M."), Map.entry(4L, "Chloé P."));
  }

  @Test
  @DisplayName("A platform admin sees every name; an empty request returns nothing")
  void admin() {
    User admin = user(1, "Ada", "Admin", null, null);
    admin.setPlatformRole(PlatformRole.ADMIN);
    user(3, "Bob", "Durand", otherBox, MembershipStatus.ACTIVE);

    assertThat(names(admin, 3L)).containsOnly(Map.entry(3L, "Bob D."));
    assertThat(userService.getDisplayNames(admin, List.of())).isEmpty();
  }

  @Test
  @DisplayName("Display name keeps the first name and the last-name initial only")
  void format() {
    assertThat(UserService.displayName(User.builder().firstName("Jean").lastName("dupont").build()))
        .isEqualTo("Jean D.");
    assertThat(UserService.displayName(User.builder().firstName("Mononyme").lastName(" ").build()))
        .isEqualTo("Mononyme");
  }
}
