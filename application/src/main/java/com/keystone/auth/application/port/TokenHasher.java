package com.keystone.auth.application.port;

import com.keystone.auth.domain.model.TokenHash;

/**
 * One-way hash of a refresh-token plaintext. Persistence stores only the hash; a database leak
 * therefore does not yield usable tokens (see ADR-0005).
 */
public interface TokenHasher {

  TokenHash sha256(String plaintext);
}
