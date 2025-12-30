package com.soundwrapped.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity to store Last.fm authentication tokens for scrobbling integration.
 * Users connect their Last.fm account to enable automatic tracking of SoundCloud plays.
 */
@Entity
@Table(name = "lastfm_tokens", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"soundcloud_user_id"})
})
public class LastFmToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String soundcloudUserId;

    @Column(nullable = false)
    private String lastFmUsername;

    @Column(nullable = false, length = 500)
    private String sessionKey; // Last.fm session key for authenticated API calls

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime lastSyncAt; // Last time we synced scrobbles from Last.fm

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (lastSyncAt == null) {
            lastSyncAt = LocalDateTime.now().minusDays(1); // Start from 1 day ago
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

    public String getLastFmUsername() {
        return lastFmUsername;
    }

    public void setLastFmUsername(String lastFmUsername) {
        this.lastFmUsername = lastFmUsername;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public void setSessionKey(String sessionKey) {
        this.sessionKey = sessionKey;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastSyncAt() {
        return lastSyncAt;
    }

    public void setLastSyncAt(LocalDateTime lastSyncAt) {
        this.lastSyncAt = lastSyncAt;
    }
}

