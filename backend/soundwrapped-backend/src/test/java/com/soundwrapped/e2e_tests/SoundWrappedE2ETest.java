package com.soundwrapped.e2e_tests;

import com.soundwrapped.SoundWrappedApplication;
import com.soundwrapped.config.PostgresTestContainerConfig;
import com.soundwrapped.entity.Token;
import com.soundwrapped.repository.TokenRepository;
import com.soundwrapped.service.SoundWrappedService;
import com.soundwrapped.service.TokenStore;
import com.soundwrapped.service.GenreAnalysisService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import java.util.*;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = { SoundWrappedApplication.class, PostgresTestContainerConfig.class })
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // Reuse container for all tests
class SoundWrappedE2ETest {
	@Autowired
	private TokenRepository tokenRepository;

	private TokenStore tokenStore;

	@MockBean
	private RestTemplate restTemplate;

	@MockBean
	private GenreAnalysisService genreAnalysisService;

	@MockBean
	private com.soundwrapped.repository.UserActivityRepository userActivityRepository;

	@MockBean
	private com.soundwrapped.service.ActivityTrackingService activityTrackingService;
	
	@MockBean
	private com.soundwrapped.service.LyricsService lyricsService;
	
	@MockBean
	private com.soundwrapped.service.EnhancedArtistService enhancedArtistService;
	
	@MockBean
	private com.soundwrapped.service.SimilarArtistsService similarArtistsService;

	private SoundWrappedService soundWrappedService;

	@BeforeEach
	void setUp() {
		// Clean up database before each test to avoid duplicate key errors
		tokenRepository.deleteAll();
		
		tokenStore = new TokenStore(tokenRepository);
		soundWrappedService = new SoundWrappedService(tokenStore, restTemplate, genreAnalysisService, userActivityRepository, activityTrackingService, lyricsService, enhancedArtistService, similarArtistsService);

		ReflectionTestUtils.setField(soundWrappedService, "soundCloudApiBaseUrl", "https://api.soundcloud.com");
		ReflectionTestUtils.setField(soundWrappedService, "clientId", "dummyClientId");
		ReflectionTestUtils.setField(soundWrappedService, "clientSecret", "dummyClientSecret");
	}

	@Test
	void testTokenPersistenceAfterExchange() {
		String uniqueAccessToken = "accessFromCode_" + UUID.randomUUID();
		String uniqueRefreshToken = "refreshFromCode_" + UUID.randomUUID();
		Map<String, Object> fakeTokenResponse = Map.of("access_token", uniqueAccessToken, "refresh_token",
				uniqueRefreshToken);

		when(restTemplate.exchange(eq("https://api.soundcloud.com/oauth2/token"), eq(HttpMethod.POST),
				any(HttpEntity.class), any(ParameterizedTypeReference.class)))
				.thenReturn(new ResponseEntity<Map<String, Object>>(fakeTokenResponse, HttpStatus.OK));

		Map<String, Object> response = soundWrappedService.exchangeAuthorizationCode("dummyCode");

		assertEquals(uniqueAccessToken, response.get("access_token"));

		Optional<Token> savedOpt = tokenRepository.findByAccessToken(uniqueAccessToken);
		assertTrue(savedOpt.isPresent());
		assertEquals(uniqueRefreshToken, savedOpt.get().getRefreshToken());
	}

	@Test
	void testGetUserProfileWithValidToken() {
		String uniqueAccessToken = "validAccess_" + UUID.randomUUID();
		String uniqueRefreshToken = "refresh123_" + UUID.randomUUID();
		Token token = new Token(uniqueAccessToken, uniqueRefreshToken);
		tokenStore.saveTokens(token.getAccessToken(), token.getRefreshToken());

		Map<String, Object> fakeProfile = Map.of("username", "testuser");
		ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<Map<String, Object>>(fakeProfile, HttpStatus.OK);

		when(restTemplate.exchange(contains("/me"), eq(HttpMethod.GET), any(HttpEntity.class),
				any(ParameterizedTypeReference.class))).thenReturn(responseEntity);

		Map<String, Object> result = soundWrappedService.getUserProfile();
		assertEquals("testuser", result.get("username"));
	}

