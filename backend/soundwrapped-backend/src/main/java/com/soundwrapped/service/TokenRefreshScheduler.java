package com.soundwrapped.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Scheduled service to proactively refresh SoundCloud access tokens
 * before they expire. This ensures users remain authenticated even
 * during periods of inactivity.
 */
@Service
@ConditionalOnProperty(name = "soundwrapped.token-refresh.enabled", havingValue = "true", matchIfMissing = true)
public class TokenRefreshScheduler {

	private final SoundWrappedService soundWrappedService;
	private final TokenStore tokenStore;

	public TokenRefreshScheduler(SoundWrappedService soundWrappedService, TokenStore tokenStore) {
		this.soundWrappedService = soundWrappedService;
		this.tokenStore = tokenStore;
	}

	/**
	 * Check and refresh tokens every hour if they're expiring soon.
	 * This runs proactively to prevent token expiration during inactivity.
	 * Initial delay of 5 minutes allows the application to fully start and
	 * ensures tokens are loaded from the database before checking.
	 */
	@Scheduled(fixedRate = 3600000, initialDelay = 300000) // Every hour (3600000 ms), start after 5 minutes (300000 ms)
	public void refreshTokensIfNeeded() {
		try {
			// Check if there are any tokens in the database first
			if (!tokenStore.getToken().isPresent()) {
				// No tokens in database - this is normal if user hasn't authenticated yet
				// Only log at debug level to avoid confusion
				return;
			}
			
			if (tokenStore.needsRefresh()) {
				String refreshToken = tokenStore.getRefreshToken();
				if (refreshToken != null && !refreshToken.isBlank()) {
					System.out.println("[TokenRefreshScheduler] 🔄 Proactively refreshing access token...");
					soundWrappedService.refreshAccessToken(refreshToken);
					System.out.println("[TokenRefreshScheduler] ✅ Token refreshed successfully");
				} else {
					System.out.println("[TokenRefreshScheduler] ⚠️ No refresh token available - user needs to re-authenticate");
				}
			} else {
				System.out.println("[TokenRefreshScheduler] ✓ Token is still valid, no refresh needed");
			}
		} catch (Exception e) {
			System.err.println("[TokenRefreshScheduler] ❌ Error refreshing token: " + e.getMessage());
			e.printStackTrace();
		}
	}
}

