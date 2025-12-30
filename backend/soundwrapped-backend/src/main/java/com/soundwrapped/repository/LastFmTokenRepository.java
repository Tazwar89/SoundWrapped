package com.soundwrapped.repository;

import com.soundwrapped.entity.LastFmToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LastFmTokenRepository extends JpaRepository<LastFmToken, Long> {
    Optional<LastFmToken> findBySoundcloudUserId(String soundcloudUserId);
    Optional<LastFmToken> findByLastFmUsername(String lastFmUsername);
    void deleteBySoundcloudUserId(String soundcloudUserId);
}

