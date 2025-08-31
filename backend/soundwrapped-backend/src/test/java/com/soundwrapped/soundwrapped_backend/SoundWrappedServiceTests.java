package com.soundwrapped.soundwrapped_backend;

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

    private SoundWrappedService soundWrappedService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        soundWrappedService = new SoundWrappedService(tokenStore, restTemplate);
        // Inject a non-null base URL to avoid "null/me"
        ReflectionTestUtils.setField(soundWrappedService, "soundCloudApiBaseUrl", "https://api.soundcloud.com");
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

        //Mock GET request to profileUrl
        when(restTemplate.exchange(
                eq(profileUrl),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
        .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED))
        .thenReturn(new ResponseEntity<Map<String, Object>>(Map.of("username", "testuser"), HttpStatus.OK));

        //POST to refresh token → return new access token
        Map<String, Object> tokenResponse = Map.of("access_token", newAccessToken);
        when(restTemplate.exchange(
                eq(tokenUrl),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
        .thenReturn(new ResponseEntity<Map<String, Object>>(tokenResponse, HttpStatus.OK));

        //Execute service method
        Map<String, Object> result = soundWrappedService.getUserProfile();

        assertEquals("testuser", result.get("username"));
        verify(tokenStore).saveTokens(eq(newAccessToken), eq(refreshToken));

        //Optional: capture the GET request to check Authorization header
        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate, atLeastOnce()).exchange(
                eq(profileUrl),
                eq(HttpMethod.GET),
                captor.capture(),
                any(ParameterizedTypeReference.class)
        );

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
}