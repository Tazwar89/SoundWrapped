package com.soundwrapped.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import java.util.Map;

@Service
public class SoundCloudService {
	@Value("${soundcloud.api.base-url:https://api.soundcloud.com}")
	private String soundCloudApiBaseUrl;
	private final RestTemplate restTemplate = new RestTemplate();

	//Used for making GET requests
	private Map<String, Object> makeGetRequest(String url, String accessToken) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(accessToken);
		HttpEntity<String> entity = new HttpEntity<String>(headers);
		ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

		return response.getBody();
	}

	public Map<String, Object> getUserProfile(String accessToken) {
		String url = soundCloudApiBaseUrl + "/me";

		return makeGetRequest(url, accessToken);
	}

	public Map<String, Object> getUserFavorites(String accessToken) {
		String url = soundCloudApiBaseUrl + "/me/favorites";

		return makeGetRequest(url, accessToken);
    }

	public Map<String, Object> getUserPlaylists(String accessToken) {
		String url = soundCloudApiBaseUrl + "/me/playlists";

		return makeGetRequest(url, accessToken);
    }

	public Map<String, Object> getUserFollowers(String accessToken) {
    	String url = soundCloudApiBaseUrl + "/me/followers";

    	return makeGetRequest(url, accessToken);
    }
}