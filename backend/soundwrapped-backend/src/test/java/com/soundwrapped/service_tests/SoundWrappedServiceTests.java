package com.soundwrapped.service_tests;

import com.soundwrapped.service.*;
import com.soundwrapped.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SoundWrappedServiceTests {
	@Mock
	private TokenStore tokenStore;

	@Mock
	private RestTemplate restTemplate;

	@Mock
	private GenreAnalysisService genreAnalysisService;

	@Mock
	private com.soundwrapped.repository.UserActivityRepository userActivityRepository;

	@Mock
	private com.soundwrapped.service.ActivityTrackingService activityTrackingService;

	private SoundWrappedService soundWrappedService;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		// Reset mocks to ensure clean state between tests
		reset(tokenStore, restTemplate, genreAnalysisService, userActivityRepository, activityTrackingService);
		soundWrappedService = new SoundWrappedService(tokenStore, restTemplate, genreAnalysisService, userActivityRepository, activityTrackingService);
		// Inject a non-null base URL to avoid "null/me"
		ReflectionTestUtils.setField(soundWrappedService, "soundCloudApiBaseUrl", "https://api.soundcloud.com");
		ReflectionTestUtils.setField(soundWrappedService, "clientId", "testClientId");
		ReflectionTestUtils.setField(soundWrappedService, "clientSecret", "testClientSecret");
	}

	@Test
	void testMakeGetRequestWithRefresh_refreshesTokenOnUnauthorized() {
		String expiredToken = "expiredAccess";
		String refreshToken = "validRefresh";
		String newAccessToken = "newAccess";
		String profileUrl = "https://api.soundcloud.com/me";
		String tokenUrl = "https://api.soundcloud.com/oauth2/token";

		when(tokenStore.getAccessToken()).thenReturn(expiredToken);
		when(tokenStore.getRefreshToken()).thenReturn(refreshToken);

		// Mock GET request to profileUrl
		when(restTemplate.exchange(eq(profileUrl), eq(HttpMethod.GET), any(HttpEntity.class),
				any(ParameterizedTypeReference.class))).thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED))
				.thenReturn(new ResponseEntity<Map<String, Object>>(Map.of("username", "testuser"), HttpStatus.OK));

		// POST to refresh token → return new access token
		Map<String, Object> tokenResponse = Map.of("access_token", newAccessToken);
		when(restTemplate.exchange(eq(tokenUrl), eq(HttpMethod.POST), any(HttpEntity.class),
				any(ParameterizedTypeReference.class)))
				.thenReturn(new ResponseEntity<Map<String, Object>>(tokenResponse, HttpStatus.OK));

		// Execute service method
		Map<String, Object> result = soundWrappedService.getUserProfile();

		assertEquals("testuser", result.get("username"));
		// saveTokens is called with 3 parameters: accessToken, refreshToken, expiresInSeconds (can be null)
		verify(tokenStore).saveTokens(eq(newAccessToken), eq(refreshToken), any());

		// Optional: capture the GET request to check Authorization header
		ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
		verify(restTemplate, atLeastOnce()).exchange(eq(profileUrl), eq(HttpMethod.GET), captor.capture(),
				any(ParameterizedTypeReference.class));

		HttpEntity capturedRequest = captor.getValue();
		assertTrue(capturedRequest.getHeaders().getFirst(HttpHeaders.AUTHORIZATION).contains("Bearer"));
	}

	@Test
	void testRefreshAccessToken_throwsExceptionOnMissingRefreshToken() {
		when(tokenStore.getRefreshToken()).thenReturn(null);
		TokenRefreshException exception = assertThrows(TokenRefreshException.class,
				() -> soundWrappedService.refreshAccessToken(tokenStore.getRefreshToken()));
		assertTrue(exception.getMessage().contains("Missing refresh token"));
	}

	@Test
	void testExchangeAuthorizationCode_throwsExceptionOnEmptyCode() {
		TokenExchangeException exception = assertThrows(TokenExchangeException.class,
				() -> soundWrappedService.exchangeAuthorizationCode(""));
		assertTrue(exception.getMessage().contains("Authorization code must not be empty"));
	}

	@Test
	void testExchangeAuthorizationCode_savesTokensSuccessfully() {
		String authCode = "validCode";
		Map<String, Object> fakeResponse = Map.of("access_token", "access123", "refresh_token", "refresh123");

		when(restTemplate.exchange(eq("https://api.soundcloud.com/oauth2/token"), eq(HttpMethod.POST),
				any(HttpEntity.class), any(ParameterizedTypeReference.class)))
				.thenReturn(new ResponseEntity<>(fakeResponse, HttpStatus.OK));

		Map<String, Object> result = soundWrappedService.exchangeAuthorizationCode(authCode);

		assertEquals("access123", result.get("access_token"));
		// saveTokens is called with 3 parameters: accessToken, refreshToken, expiresInSeconds (can be null)
		verify(tokenStore).saveTokens(eq("access123"), eq("refresh123"), any());
	}

	@Test
	void testMakeGetRequestWithRefresh_throwsIfRefreshFails() {
		String expiredToken = "expired";
		String refreshToken = "refresh";
		String profileUrl = "https://api.soundcloud.com/me";
		String tokenUrl = "https://api.soundcloud.com/oauth2/token";

		when(tokenStore.getAccessToken()).thenReturn(expiredToken);
		when(tokenStore.getRefreshToken()).thenReturn(refreshToken);

		// GET throws 401 (unauthorized)
		when(restTemplate.exchange(eq(profileUrl), eq(HttpMethod.GET), any(HttpEntity.class),
				any(ParameterizedTypeReference.class)))
				.thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

		// POST refresh token fails (returns 400 Bad Request)
		when(restTemplate.exchange(eq(tokenUrl), eq(HttpMethod.POST), any(HttpEntity.class),
				any(ParameterizedTypeReference.class))).thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

		// The exception should be wrapped in ApiRequestException
		ApiRequestException exception = assertThrows(ApiRequestException.class,
				() -> soundWrappedService.getUserProfile());

		// The message should contain "Failed to refresh access token" (it may also contain "during GET request")
		assertTrue(exception.getMessage().contains("Failed to refresh access token"));
	}

	@Test
	void testMakeGetRequestWithRefresh_succeedsWithoutRefresh() {
		String accessToken = "validAccess";
		String profileUrl = "https://api.soundcloud.com/me";

		when(tokenStore.getAccessToken()).thenReturn(accessToken);

		Map<String, Object> profile = Map.of("username", "user1");
		when(restTemplate.exchange(eq(profileUrl), eq(HttpMethod.GET), any(HttpEntity.class),
				any(ParameterizedTypeReference.class))).thenReturn(new ResponseEntity<>(profile, HttpStatus.OK));

		Map<String, Object> result = soundWrappedService.getUserProfile();

		assertEquals("user1", result.get("username"));
		verify(tokenStore, never()).saveTokens(anyString(), anyString());
	}
}