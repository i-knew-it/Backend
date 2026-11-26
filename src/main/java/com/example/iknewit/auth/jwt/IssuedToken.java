package com.example.iknewit.auth.jwt;

import java.time.Instant;

public record IssuedToken(String token, Instant expiresAt) {
}
