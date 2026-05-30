package com.keystone.auth.application.usecase;

import com.keystone.auth.domain.model.TokenPair;
import com.keystone.auth.domain.model.UserId;

/**
 * Outcome of {@link LoginUseCase#execute}. The {@link InvalidCredentials} branch is used for
 * <em>both</em> "unknown email" and "wrong password" so the REST adapter can map either to the same
 * response without leaking which one applied (no account enumeration).
 */
public sealed interface LoginResult {

  record Success(UserId userId, TokenPair tokens) implements LoginResult {}

  record InvalidCredentials() implements LoginResult {}
}
