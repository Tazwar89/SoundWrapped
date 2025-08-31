package com.soundwrapped.service_tests;

import com.soundwrapped.service.TokenStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class TokenStoreTest {
	@Autowired
	private TokenStore tokenStore;

	@Test
	void testTokenPersistence() {
		tokenStore.saveTokens("dummy-access", "dummy-refresh");

		assertEquals("dummy-access", tokenStore.getAccessToken());
		assertEquals("dummy-refresh", tokenStore.getRefreshToken());
	}
}