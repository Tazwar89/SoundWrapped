package com.soundwrapped.service_tests;

import com.soundwrapped.repository.TokenRepository;
import com.soundwrapped.service.TokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class TokenStoreTest {
	@Autowired
	private TokenRepository tokenRepository;
	
	@Autowired
	private TokenStore tokenStore;

	@BeforeEach
	void setUp() {
		// Clean up database before each test to avoid duplicate key errors
		tokenRepository.deleteAll();
	}

	@Test
	void testTokenPersistence() {
		String uniqueAccessToken = "dummy-access_" + UUID.randomUUID();
		String uniqueRefreshToken = "dummy-refresh_" + UUID.randomUUID();
		
		tokenStore.saveTokens(uniqueAccessToken, uniqueRefreshToken);

		assertEquals(uniqueAccessToken, tokenStore.getAccessToken());
		assertEquals(uniqueRefreshToken, tokenStore.getRefreshToken());
	}
}