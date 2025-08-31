package com.soundwrapped.soundwrapped_backend;

import com.soundwrapped.service.TokenStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class TokenStoreTest {
	@Autowired
	private TokenStore tokenStore;

	@Test
	void testTokenPersistence() {
		tokenStore.saveTokens("dummy-access", "dummy-refresh");

		assertEquals("dummy-access", tokenStore.getAccessToken());
		assertEquals("dummy-refresh", tokenStore.getRefreshToken());
	}
}