	@Test
	void testGetUserProfileWithExpiredToken_refreshesToken() {
		String uniqueExpiredToken = "expiredAccess_" + UUID.randomUUID();
		String uniqueRefreshToken = "refreshToken_" + UUID.randomUUID();
		Token token = new Token(uniqueExpiredToken, uniqueRefreshToken);
		tokenStore.saveTokens(token.getAccessToken(), token.getRefreshToken());

		Map<String, Object> fakeProfile = Map.of("username", "refreshedUser");
		ResponseEntity<Map<String, Object>> profileResponse = new ResponseEntity<Map<String, Object>>(fakeProfile, HttpStatus.OK);

		String uniqueNewAccessToken = "newAccess_" + UUID.randomUUID();
		Map<String, Object> newTokenResponse = Map.of("access_token", uniqueNewAccessToken, "refresh_token", uniqueRefreshToken);

		// GET returns 401 first, then succeeds after refresh
		when(restTemplate.exchange(contains("/me"), eq(HttpMethod.GET), any(HttpEntity.class),
				any(ParameterizedTypeReference.class))).thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED))
				.thenReturn(profileResponse);

		// POST to token endpoint
		when(restTemplate.exchange(eq("https://api.soundcloud.com/oauth2/token"), eq(HttpMethod.POST),
				any(HttpEntity.class), any(ParameterizedTypeReference.class)))
				.thenReturn(new ResponseEntity<Map<String, Object>>(newTokenResponse, HttpStatus.OK));

		Map<String, Object> result = soundWrappedService.getUserProfile();
		assertEquals("refreshedUser", result.get("username"));

		Optional<Token> savedOpt = tokenRepository.findByAccessToken(uniqueNewAccessToken);
		assertTrue(savedOpt.isPresent());
	}

	@Test
	void testGetFullWrappedSummary() {
		String uniqueAccessToken = "fullSummaryAccess_" + UUID.randomUUID();
		String uniqueRefreshToken = "refreshToken_" + UUID.randomUUID();
		Token token = new Token(uniqueAccessToken, uniqueRefreshToken);
		tokenStore.saveTokens(token.getAccessToken(), token.getRefreshToken());

		Map<String, Object> profile = Map.ofEntries(entry("username", "user1"), entry("full_name", "User One"),
				entry("followers_count", 10), entry("followings_count", 5), entry("public_favorites_count", 3),
				entry("reposts_count", 1), entry("track_count", 2), entry("playlist_count", 1),
				entry("comments_count", 0), entry("upload_seconds_left", 1000),
				entry("created_at", "2013/03/23 14:58:27 +0000"));

		// Mock all GET endpoints
		when(restTemplate.exchange(contains("/me"), eq(HttpMethod.GET), any(HttpEntity.class),
				any(ParameterizedTypeReference.class))).thenReturn(new ResponseEntity<Map<String, Object>>(profile, HttpStatus.OK));

		when(restTemplate.exchange(contains("/me/favorites"), eq(HttpMethod.GET), any(HttpEntity.class),
				any(ParameterizedTypeReference.class)))
				.thenReturn(new ResponseEntity<Map<String, Object>>(Map.of("collection", List.of()), HttpStatus.OK));

		when(restTemplate.exchange(contains("/me/playlists"), eq(HttpMethod.GET), any(HttpEntity.class),
				any(ParameterizedTypeReference.class)))
				.thenReturn(new ResponseEntity<Map<String, Object>>(Map.of("collection", List.of()), HttpStatus.OK));

		when(restTemplate.exchange(contains("/me/followers"), eq(HttpMethod.GET), any(HttpEntity.class),
				any(ParameterizedTypeReference.class)))
				.thenReturn(new ResponseEntity<Map<String, Object>>(Map.of("collection", List.of()), HttpStatus.OK));

		when(restTemplate.exchange(contains("/me/tracks"), eq(HttpMethod.GET), any(HttpEntity.class),
				any(ParameterizedTypeReference.class)))
				.thenReturn(new ResponseEntity<Map<String, Object>>(Map.of("collection", List.of()), HttpStatus.OK));

		Map<String, Object> wrapped = soundWrappedService.getFullWrappedSummary();

		assertEquals("user1", wrapped.get("username"));
		assertEquals("User One", wrapped.get("fullName"));
		assertEquals(10, wrapped.get("followers"));
		assertEquals(1, wrapped.get("playlistsCreated"));
	}

	@Test
	void testTokenCleanupAfterSavingNewTokens() {
		String oldAccessToken = "oldAccess_" + UUID.randomUUID();
		String oldRefreshToken = "oldRefresh_" + UUID.randomUUID();
		String newAccessToken = "newAccess_" + UUID.randomUUID();
		String newRefreshToken = "newRefresh_" + UUID.randomUUID();
		
		tokenStore.saveTokens(oldAccessToken, oldRefreshToken);
		tokenStore.saveTokens(newAccessToken, newRefreshToken);

		List<Token> tokens = tokenRepository.findAll();
		assertEquals(1, tokens.size());
		Token remaining = tokens.get(0);
		assertEquals(newAccessToken, remaining.getAccessToken());
		assertEquals(newRefreshToken, remaining.getRefreshToken());
	}
}