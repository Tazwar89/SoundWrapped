package com.soundwrapped.soundwrapped_backend;

import com.soundwrapped.service.*;
import com.soundwrapped.controller.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpStatus;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class SoundWrappedApplicationTests {
	@MockBean
	private TokenStore tokenStore;

	@MockBean
	private SoundWrappedService soundCloudService;

	@MockBean
	private SoundWrappedController soundCloudController;

	@Autowired
    private OAuthCallbackController oAuthCallbackController;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		// Create a mock SoundCloudService
        soundCloudService = mock(SoundWrappedService.class);
        // Inject the mock into the controller
        soundCloudController = new SoundWrappedController(soundCloudService);
		// Use constructor injection if needed
		oAuthCallbackController = new OAuthCallbackController(soundCloudService, tokenStore);
	}

	@Test
	void testOAuthCallbackStoresTokens() {
		String code = "dummyCode";
		doAnswer(invocation -> {
			tokenStore.setAccessToken("mockAccess");
			tokenStore.setRefreshToken("mockRefresh");

			return null;
		}).when(soundCloudService).exchangeAuthorizationCode(code);

		when(tokenStore.getAccessToken()).thenReturn("mockAccess");
		when(tokenStore.getRefreshToken()).thenReturn("mockRefresh");

		Map<String, Object> result = oAuthCallbackController.handleCallback(code);

		assertEquals("mockAccess", result.get("accessToken"));
		assertEquals("mockRefresh", result.get("refreshToken"));
	}

	@Test
	void testGetUserProfileReturnsProfile() {
		Map<String, Object> mockProfile = Map.of("username", "testuser");
		// Mock the makeGetRequestWithRefresh call internally
		when(soundCloudService.getUserProfile()).thenReturn(mockProfile);
		Map<String, Object> profile = soundCloudController.getUserProfile();
		assertEquals("testuser", profile.get("username"));
	}

	@Test
	void testRefreshAccessTokenHandlesUnauthorized() {
		// Example: test refreshAccessToken flow
		when(tokenStore.getAccessToken()).thenReturn("expiredToken");
		when(tokenStore.getRefreshToken()).thenReturn("refreshToken");

		// Here you would mock RestTemplate or the HTTP call
		// to simulate UNAUTHORIZED and successful token refresh
	}
}