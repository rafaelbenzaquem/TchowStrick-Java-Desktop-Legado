package br.com.mss.tchow.app;

/**
 * Official account session stored separately from the local profile and match tokens. guestId is
 * the server-issued online identity; the local profile ID remains unchanged.
 */
public record StoredAccountSession(
        String token, String accountId, String guestId, long expiresAtEpochSeconds) {}
