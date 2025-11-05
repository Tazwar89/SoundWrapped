package com.soundwrapped.controller;

import com.soundwrapped.service.ActivityTrackingService;
import com.soundwrapped.service.SoundWrappedService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for receiving system-level media playback events.
 * This allows desktop apps and browser extensions to send real-time
 * SoundCloud playback data detected via system APIs (similar to Music Presence).
 */
@RestController
@RequestMapping("/api/tracking")
@CrossOrigin(origins = "*", allowCredentials = "false") // Allow browser extension requests
public class SystemPlaybackController {

    private final ActivityTrackingService activityTrackingService;
    private final SoundWrappedService soundWrappedService;

    public SystemPlaybackController(
            ActivityTrackingService activityTrackingService,
            SoundWrappedService soundWrappedService) {
        this.activityTrackingService = activityTrackingService;
        this.soundWrappedService = soundWrappedService;
    }

    /**
     * Receive system-level playback event (from desktop app or browser extension)
     * 
     * Expected payload:
     * {
     *   "trackId": "soundcloud-track-id",
     *   "artist": "Artist Name",
     *   "title": "Track Title",
     *   "durationMs": 180000,
     *   "playbackPositionMs": 45000,
     *   "isPlaying": true,
     *   "source": "desktop-app" | "browser-extension",
     *   "platform": "windows" | "macos" | "linux"
     * }
     */
    @PostMapping("/system-playback")
    public ResponseEntity<Map<String, Object>> trackSystemPlayback(
            @RequestBody Map<String, Object> playbackEvent) {
        try {
            System.out.println("[SystemPlayback] Received playback event: " + playbackEvent);
            
            // Get current user ID from profile
            Map<String, Object> profile = soundWrappedService.getUserProfile();
            String userId = String.valueOf(profile.getOrDefault("id", "unknown"));
            
            System.out.println("[SystemPlayback] User ID: " + userId);
            
            // Extract track information
            String trackId = (String) playbackEvent.getOrDefault("trackId", "");
            Object durationObj = playbackEvent.get("durationMs");
            Long durationMs = durationObj != null ? 
                ((Number) durationObj).longValue() : 0L;
            
            String title = (String) playbackEvent.getOrDefault("title", "Unknown Track");
            String artist = (String) playbackEvent.getOrDefault("artist", "Unknown Artist");
            
            System.out.println("[SystemPlayback] Track: " + title + " by " + artist);
            System.out.println("[SystemPlayback] Track ID: " + trackId);
            System.out.println("[SystemPlayback] Duration: " + durationMs + "ms (" + (durationMs / 1000) + "s)");
            
            // Track the play event
            if (!trackId.isEmpty()) {
                activityTrackingService.trackPlay(userId, trackId, durationMs);
                System.out.println("[SystemPlayback] ✅ Successfully tracked play event");
            } else {
                System.out.println("[SystemPlayback] ⚠️ Empty track ID, skipping");
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Playback event tracked");
            response.put("trackId", trackId);
            response.put("userId", userId);
            response.put("durationMs", durationMs);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            System.err.println("[SystemPlayback] ❌ Error tracking playback: " + e.getMessage());
            e.printStackTrace();
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to track playback event: " + e.getMessage());
            error.put("error", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Receive system-level like event (from browser extension monitoring SoundCloud web)
     */
    @PostMapping("/system-like")
    public ResponseEntity<Map<String, Object>> trackSystemLike(
            @RequestParam String trackId) {
        try {
            Map<String, Object> profile = soundWrappedService.getUserProfile();
            String userId = String.valueOf(profile.getOrDefault("id", "unknown"));
            
            activityTrackingService.trackLike(userId, trackId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Like event tracked");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to track like event: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Health check endpoint for desktop apps/extensions
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "healthy");
        response.put("service", "System Playback Tracking");
        response.put("version", "1.0.0");
        return ResponseEntity.ok(response);
    }
}
