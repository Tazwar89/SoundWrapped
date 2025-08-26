package com.soundwrapped.controller;

import com.soundwrapped.service.SoundCloudService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

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

	@GetMapping("/api/soundcloud/wrapped")
	public Map<String, Object> getWrappedSummary(@RequestParam String accessToken) {
		Map<String, Object> profile = soundCloudService.getUserProfile(accessToken);
		Map<String, Object> wrapped = new HashMap<>();
		wrapped.put("username", profile.get("username"));
		wrapped.put("fullName", profile.get("full_name"));
		wrapped.put("likes", profile.get("public_favorites_count"));
		wrapped.put("reposts", profile.get("reposts_count"));
		wrapped.put("followers", profile.get("followers_count"));
		wrapped.put("following", profile.get("followings_count"));
		wrapped.put("tracks", profile.get("track_count"));
		wrapped.put("playlists", profile.get("playlist_count"));
		wrapped.put("comments", profile.get("comments_count"));

		//Aaccount age in years
		String createdAt = (String) profile.get("created_at");

		if (createdAt != null) {
			wrapped.put("accountAgeYears", calculateAccountAgeYears(createdAt));
		}

		


		return wrapped;
    }
}