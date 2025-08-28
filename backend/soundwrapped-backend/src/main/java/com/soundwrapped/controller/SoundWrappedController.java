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

		String createdAt = (String) profile.get("created_at");

		if (createdAt != null) {
			wrapped.put("accountAgeYears", calculateAccountAgeYears(createdAt));
		}

		List<Map<String, Object>> tracks = soundCloudService.getUserTracks(accessToken);
		long totalDurationMs = tracks.stream()
				.mapToLong(track -> ((Number) track.getOrDefault("duration", 0))
				.longValue()).sum();
		double totalDurationHours = totalDurationMs / 1000.0 / 60.0 / 60.0;
		wrapped.put("totalListeningHours", totalDurationHours);

		int followers = (int) profile.getOrDefault("followers_count", 0);
		wrapped.put("funFact", followers > 1000 ? "You're pretty famous! 🎉" : "Every star starts small 🥹");

		//Assuming reading speed of 50 pages/book and book length of 300 pages
		int estimatedBooksRead = (int) (totalDurationHours * 50 / 300);
		wrapped.put("booksYouCouldHaveRead", estimatedBooksRead);

		//Top 5 tracks by play count
		List<Map<String, Object>> topTracks = tracks.stream()
				.sorted((a, b) -> Long.compare(
						((Number) b.getOrDefault("playback_count", 0)).longValue(),
						((Number) a.getOrDefault("playback_count", 0)).longValue()))
				.limit(5).toList();
		wrapped.put("topTracks", topTracks);

		List<Map<String, Object>> playlists = soundCloudService.getUserPlaylists(accessToken);
		List<Map<String, Object>> topPlaylists = playlists.stream()
				.sorted((a, b) -> Long.compare(((Number) b.getOrDefault("likes_count", 0))
						.longValue(), ((Number) a.getOrDefault("likes_count", 0))
						.longValue())).limit(5).toList();
		wrapped.put("topPlaylists", topPlaylists);

		List<Map<String, Object>> likes = soundCloudService.getUserLikes(accessToken);
		Map<String, Integer> artistCounts = new HashMap<>();
		Map<String, Long> artistListeningMs = new HashMap<>();

		//Count likes per artist and accumulate track durations
		for (Map<String, Object> track : likes) {
			Map<String, Object> user = (Map<String, Object>) track.get("user");

			if (user != null) {
				String artistName = (String) user.get("username");
				artistCounts.put(artistName, artistCounts.getOrDefault(artistName, 0) + 1);
			}
		}

		for (Map<String, Object> track : tracks) {
			Map<String, Object> user = (Map<String, Object>) track.get("user");

			if (user != null) {
				String artistName = (String) user.get("username");
				long durationMs = ((Number) track.getOrDefault("duration", 0)).longValue();
				artistListeningMs.put(artistName, artistListeningMs.getOrDefault(artistName, 0L) + durationMs);
			}
		}

		//Top 5 most liked artists
		List<String> topArtists = artistCounts.entrySet().stream().sorted((a, b) -> b
				.getValue().compareTo(a.getValue())).limit(5).map(Map.Entry::getKey).toList();
		wrapped.put("topFavoritedArtists", topArtists);

		//Artist listening hours
		Map<String, Double> artistListeningHours = new HashMap<>();

		for (Map.Entry<String, Long> entry : artistListeningMs.entrySet()) {
			artistListeningHours.put(entry.getKey(), entry.getValue() / 1000.0 / 60.0 / 60.0);
		}

		wrapped.put("artistListeningHours", artistListeningHours);

		//Top 5 artists by listening hours
		List<String> topArtistsByHours = artistListeningHours.entrySet().stream()
				.sorted((a, b) -> Double.compare(b.getValue(), a.getValue())).limit(5)
				.map(Map.Entry::getKey).toList();
		wrapped.put("topArtistsByHours", topArtistsByHours);

		//Global music taste placeholder
		wrapped.put("globalTasteComparison", "Listeners in New York, Berlin and Tokyo share your musical taste! 🎧");

		return wrapped;
	}
}