package com.soundwrapped.controller;

import com.soundwrapped.service.SoundCloudService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SoundWrappedController 
 * 
 * @author Tazwar Sikder
 */
@RestController
public class SoundWrappedController {
	private final SoundCloudService soundCloudService;

	public SoundWrappedController(SoundCloudService soundCloudService) {
		this.soundCloudService = soundCloudService;
	}

	private int calculateAccountAgeYears(String createdAt) {
		//SoundCloud createdAt format example: "2013/03/23 14:58:27 +0000"
		SimpleDateFormat original = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss Z");
		Date parsedDate = null;

		try {
			parsedDate = original.parse(createdAt);
		}

		catch (ParseException pe) {
			return 0;
		}

		//Formatted convention for locale-based ZonedDateTime
		SimpleDateFormat formatted = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
		String formattedDate = formatted.format(parsedDate);
		ZonedDateTime createdDate = ZonedDateTime.parse((CharSequence) formattedDate);
		ZonedDateTime now = ZonedDateTime.now();

		return now.getYear() - createdDate.getYear();
	}
	
	/**
	 * getWrappedSummary 
	 * d
	 * 
	 * List<Map<String, Object>> is used throughout this class because each element of
	 * the List contains metadata for individual tracks, i.e. each track has a Map
	 * including key-value information such as title: Song A, duration: 35000 sec, etc.
	 * 
	 * @param accessToken an access token for user authentication
	 * @return            a Map containing all essential Wrapped statistics
	 */
	@GetMapping("/api/soundcloud/wrapped")
	public Map<String, Object> getWrappedSummary(@RequestParam String accessToken) {
		Map<String, Object> profile = soundCloudService.getUserProfile(accessToken);
		Map<String, Object> wrapped = new HashMap<>();
		wrapped.put("username", profile.get("username"));
		wrapped.put("fullName", profile.get("full_name"));
		wrapped.put("followers", profile.get("followers_count"));
		wrapped.put("following", profile.get("followings_count"));
		wrapped.put("likesGiven", profile.get("public_favorites_count"));
		wrapped.put("reposts", profile.get("reposts_count"));
		wrapped.put("tracksUploaded", profile.get("track_count"));
		wrapped.put("playlistsCreated", profile.get("playlist_count"));
		wrapped.put("commentsPosted", profile.get("comments_count"));
		wrapped.put("remainingUploadQuotaSeconds", profile.get("upload_seconds_left"));

		//Account age in years
		String createdAt = (String) profile.get("created_at");

		if (createdAt != null) {
			wrapped.put("accountAgeYears", calculateAccountAgeYears(createdAt));
		}

		//Total listening time (if track durations are available)
		List<Map<String, Object>> tracks = soundCloudService.getUserTracks(accessToken);
		long totalDurationMs = tracks.stream()
				.mapToLong(track -> ((Number) track.getOrDefault("duration", 0))
				.longValue()).sum();
		double totalHours = totalDurationMs / 1000.0 / 60.0 / 60.0; // milliseconds -> seconds -> minutes -> hours
		wrapped.put("totalListeningHours", totalHours);
		
		// Example "fun insights"
		int followers = (int) profile.getOrDefault("followers_count", 0);
		int following = (int) profile.getOrDefault("followings_count", 0);

		if (following > 0) {
			wrapped.put("followerToFollowingRatio", (double) followers / following);
		}

		int numTracks = (int) profile.getOrDefault("track_count", 0);
		int likes = (int) profile.getOrDefault("public_favorites_count", 0);

		if (numTracks > 0) {
			wrapped.put("avgLikesPerTrack", (double) likes / numTracks);
		}

		wrapped.put("funFact", followers > 1000 ? "You're pretty famous! 🎉" : "Every star starts small 🥹");

		//Top 5 tracks by play count
		List<Map<String, Object>> topTracks = tracks.stream()
				.sorted((a, b) -> Long.compare(
						((Number) b.getOrDefault("playback_count", 0)).longValue(),
						((Number) a.getOrDefault("playback_count", 0)).longValue()))
				.limit(5)
				.collect(Collectors.toList());
		wrapped.put("topTracks", topTracks);

		// Top 5 playlists by likes
		List<Map<String, Object>> playlists = soundCloudService.getUserPlaylists(accessToken);
		List<Map<String, Object>> topPlaylists = playlists.stream()
				.sorted((a, b) -> Long.compare(((Number) b.getOrDefault("likes_count", 0))
				.longValue(), ((Number) a.getOrDefault("likes_count", 0))
				.longValue())).limit(5).collect(Collectors.toList());
		wrapped.put("topPlaylists", topPlaylists);

		return wrapped;
    }
}