package com.soundwrapped.soundwrapped_backend;

import com.soundwrapped.service.SoundWrappedService;
import com.soundwrapped.service.TokenStore;
import com.soundwrapped.entity.Token;
import com.soundwrapped.exception.TokenRefreshException;
import com.soundwrapped.repository.TokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import java.util.*;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
class SoundWrappedServiceIntegrationTest {

    @Autowired
    private TokenRepository tokenRepository;

    private TokenStore tokenStore;

    @MockBean
    private RestTemplate restTemplate;

    private SoundWrappedService soundWrappedService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        tokenStore = new TokenStore(tokenRepository);
        soundWrappedService = new SoundWrappedService(tokenStore, restTemplate);

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
        ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(fakeProfile, HttpStatus.OK);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        Map<String, Object> result = soundWrappedService.getUserProfile();
        assertEquals("testuser", result.get("username"));
    }

    @Test
    void testMakeGetRequestWithExpiredToken_refreshesToken() {
        // Initial expired token
        Token token = new Token("expiredAccess", "refreshToken");
        tokenRepository.save(token);
        tokenStore.saveTokens(token.getAccessToken(), token.getRefreshToken());

        // Mock profile after refresh
        Map<String, Object> fakeProfile = Map.of("username", "refreshedUser");

        // Mock new token response from OAuth refresh
        ResponseEntity<Map<String, Object>> newTokenResponse = new ResponseEntity<>(
                Map.of("access_token", "newAccess", "refresh_token", "refreshToken"), // same refresh token
                HttpStatus.OK
        );

        // GET returns 401 first, then succeeds after refresh
        when(restTemplate.exchange(
                contains("/me"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        ))
        .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED))
        .thenReturn(new ResponseEntity<>(fakeProfile, HttpStatus.OK));

        // POST to token endpoint for refresh
        when(restTemplate.exchange(
                eq("https://api.soundcloud.com/oauth2/token"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenReturn(newTokenResponse);

        // Execute
        Map<String, Object> result = soundWrappedService.getUserProfile();

        // Verify updated profile
        assertEquals("refreshedUser", result.get("username"));

        // Verify token updated in DB
        Optional<Token> savedOpt = tokenRepository.findByAccessToken("newAccess");
        assertTrue(savedOpt.isPresent());
        assertEquals("refreshToken", savedOpt.get().getRefreshToken());
    }

    @Test
    void testRefreshAccessToken_missingRefreshToken_throws() {
        TokenRefreshException ex = assertThrows(TokenRefreshException.class,
                () -> soundWrappedService.refreshAccessToken(null));
        assertTrue(ex.getMessage().contains("Missing refresh token"));
    }

    @Test
    void testExchangeAuthorizationCode_savesTokens() {
        Map<String, Object> fakeTokenResponse = Map.of(
                "access_token", "accessFromCode_" + UUID.randomUUID(),
                "refresh_token", "refreshFromCode_" + UUID.randomUUID()
        );

        when(restTemplate.exchange(
                eq("https://api.soundcloud.com/oauth2/token"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(fakeTokenResponse, HttpStatus.OK));

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

        Map<String, Object> profile = Map.ofEntries(
                entry("username", "user1"),
                entry("full_name", "User One"),
                entry("followers_count", 10),
                entry("followings_count", 5),
                entry("public_favorites_count", 3),
                entry("reposts_count", 1),
                entry("track_count", 2),
                entry("playlist_count", 1),
                entry("comments_count", 0),
                entry("upload_seconds_left", 1000),
                entry("created_at", "2013/03/23 14:58:27 +0000")
        );

        // Mock all GET endpoints used by getFullWrappedSummary
        when(restTemplate.exchange(
                contains("/me"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(profile, HttpStatus.OK));

        when(restTemplate.exchange(
                contains("/me/favorites"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(Map.of("collection", List.of()), HttpStatus.OK));

        when(restTemplate.exchange(
                contains("/me/playlists"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(Map.of("collection", List.of()), HttpStatus.OK));

        when(restTemplate.exchange(
                contains("/me/followers"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(Map.of("collection", List.of()), HttpStatus.OK));

        when(restTemplate.exchange(
                contains("/me/tracks"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenReturn(new ResponseEntity<>(Map.of("collection", List.of()), HttpStatus.OK));

        Map<String, Object> wrapped = soundWrappedService.getFullWrappedSummary();

        assertEquals("user1", wrapped.get("username"));
        assertEquals("User One", wrapped.get("fullName"));
        assertEquals(10, wrapped.get("followers"));
        assertEquals(1, wrapped.get("playlistsCreated"));
    }
}