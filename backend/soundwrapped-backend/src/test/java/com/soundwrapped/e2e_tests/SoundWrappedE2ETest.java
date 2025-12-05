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

	private SoundWrappedService soundWrappedService;

	@BeforeEach
	void setUp() {
		tokenStore = new TokenStore(tokenRepository);
		soundWrappedService = new SoundWrappedService(tokenStore, restTemplate, genreAnalysisService);

		ReflectionTestUtils.setField(soundWrappedService, "soundCloudApiBaseUrl", "https://api.soundcloud.com");
		ReflectionTestUtils.setField(soundWrappedService, "clientId", "dummyClientId");
		ReflectionTestUtils.setField(soundWrappedService, "clientSecret", "dummyClientSecret");
	}

	@Test
	void testTokenPersistenceAfterExchange() {
		Map<String, Object> fakeTokenResponse = Map.of("access_token", "accessFromCode", "refresh_token",
				"refreshFromCode");

		when(restTemplate.exchange(eq("https://api.soundcloud.com/oauth2/token"), eq(HttpMethod.POST),
				any(HttpEntity.class), any(ParameterizedTypeReference.class)))
				.thenReturn(new ResponseEntity<>(fakeTokenResponse, HttpStatus.OK));

		Map<String, Object> response = soundWrappedService.exchangeAuthorizationCode("dummyCode");

		assertEquals("accessFromCode", response.get("access_token"));

		Optional<Token> savedOpt = tokenRepository.findByAccessToken("accessFromCode");
		assertTrue(savedOpt.isPresent());
		assertEquals("refreshFromCode", savedOpt.get().getRefreshToken());
	}

	@Test
	void testGetUserProfileWithValidToken() {
		Token token = new Token("validAccess", "refresh123");
		tokenStore.saveTokens(token.getAccessToken(), token.getRefreshToken());

		Map<String, Object> fakeProfile = Map.of("username", "testuser");
		ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(fakeProfile, HttpStatus.OK);

		when(restTemplate.exchange(contains("/me"), eq(HttpMethod.GET), any(HttpEntity.class),
				any(ParameterizedTypeReference.class))).thenReturn(responseEntity);

		Map<String, Object> result = soundWrappedService.getUserProfile();
		assertEquals("testuser", result.get("username"));
	}

	@Test
	void testGetUserProfileWithExpiredToken_refreshesToken() {
		Token token = new Token("expiredAccess", "refreshToken");
		tokenStore.saveTokens(token.getAccessToken(), token.getRefreshToken());

		Map<String, Object> fakeProfile = Map.of("username", "refreshedUser");
		ResponseEntity<Map<String, Object>> profileResponse = new ResponseEntity<>(fakeProfile, HttpStatus.OK);

		Map<String, Object> newTokenResponse = Map.of("access_token", "newAccess", "refresh_token", "refreshToken");

		// GET returns 401 first, then succeeds after refresh
		when(restTemplate.exchange(contains("/me"), eq(HttpMethod.GET), any(HttpEntity.class),
				any(ParameterizedTypeReference.class))).thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED))
				.thenReturn(profileResponse);

		// POST to token endpoint
		when(restTemplate.exchange(eq("https://api.soundcloud.com/oauth2/token"), eq(HttpMethod.POST),
				any(HttpEntity.class), any(ParameterizedTypeReference.class)))
				.thenReturn(new ResponseEntity<>(newTokenResponse, HttpStatus.OK));

		Map<String, Object> result = soundWrappedService.getUserProfile();
		assertEquals("refreshedUser", result.get("username"));

		Optional<Token> savedOpt = tokenRepository.findByAccessToken("newAccess");
		assertTrue(savedOpt.isPresent());
	}

	@Test
	void testGetFullWrappedSummary() {
		Token token = new Token("fullSummaryAccess", "refreshToken");
		tokenStore.saveTokens(token.getAccessToken(), token.getRefreshToken());

		Map<String, Object> profile = Map.ofEntries(entry("username", "user1"), entry("full_name", "User One"),
				entry("followers_count", 10), entry("followings_count", 5), entry("public_favorites_count", 3),
				entry("reposts_count", 1), entry("track_count", 2), entry("playlist_count", 1),
				entry("comments_count", 0), entry("upload_seconds_left", 1000),
				entry("created_at", "2013/03/23 14:58:27 +0000"));

		// Mock all GET endpoints
		when(restTemplate.exchange(contains("/me"), eq(HttpMethod.GET), any(HttpEntity.class),
				any(ParameterizedTypeReference.class))).thenReturn(new ResponseEntity<>(profile, HttpStatus.OK));

		when(restTemplate.exchange(contains("/me/favorites"), eq(HttpMethod.GET), any(HttpEntity.class),
				any(ParameterizedTypeReference.class)))
				.thenReturn(new ResponseEntity<>(Map.of("collection", List.of()), HttpStatus.OK));

		when(restTemplate.exchange(contains("/me/playlists"), eq(HttpMethod.GET), any(HttpEntity.class),
				any(ParameterizedTypeReference.class)))
				.thenReturn(new ResponseEntity<>(Map.of("collection", List.of()), HttpStatus.OK));

		when(restTemplate.exchange(contains("/me/followers"), eq(HttpMethod.GET), any(HttpEntity.class),
				any(ParameterizedTypeReference.class)))
				.thenReturn(new ResponseEntity<>(Map.of("collection", List.of()), HttpStatus.OK));

		when(restTemplate.exchange(contains("/me/tracks"), eq(HttpMethod.GET), any(HttpEntity.class),
				any(ParameterizedTypeReference.class)))
				.thenReturn(new ResponseEntity<>(Map.of("collection", List.of()), HttpStatus.OK));

		Map<String, Object> wrapped = soundWrappedService.getFullWrappedSummary();

		assertEquals("user1", wrapped.get("username"));
		assertEquals("User One", wrapped.get("fullName"));
		assertEquals(10, wrapped.get("followers"));
		assertEquals(1, wrapped.get("playlistsCreated"));
	}

	@Test
	void testTokenCleanupAfterSavingNewTokens() {
		tokenStore.saveTokens("oldAccess", "oldRefresh");
		tokenStore.saveTokens("newAccess", "newRefresh");

		List<Token> tokens = tokenRepository.findAll();
		assertEquals(1, tokens.size());
		Token remaining = tokens.get(0);
		assertEquals("newAccess", remaining.getAccessToken());
		assertEquals("newRefresh", remaining.getRefreshToken());
	}
}