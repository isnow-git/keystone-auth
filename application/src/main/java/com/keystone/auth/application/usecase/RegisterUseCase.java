package com.keystone.auth.application.usecase;

import com.keystone.auth.application.port.PasswordHasher;
import com.keystone.auth.application.port.UserRepository;
import com.keystone.auth.domain.model.Email;
import com.keystone.auth.domain.model.User;
import java.time.Clock;
import java.util.Objects;

/**
 * Registers a new principal.
 *
 * <p>Sequence:
 *
 * <ol>
 *   <li>Validate the email (delegated to the {@link Email} value object).
 *   <li>Validate the password against the policy ({@link PasswordPolicy}).
 *   <li>Hash the password via the {@link PasswordHasher} port.
 *   <li>Attempt insertion via {@link UserRepository#insertIfAbsent}. The repository owns the
 *       uniqueness contract; we trust its boolean return rather than reading state twice.
 * </ol>
 *
 * <p>Failures are returned as a sealed {@link RegistrationResult}; exceptions remain reserved for
 * genuine bugs.
 */
public final class RegisterUseCase {

  private final UserRepository userRepository;
  private final PasswordHasher passwordHasher;
  private final Clock clock;

  public RegisterUseCase(
      UserRepository userRepository, PasswordHasher passwordHasher, Clock clock) {
    this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
    this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * Execute the registration flow.
   *
   * @param rawEmail raw email submitted by the client
   * @param rawPassword raw password submitted by the client
   * @return one of {@link RegistrationResult.Success}, {@link RegistrationResult.InvalidEmail},
   *     {@link RegistrationResult.WeakPassword}, {@link RegistrationResult.EmailAlreadyTaken}
   */
  public RegistrationResult execute(String rawEmail, String rawPassword) {
    Email email;
    try {
      email = Email.of(rawEmail);
    } catch (RuntimeException e) {
      return new RegistrationResult.InvalidEmail(e.getMessage());
    }

    var policyResult = PasswordPolicy.validate(rawPassword);
    if (policyResult instanceof PasswordPolicy.Invalid invalid) {
      return new RegistrationResult.WeakPassword(invalid.reason());
    }

    var hash = passwordHasher.hash(rawPassword);
    var user = User.register(email, hash, clock.instant());

    if (!userRepository.insertIfAbsent(user)) {
      return new RegistrationResult.EmailAlreadyTaken();
    }
    return new RegistrationResult.Success(user);
  }
}
