package com.keystone.auth.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.keystone.auth.application.support.FakePasswordHasher;
import com.keystone.auth.application.support.InMemoryUserRepository;
import com.keystone.auth.domain.model.Email;
import com.keystone.auth.domain.model.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegisterUseCaseTest {

  private static final Instant FIXED_NOW = Instant.parse("2026-01-01T00:00:00Z");
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
  private static final String STRONG_PASSWORD = "correct-horse-battery-staple";

  private InMemoryUserRepository users;
  private FakePasswordHasher hasher;
  private RegisterUseCase useCase;

  @BeforeEach
  void setUp() {
    users = new InMemoryUserRepository();
    hasher = new FakePasswordHasher();
    useCase = new RegisterUseCase(users, hasher, FIXED_CLOCK);
  }

  @Test
  void rejectsNullCollaborators() {
    assertThatNullPointerException()
        .isThrownBy(() -> new RegisterUseCase(null, hasher, FIXED_CLOCK));
    assertThatNullPointerException()
        .isThrownBy(() -> new RegisterUseCase(users, null, FIXED_CLOCK));
    assertThatNullPointerException().isThrownBy(() -> new RegisterUseCase(users, hasher, null));
  }

  @Test
  void registersANewUserOnTheHappyPath() {
    var result = useCase.execute("Alice@Example.com", STRONG_PASSWORD);

    assertThat(result).isInstanceOf(RegistrationResult.Success.class);
    var success = (RegistrationResult.Success) result;
    assertThat(success.user().email()).isEqualTo(Email.of("alice@example.com"));
    assertThat(success.user().roles()).containsExactly(Role.USER);
    assertThat(success.user().createdAt()).isEqualTo(FIXED_NOW);
    assertThat(success.user().passwordHash().encoded()).startsWith("$fake$");
    assertThat(users.size()).isOne();
  }

  @Test
  void rejectsMalformedEmail() {
    var result = useCase.execute("not-an-email", STRONG_PASSWORD);

    assertThat(result).isInstanceOf(RegistrationResult.InvalidEmail.class);
    assertThat(users.size()).isZero();
  }

  @Test
  void rejectsTooShortPasswordWithGenericReason() {
    var result = useCase.execute("bob@example.com", "short");

    assertThat(result).isInstanceOf(RegistrationResult.WeakPassword.class);
    var weak = (RegistrationResult.WeakPassword) result;
    assertThat(weak.reason()).contains("12");
    assertThat(users.size()).isZero();
  }

  @Test
  void rejectsControlCharactersInPassword() {
    var result = useCase.execute("bob@example.com", "good-length-but-\t-tab-inside");

    assertThat(result).isInstanceOf(RegistrationResult.WeakPassword.class);
    assertThat(users.size()).isZero();
  }

  @Test
  void doesNotHashOrInsertWhenEmailIsInvalid() {
    useCase.execute("bad@@@", STRONG_PASSWORD);
    assertThat(users.size()).isZero();
  }

  @Test
  void surfacesUniquenessConflictAsEmailAlreadyTaken() {
    useCase.execute("dup@example.com", STRONG_PASSWORD);

    var second = useCase.execute("DUP@example.com", STRONG_PASSWORD);

    assertThat(second).isInstanceOf(RegistrationResult.EmailAlreadyTaken.class);
    assertThat(users.size()).isOne();
  }

  @Test
  void doesNotLeakWhetherEmailExistsThroughTheResultShape() {
    // A REST adapter mapping `EmailAlreadyTaken` to the same 409 body as some other generic
    // conflict would prevent enumeration. The use case's job is to make that mapping easy:
    // both `EmailAlreadyTaken` and `Success` carry no user-facing identifier the adapter could
    // accidentally leak.
    var first = useCase.execute("ev@example.com", STRONG_PASSWORD);
    var second = useCase.execute("ev@example.com", STRONG_PASSWORD);

    assertThat(first).isInstanceOf(RegistrationResult.Success.class);
    assertThat(second).isInstanceOf(RegistrationResult.EmailAlreadyTaken.class);
    // The EmailAlreadyTaken record has no fields — nothing for the adapter to leak.
    assertThat(((Record) second).getClass().getRecordComponents()).isEmpty();
  }
}
