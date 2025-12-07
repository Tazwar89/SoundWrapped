package com.soundwrapped.service;

import com.soundwrapped.entity.UserActivity;
import com.soundwrapped.repository.UserActivityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for tracking user activity detected by the browser extension.
 * The extension monitors SoundCloud.com playback and sends events to this service.
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
     * Track a play event when the browser extension detects playback on SoundCloud.com
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
     * Track a like event when the browser extension detects a like on SoundCloud.com
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
     * Track a repost event when the browser extension detects a repost on SoundCloud.com
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
     * Track a share event when the browser extension detects a share on SoundCloud.com
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
     * Get total play count for a user (tracked by browser extension from SoundCloud.com)
     * within the specified date range
     */
    public long getTotalPlays(String soundcloudUserId, LocalDateTime startDate, LocalDateTime endDate) {
        List<UserActivity> plays = activityRepository.findBySoundcloudUserIdAndActivityTypeAndCreatedAtBetween(
            soundcloudUserId,
            UserActivity.ActivityType.PLAY,
            startDate,
            endDate
        );
        return plays.size();
    }

    /**
     * Get total listening time in milliseconds (tracked by browser extension from SoundCloud.com)
     * within the specified date range
     */
    public long getTotalListeningTimeMs(String soundcloudUserId, LocalDateTime startDate, LocalDateTime endDate) {
        Long totalMs = activityRepository.getTotalPlayDurationMs(soundcloudUserId, startDate, endDate);
        return totalMs != null ? totalMs : 0L;
    }

    /**
     * Get total likes tracked by browser extension from SoundCloud.com
     * within the specified date range
     */
    public long getTotalLikes(String soundcloudUserId, LocalDateTime startDate, LocalDateTime endDate) {
        List<UserActivity> likes = activityRepository.findBySoundcloudUserIdAndActivityTypeAndCreatedAtBetween(
            soundcloudUserId,
            UserActivity.ActivityType.LIKE,
            startDate,
            endDate
        );
        return likes.size();
    }
}
