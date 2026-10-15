package com.soprahr.interne.dto;

import java.time.Instant;

public record LoginResponse(String token, String tokenType, Instant expiresAt, UserProfileResponse user) {
}
