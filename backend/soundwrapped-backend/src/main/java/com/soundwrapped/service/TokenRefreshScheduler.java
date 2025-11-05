package com.soundwrapped.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled task service to proactively refresh SoundCloud OAuth tokens
 * before they expire to prevent authentication issues.
 * 
 * SoundCloud access tokens typically expire after 12-24 hours.
 * This service refreshes them every 6 hours to ensure they remain valid.
 * 
 * @author Tazwar Sikder
 */
@Component
public class TokenRefreshScheduler {
    
    private static final Logger logger = LoggerFactory.getLogger(TokenRefreshScheduler.class);
    
    private final SoundWrappedService soundWrappedService;
    private final TokenStore tokenStore;
    
    public TokenRefreshScheduler(SoundWrappedService soundWrappedService, TokenStore tokenStore) {
        this.soundWrappedService = soundWrappedService;
        this.tokenStore = tokenStore;
    }
    
    /**
     * Proactively refresh access token every 6 hours.
     * Runs at fixed rate: every 6 hours (21600000 milliseconds).
     * 
     * Schedule: runs every 6 hours starting 1 hour after application startup
     * to give time for initial authentication.
     */
    @Scheduled(fixedRate = 21600000, initialDelay = 3600000) // 6 hours, 1 hour initial delay
    public void refreshTokenPeriodically() {
        try {
            String refreshToken = tokenStore.getRefreshToken();
            String accessToken = tokenStore.getAccessToken();
            
            // Only refresh if we have tokens
            if (refreshToken == null || refreshToken.isBlank()) {
                logger.debug("No refresh token available - skipping scheduled refresh");
                return;
            }
            
            if (accessToken == null || accessToken.isBlank()) {
                logger.debug("No access token available - skipping scheduled refresh");
                return;
            }
            
            logger.info("🔄 Starting proactive token refresh...");
            
            // Attempt to refresh the token
            try {
                soundWrappedService.refreshAccessToken(refreshToken);
                logger.info("✅ Token refreshed successfully via scheduled task");
            } catch (Exception e) {
                logger.warn("⚠️ Scheduled token refresh failed: {}", e.getMessage());
                // Don't throw - this is a background task, we'll try again next cycle
            }
            
        } catch (Exception e) {
            logger.error("❌ Error in scheduled token refresh task: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Verify token validity by making a test API call.
     * Runs every 12 hours to check if tokens are still valid.
     * If invalid, attempts to refresh them.
     */
    @Scheduled(fixedRate = 43200000, initialDelay = 7200000) // 12 hours, 2 hour initial delay
    public void verifyTokenValidity() {
        try {
            String accessToken = tokenStore.getAccessToken();
            String refreshToken = tokenStore.getRefreshToken();
            
            if (accessToken == null || accessToken.isBlank()) {
                logger.debug("No access token to verify");
                return;
            }
            
            if (refreshToken == null || refreshToken.isBlank()) {
                logger.debug("No refresh token available for verification");
                return;
            }
            
            logger.info("🔍 Verifying token validity...");
            
            // Make a lightweight API call to verify token is still valid
            try {
                soundWrappedService.getUserProfile();
                logger.info("✅ Token is still valid");
            } catch (Exception e) {
                logger.warn("⚠️ Token appears to be invalid or expired: {}", e.getMessage());
                logger.info("🔄 Attempting to refresh token...");
                
                try {
                    soundWrappedService.refreshAccessToken(refreshToken);
                    logger.info("✅ Token refreshed after verification failure");
                } catch (Exception refreshError) {
                    logger.error("❌ Failed to refresh token after verification: {}", refreshError.getMessage());
                }
            }
            
        } catch (Exception e) {
            logger.error("❌ Error in token verification task: {}", e.getMessage(), e);
        }
    }
}

