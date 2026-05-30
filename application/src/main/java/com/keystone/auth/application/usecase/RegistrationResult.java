package com.keystone.auth.application.usecase;

import com.keystone.auth.domain.model.User;

/**
 * Outcome of {@link RegisterUseCase#execute}. Sealed so the REST adapter can exhaustively map each
 * branch to an HTTP status without an {@code else} branch swallowing future cases.
 */
public sealed interface RegistrationResult {

  /** Registration succeeded; the persisted {@link User} is attached for any follow-up wiring. */
  record Success(User user) implements RegistrationResult {}

  /** The provided email is malformed. The caller should map this to 400. */
  record InvalidEmail(String reason) implements RegistrationResult {}

  /**
   * The provided password does not meet the policy. The caller should map this to 422 with a
   * generic message — never echo the password back.
   */
  record WeakPassword(String reason) implements RegistrationResult {}

  /**
   * The email is already registered. The caller should map this to 409 with a generic message that
   * does <em>not</em> confirm whether the email belongs to an existing account (avoid enumeration).
   */
  record EmailAlreadyTaken() implements RegistrationResult {}
}
