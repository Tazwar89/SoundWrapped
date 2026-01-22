package com.soundwrapped.integration_tests;

import com.soundwrapped.service.SoundWrappedService;
import com.soundwrapped.service.TokenStore;
import com.soundwrapped.service.GenreAnalysisService;
import com.soundwrapped.entity.Token;
import com.soundwrapped.exception.TokenRefreshException;
import com.soundwrapped.repository.TokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.mockito.ArgumentMatchers;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import java.util.*;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SoundWrappedServiceIntegrationTest {

	@Autowired
	private TokenRepository tokenRepository;

	private TokenStore tokenStore;

	@Autowired
	private RestTemplate restTemplate;

	@Autowired
	private GenreAnalysisService genreAnalysisService;

	@Autowired
	private com.soundwrapped.repository.UserActivityRepository userActivityRepository;

	@Autowired
	private com.soundwrapped.service.ActivityTrackingService activityTrackingService;
	
	@Autowired
	private com.soundwrapped.service.LyricsService lyricsService;
	
	@Autowired
	private com.soundwrapped.service.EnhancedArtistService enhancedArtistService;
	

	private SoundWrappedService soundWrappedService;

	@TestConfiguration
	static class TestBeansConfig {
		@Bean
		@Primary
		RestTemplate restTemplate() {
			return Mockito.mock(RestTemplate.class);
		}

		@Bean
		@Primary
		GenreAnalysisService genreAnalysisService() {
			return Mockito.mock(GenreAnalysisService.class);
		}

		@Bean
		@Primary
		com.soundwrapped.repository.UserActivityRepository userActivityRepository() {
			return Mockito.mock(com.soundwrapped.repository.UserActivityRepository.class);
		}

		@Bean
		@Primary
		com.soundwrapped.service.ActivityTrackingService activityTrackingService() {
			return Mockito.mock(com.soundwrapped.service.ActivityTrackingService.class);
		}

		@Bean
		@Primary
		com.soundwrapped.service.LyricsService lyricsService() {
			return Mockito.mock(com.soundwrapped.service.LyricsService.class);
		}

		@Bean
		@Primary
		com.soundwrapped.service.EnhancedArtistService enhancedArtistService() {
			return Mockito.mock(com.soundwrapped.service.EnhancedArtistService.class);
		}
	}

	@BeforeEach
	void setUp() {
		// Clean up database before each test to avoid duplicate key errors
		tokenRepository.deleteAll();
		
		MockitoAnnotations.openMocks(this);
		tokenStore = new TokenStore(tokenRepository);
		soundWrappedService = new SoundWrappedService(tokenStore, restTemplate, genreAnalysisService, userActivityRepository, activityTrackingService, lyricsService, enhancedArtistService);

		// Inject dummy SoundCloud API values
		ReflectionTestUtils.setField(soundWrappedService, "soundCloudApiBaseUrl", "https://api.soundcloud.com");
		ReflectionTestUtils.setField(soundWrappedService, "clientId", "dummyClientId");
		ReflectionTestUtils.setField(soundWrappedService, "clientSecret", "dummyClientSecret");
	}

	@Test
	void testMakeGetRequestWithValidAccessToken() {
		String accessToken = "validAccess_" + UUID.randomUUID();
		String refreshToken = "refresh_" + UUID.randomUUID();
		tokenStore.saveTokens(accessToken, refreshToken);

		Map<String, Object> fakeProfile = Map.of("username", "testuser");
		ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<Map<String, Object>>(fakeProfile, HttpStatus.OK);

		when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), ArgumentMatchers.<HttpEntity<?>>any(),
				ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
				.thenReturn(responseEntity);

		Map<String, Object> result = soundWrappedService.getUserProfile();
		assertEquals("testuser", result.get("username"));
	}

	@Test
	void testMakeGetRequestWithExpiredToken_refreshesToken() {
		// Initial expired token - use unique values
		String uniqueExpiredToken = "expiredAccess_" + UUID.randomUUID();
		String uniqueRefreshToken = "refreshToken_" + UUID.randomUUID();
		Token token = new Token(uniqueExpiredToken, uniqueRefreshToken);
		tokenRepository.save(token);
		tokenStore.saveTokens(token.getAccessToken(), token.getRefreshToken());

		// Mock profile after refresh
		Map<String, Object> fakeProfile = Map.of("username", "refreshedUser");

		// Mock new token response from OAuth refresh
		String uniqueNewAccessToken = "newAccess_" + UUID.randomUUID();
		ResponseEntity<Map<String, Object>> newTokenResponse = new ResponseEntity<Map<String, Object>>(
				Map.of("access_token", uniqueNewAccessToken, "refresh_token", uniqueRefreshToken), // same refresh token
				HttpStatus.OK);

		// GET returns 401 first, then succeeds after refresh
		when(restTemplate.exchange(contains("/me"), eq(HttpMethod.GET), ArgumentMatchers.<HttpEntity<?>>any(),
				ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
				.thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED))
				.thenReturn(new ResponseEntity<Map<String, Object>>(fakeProfile, HttpStatus.OK));

		// POST to token endpoint for refresh
		when(restTemplate.exchange(eq("https://api.soundcloud.com/oauth2/token"), eq(HttpMethod.POST),
				ArgumentMatchers.<HttpEntity<?>>any(),
				ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
				.thenReturn(newTokenResponse);

		// Execute
		Map<String, Object> result = soundWrappedService.getUserProfile();

		// Verify updated profile
		assertEquals("refreshedUser", result.get("username"));

		// Verify token updated in DB
		Optional<Token> savedOpt = tokenRepository.findByAccessToken(uniqueNewAccessToken);
		assertTrue(savedOpt.isPresent());
		assertEquals(uniqueRefreshToken, savedOpt.get().getRefreshToken());
	}

	@Test
	void testRefreshAccessToken_missingRefreshToken_throws() {
		TokenRefreshException ex = assertThrows(TokenRefreshException.class,
				() -> soundWrappedService.refreshAccessToken(null));
		assertTrue(ex.getMessage().contains("Missing refresh token"));
	}

	@Test
	void testExchangeAuthorizationCode_savesTokens() {
		Map<String, Object> fakeTokenResponse = Map.of("access_token", "accessFromCode_" + UUID.randomUUID(),
				"refresh_token", "refreshFromCode_" + UUID.randomUUID());

		when(restTemplate.exchange(eq("https://api.soundcloud.com/oauth2/token"), eq(HttpMethod.POST),
				ArgumentMatchers.<HttpEntity<?>>any(),
				ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
				.thenReturn(new ResponseEntity<Map<String, Object>>(fakeTokenResponse, HttpStatus.OK));

		Map<String, Object> response = soundWrappedService.exchangeAuthorizationCode("dummyCode");
		assertEquals(fakeTokenResponse.get("access_token"), response.get("access_token"));

		Optional<Token> savedOpt = tokenRepository.findByAccessToken((String) fakeTokenResponse.get("access_token"));
		assertTrue(savedOpt.isPresent());
		assertEquals(fakeTokenResponse.get("refresh_token"), savedOpt.get().getRefreshToken());
	}

	@Test
	void testGetFullWrappedSummary_returnsAggregatedData() {
		String accessToken = "fullSummaryAccess_" + UUID.randomUUID();
		String refreshToken = "refresh_" + UUID.randomUUID();
		tokenStore.saveTokens(accessToken, refreshToken);

		Map<String, Object> profile = Map.ofEntries(entry("username", "user1"), entry("full_name", "User One"),
				entry("followers_count", 10), entry("followings_count", 5), entry("public_favorites_count", 3),
				entry("reposts_count", 1), entry("track_count", 2), entry("playlist_count", 1),
				entry("comments_count", 0), entry("upload_seconds_left", 1000),
				entry("created_at", "2013/03/23 14:58:27 +0000"));

		// Mock all GET endpoints used by getFullWrappedSummary
		when(restTemplate.exchange(contains("/me"), eq(HttpMethod.GET), ArgumentMatchers.<HttpEntity<?>>any(),
				ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
				.thenReturn(new ResponseEntity<Map<String, Object>>(profile, HttpStatus.OK));

		when(restTemplate.exchange(contains("/me/favorites"), eq(HttpMethod.GET), ArgumentMatchers.<HttpEntity<?>>any(),
				ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
				.thenReturn(new ResponseEntity<Map<String, Object>>(Map.of("collection", List.of()), HttpStatus.OK));

		when(restTemplate.exchange(contains("/me/playlists"), eq(HttpMethod.GET), ArgumentMatchers.<HttpEntity<?>>any(),
				ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
				.thenReturn(new ResponseEntity<Map<String, Object>>(Map.of("collection", List.of()), HttpStatus.OK));

		when(restTemplate.exchange(contains("/me/followers"), eq(HttpMethod.GET), ArgumentMatchers.<HttpEntity<?>>any(),
				ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
				.thenReturn(new ResponseEntity<Map<String, Object>>(Map.of("collection", List.of()), HttpStatus.OK));

		when(restTemplate.exchange(contains("/me/tracks"), eq(HttpMethod.GET), ArgumentMatchers.<HttpEntity<?>>any(),
				ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
				.thenReturn(new ResponseEntity<Map<String, Object>>(Map.of("collection", List.of()), HttpStatus.OK));

		Map<String, Object> wrapped = soundWrappedService.getFullWrappedSummary();

		assertEquals("user1", wrapped.get("username"));
		assertEquals("User One", wrapped.get("fullName"));
		assertEquals(10, wrapped.get("followers"));
		assertEquals(1, wrapped.get("playlistsCreated"));
	}
}