package com.soundwrapped.controller;

import com.soundwrapped.service.ActivityTrackingService;
import com.soundwrapped.service.SoundWrappedService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for tracking user activity within the SoundWrapped app.
 * This allows us to build analytics by collecting data over time.
 */
@RestController
@RequestMapping("/api/activity")
public class ActivityTrackingController {

    private final ActivityTrackingService activityTrackingService;
    private final SoundWrappedService soundWrappedService;

    public ActivityTrackingController(
            ActivityTrackingService activityTrackingService,
            SoundWrappedService soundWrappedService) {
        this.activityTrackingService = activityTrackingService;
        this.soundWrappedService = soundWrappedService;
    }

    /**
     * Track a play event (when user plays a track in-app)
     */
    @PostMapping("/track/play")
    public ResponseEntity<Map<String, Object>> trackPlay(
            @RequestParam String trackId,
            @RequestParam(required = false) Long durationMs) {
        try {
            // Get current user ID from profile
            Map<String, Object> profile = soundWrappedService.getUserProfile();
            String userId = String.valueOf(profile.getOrDefault("id", "unknown"));
            
            activityTrackingService.trackPlay(userId, trackId, durationMs != null ? durationMs : 0L);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Play event tracked");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to track play event: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Track a like event (when user likes a track in-app)
     */
    @PostMapping("/track/like")
    public ResponseEntity<Map<String, Object>> trackLike(@RequestParam String trackId) {
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
     * Track a repost event (when user reposts a track in-app)
     */
    @PostMapping("/track/repost")
    public ResponseEntity<Map<String, Object>> trackRepost(@RequestParam String trackId) {
        try {
            Map<String, Object> profile = soundWrappedService.getUserProfile();
            String userId = String.valueOf(profile.getOrDefault("id", "unknown"));
            
            activityTrackingService.trackRepost(userId, trackId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Repost event tracked");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to track repost event: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
