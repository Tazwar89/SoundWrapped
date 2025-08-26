package com.soundwrapped.controller;

import com.soundwrapped.service.SoundCloudService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/soundcloud")
public class SoundCloudController {
	@Autowired
	private final SoundCloudService soundCloudService;

	public SoundCloudController(SoundCloudService soundCloudService) {
		this.soundCloudService = soundCloudService;
	}

	@GetMapping("/profile")
	public Map<String, Object> getUserProfile(@RequestParam String accessToken) {
		return soundCloudService.getUserProfile(accessToken);
	}

	@GetMapping("/favorites")
	public Map<String, Object> getUserFavorites(@RequestParam String accessToken) {
		return soundCloudService.getUserFavorites(accessToken);
	}

	@GetMapping("/playlists")
	public Map<String, Object> getUserPlaylists(@RequestParam String accessToken) {
		return soundCloudService.getUserPlaylists(accessToken);
	}

	@GetMapping("/followers")
	public Map<String, Object> getUserFollowers(@RequestParam String accessToken) {
		return soundCloudService.getUserFollowers(accessToken);
	}
}