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
    @Index(name = "idx_activity_type_date", columnList = "activityType,createdAt"),
    @Index(name = "idx_source", columnList = "source")
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

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ActivitySource source = ActivitySource.INAPP;

    @Column
    private String matchedSoundCloudTrackId; // SoundCloud track ID when source is LASTFM

    @Column
    private String lastFmArtist; // Original artist name from Last.fm

    @Column
    private String lastFmTrack; // Original track name from Last.fm

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
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

    public ActivitySource getSource() {
        return source;
    }

    public void setSource(ActivitySource source) {
        this.source = source;
    }

    public String getMatchedSoundCloudTrackId() {
        return matchedSoundCloudTrackId;
    }

    public void setMatchedSoundCloudTrackId(String matchedSoundCloudTrackId) {
        this.matchedSoundCloudTrackId = matchedSoundCloudTrackId;
    }

    public String getLastFmArtist() {
        return lastFmArtist;
    }

    public void setLastFmArtist(String lastFmArtist) {
        this.lastFmArtist = lastFmArtist;
    }

    public String getLastFmTrack() {
        return lastFmTrack;
    }

    public void setLastFmTrack(String lastFmTrack) {
        this.lastFmTrack = lastFmTrack;
    }

    public enum ActivityType {
        PLAY,
        LIKE,
        REPOST,
        SHARE
    }

    public enum ActivitySource {
        INAPP,   // Activity tracked via the SoundWrapped app or browser extension
        LASTFM   // Activity synced from Last.fm scrobbles
    }
}
