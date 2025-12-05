package com.soundwrapped.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tokens")
public class Token {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(unique = true, length = 2000)
	private String accessToken;

	@Column(unique = true, length = 2000)
	private String refreshToken;

	@Column(name = "expires_at")
	private LocalDateTime expiresAt;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	public Token() {
		this.createdAt = LocalDateTime.now();
	}

	public Token(String accessToken, String refreshToken) {
		this.accessToken = accessToken;
		this.refreshToken = refreshToken;
		this.createdAt = LocalDateTime.now();
		// Default expiration: SoundCloud tokens typically expire in 12 hours
		// We'll refresh proactively after 10 hours to be safe
		this.expiresAt = LocalDateTime.now().plusHours(10);
	}

	public Token(String accessToken, String refreshToken, int expiresInSeconds) {
		this.accessToken = accessToken;
		this.refreshToken = refreshToken;
		this.createdAt = LocalDateTime.now();
		// Set expiration time based on expires_in from SoundCloud
		// Refresh proactively 1 hour before expiration
		int refreshBeforeSeconds = Math.max(3600, expiresInSeconds - 3600);
		this.expiresAt = LocalDateTime.now().plusSeconds(refreshBeforeSeconds);
	}

	public Long getId() {
		return id;
	}

	public String getAccessToken() {
		return accessToken;
	}

	public void setAccessToken(String accessToken) {
		this.accessToken = accessToken;
	}

	public String getRefreshToken() {
		return refreshToken;
	}

	public void setRefreshToken(String refreshToken) {
		this.refreshToken = refreshToken;
	}

	public LocalDateTime getExpiresAt() {
		return expiresAt;
	}

	public void setExpiresAt(LocalDateTime expiresAt) {
		this.expiresAt = expiresAt;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	/**
	 * Check if the token is expired or will expire soon (within 1 hour)
	 */
	public boolean isExpiredOrExpiringSoon() {
		if (expiresAt == null) {
			// If no expiration set, assume it might be expired (conservative approach)
			return true;
		}
		return LocalDateTime.now().isAfter(expiresAt.minusHours(1));
	}

	/**
	 * Check if the token is fully expired
	 */
	public boolean isExpired() {
		if (expiresAt == null) {
			return true;
		}
		return LocalDateTime.now().isAfter(expiresAt);
	}
}