package com.soundwrapped.controller;

import com.soundwrapped.service.SoundCloudService;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * SoundCloudController 
 * 
 * @author Tazwar Sikder
 */

@RestController
@RequestMapping("/api/soundcloud")
public class SoundCloudController {
	private final SoundCloudService soundCloudService;

	public SoundCloudController(SoundCloudService soundCloudService) {
		this.soundCloudService = soundCloudService;
	}

	@GetMapping("/profile")
	public Map<String, Object> getUserProfile(@RequestParam String accessToken) {
		return soundCloudService.getUserProfile(accessToken);
	}

	@GetMapping("/favorites")
	public List<Map<String, Object>> getUserFavorites(@RequestParam String accessToken) {
		return soundCloudService.getUserFavorites(accessToken);
	}

	@GetMapping("/playlists")
	public List<Map<String, Object>> getUserPlaylists(@RequestParam String accessToken) {
		return soundCloudService.getUserPlaylists(accessToken);
	}

	@GetMapping("/followers")
	public List<Map<String, Object>> getUserFollowers(@RequestParam String accessToken) {
		return soundCloudService.getUserFollowers(accessToken);
	}

	@GetMapping("/tracks")
	public List<Map<String, Object>> getUserTracks(@RequestParam String accessToken) {
		return soundCloudService.getUserTracks(accessToken);
	}
}