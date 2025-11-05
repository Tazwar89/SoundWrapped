package com.soundwrapped.service;

import com.soundwrapped.entity.UserActivity;
import com.soundwrapped.repository.UserActivityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service for tracking user activity within the SoundWrapped application.
 * This allows us to build analytics by collecting data over time, since
 * SoundCloud API doesn't provide listening history.
 */
@Service
public class ActivityTrackingService {

    private final UserActivityRepository activityRepository;

    public ActivityTrackingService(UserActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    /**
     * Track a play event when user plays a track in-app
     */
    @Transactional
    public void trackPlay(String soundcloudUserId, String trackId, Long durationMs) {
        UserActivity activity = new UserActivity();
        activity.setSoundcloudUserId(soundcloudUserId);
        activity.setTrackId(trackId);
        activity.setActivityType(UserActivity.ActivityType.PLAY);
        activity.setPlayDurationMs(durationMs);
        activityRepository.save(activity);
    }

    /**
     * Track a like event when user likes a track in-app
     */
    @Transactional
    public void trackLike(String soundcloudUserId, String trackId) {
        UserActivity activity = new UserActivity();
        activity.setSoundcloudUserId(soundcloudUserId);
        activity.setTrackId(trackId);
        activity.setActivityType(UserActivity.ActivityType.LIKE);
        activityRepository.save(activity);
    }

    /**
     * Track a repost event when user reposts a track in-app
     */
    @Transactional
    public void trackRepost(String soundcloudUserId, String trackId) {
        UserActivity activity = new UserActivity();
        activity.setSoundcloudUserId(soundcloudUserId);
        activity.setTrackId(trackId);
        activity.setActivityType(UserActivity.ActivityType.REPOST);
        activityRepository.save(activity);
    }

    /**
     * Track a share event when user shares a track in-app
     */
    @Transactional
    public void trackShare(String soundcloudUserId, String trackId) {
        UserActivity activity = new UserActivity();
        activity.setSoundcloudUserId(soundcloudUserId);
        activity.setTrackId(trackId);
        activity.setActivityType(UserActivity.ActivityType.SHARE);
        activityRepository.save(activity);
    }

    /**
     * Get total play count for a user (in-app only)
     */
    public long getTotalPlays(String soundcloudUserId, LocalDateTime startDate, LocalDateTime endDate) {
        return activityRepository.countBySoundcloudUserIdAndActivityType(
            soundcloudUserId, 
            UserActivity.ActivityType.PLAY
        );
    }

    /**
     * Get total listening time in milliseconds (in-app only)
     */
    public long getTotalListeningTimeMs(String soundcloudUserId, LocalDateTime startDate, LocalDateTime endDate) {
        Long totalMs = activityRepository.getTotalPlayDurationMs(soundcloudUserId, startDate, endDate);
        return totalMs != null ? totalMs : 0L;
    }

    /**
     * Get total likes tracked in-app
     */
    public long getTotalLikes(String soundcloudUserId, LocalDateTime startDate, LocalDateTime endDate) {
        return activityRepository.countBySoundcloudUserIdAndActivityType(
            soundcloudUserId, 
            UserActivity.ActivityType.LIKE
        );
    }
}
