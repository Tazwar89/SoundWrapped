package com.soundwrapped.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import java.util.*;

/**
 * SoundCloudService
 * 
 * @author Tazwar Sikder
 */

@Service
public class SoundCloudService {
	@Value("${soundcloud.api.base-url:https://api.soundcloud.com}")
	private String soundCloudApiBaseUrl;
	private final RestTemplate restTemplate = new RestTemplate();

	/**
	 * Sends HTTP GET requests (using Spring's RestTemplate utility) to the URL with
	 * user's authorization code, and returns response data
	 * 
	 * @param url         the SoundCloud endpoint to call
	 * @param accessToken an access token for user authentication
	 * @return            JSON response body as a Map (for a single track)
	 */
	private Map<String, Object> makeGetRequest(String url, String accessToken) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(accessToken);
		HttpEntity<String> entity = new HttpEntity<String>(headers);
		ResponseEntity<Map> trackData = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

		return trackData.getBody();
	}

	/**
	 * "Overloaded" version of makeGetRequest to send GET requests for endpoints
	 * returning paginated JSON arrays
	 * 
	 * @param url         the SoundCloud endpoint to call
	 * @param accessToken an access token for user authentication
	 * @return            JSON response body as a Map (for multiple tracks)
	 */
	private List<Map<String, Object>> fetchPaginatedResults(String initialUrl, String accessToken) {
		List<Map<String, Object>> paginatedResults = new ArrayList<Map<String, Object>>();

		while (initialUrl != null) {
			Map<String, Object> response = makeGetRequest(initialUrl, accessToken);
			List<Map<String, Object>> pageResults = (List<Map<String, Object>>) response.get("collection");

			if (paginatedResults != null) {
				paginatedResults.addAll(pageResults);
			}

			initialUrl = (String) response.get("next_href");
		}

		return paginatedResults;
	}

	public Map<String, Object> getUserProfile(String accessToken) {
		String url = soundCloudApiBaseUrl + "/me";

		return makeGetRequest(url, accessToken);
	}

	public List<Map<String, Object>> getUserFavorites(String accessToken) {
		String url = soundCloudApiBaseUrl + "/me/favorites";

		return fetchPaginatedResults(url, accessToken);
    }

	public List<Map<String, Object>> getUserPlaylists(String accessToken) {
		String url = soundCloudApiBaseUrl + "/me/playlists?linked_partitioning=true&limit=50";

		return fetchPaginatedResults(url, accessToken);
    }

	public List<Map<String, Object>> getUserFollowers(String accessToken) {
    	String url = soundCloudApiBaseUrl + "/me/followers?linked_partitioning=true&limit=50";

    	return fetchPaginatedResults(url, accessToken);
    }

	public List<Map<String, Object>> getUserTracks(String accessToken) {
		String url = soundCloudApiBaseUrl + "/me/tracks?linked_partitioning=true&limit=50";

		return fetchPaginatedResults(url, accessToken);
	}
}