package com.soundwrapped.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity to track user activity within the SoundWrapped application.
 * This allows us to build analytics by collecting data over time, since
 * SoundCloud API doesn't provide listening history.
 */
@Entity
@Table(name = "user_activities", indexes = {
    @Index(name = "idx_user_track", columnList = "soundcloudUserId,trackId"),
    @Index(name = "idx_activity_type_date", columnList = "activityType,createdAt")
})
public class UserActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String soundcloudUserId;

    @Column(nullable = false)
    private String trackId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ActivityType activityType;

    @Column
    private Long playDurationMs; // For play events, duration in milliseconds

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSoundcloudUserId() {
        return soundcloudUserId;
    }

    public void setSoundcloudUserId(String soundcloudUserId) {
        this.soundcloudUserId = soundcloudUserId;
    }

    public String getTrackId() {
        return trackId;
    }

    public void setTrackId(String trackId) {
        this.trackId = trackId;
    }

    public ActivityType getActivityType() {
        return activityType;
    }

    public void setActivityType(ActivityType activityType) {
        this.activityType = activityType;
    }

    public Long getPlayDurationMs() {
        return playDurationMs;
    }

    public void setPlayDurationMs(Long playDurationMs) {
        this.playDurationMs = playDurationMs;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public enum ActivityType {
        PLAY,       // User played a track in-app
        LIKE,       // User liked a track (in-app)
        REPOST,     // User reposted a track (in-app)
        SHARE       // User shared a track (in-app)
    }
}
