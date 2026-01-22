package com.soundwrapped.integration_tests;

import com.soundwrapped.entity.Token;
import com.soundwrapped.repository.TokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TokenRepositoryIntegrationTest {
	@Autowired
	private TokenRepository tokenRepository;

	@BeforeEach
    void cleanDatabase() {
        // Ensure no leftover tokens exist
        tokenRepository.deleteAll();
    }

	@Test
	void testSaveAndFindByAccessToken() {
		String accessToken = "test-access_" + UUID.randomUUID();
		String refreshToken = "test-refresh_" + UUID.randomUUID();
		Token token = new Token(accessToken, refreshToken);
		tokenRepository.save(token);

		Optional<Token> foundOpt = tokenRepository.findByAccessToken(accessToken);
		assertTrue(foundOpt.isPresent());
		Token found = foundOpt.get();
		assertEquals(refreshToken, found.getRefreshToken());
	}

	@Test
	void testSaveAndFindByRefreshToken() {
		String accessToken = "test-access_" + UUID.randomUUID();
		String refreshToken = "test-refresh_" + UUID.randomUUID();
		Token token = new Token(accessToken, refreshToken);
		tokenRepository.save(token);

		Optional<Token> foundOpt = tokenRepository.findByRefreshToken(refreshToken);
		assertTrue(foundOpt.isPresent());
		Token found = foundOpt.get();
		assertEquals(accessToken, found.getAccessToken());
	}

	@Test
	void testFindAllTokens() {
		Token token1 = new Token("access1_" + UUID.randomUUID(), "refresh1_" + UUID.randomUUID());
		Token token2 = new Token("access2_" + UUID.randomUUID(), "refresh2_" + UUID.randomUUID());

		tokenRepository.save(token1);
		tokenRepository.save(token2);

		List<Token> tokens = tokenRepository.findAll();
		assertEquals(2, tokens.size());
	}

	@Test
	void testUpdateToken() {
		String oldAccess = "oldAccess_" + UUID.randomUUID();
		String oldRefresh = "oldRefresh_" + UUID.randomUUID();
		String newAccess = "newAccess_" + UUID.randomUUID();
		
		Token token = new Token(oldAccess, oldRefresh);
		tokenRepository.save(token);

		Optional<Token> savedOpt = tokenRepository.findByAccessToken(oldAccess);
		assertTrue(savedOpt.isPresent());

		Token saved = savedOpt.get();
		saved.setAccessToken(newAccess);
		tokenRepository.save(saved);

		Optional<Token> updatedOpt = tokenRepository.findByAccessToken(newAccess);
		assertTrue(updatedOpt.isPresent());

		Token updated = updatedOpt.get();
		assertNotNull(updated);
		assertEquals(oldRefresh, updated.getRefreshToken());
	}

	@Test
	void testDeleteToken() {
		String accessToken = "toDelete_" + UUID.randomUUID();
		String refreshToken = "refreshX_" + UUID.randomUUID();
		Token token = new Token(accessToken, refreshToken);
		tokenRepository.save(token);

		Optional<Token> savedOpt = tokenRepository.findByAccessToken(accessToken);
		assertTrue(savedOpt.isPresent());

		Token saved = savedOpt.get();
		tokenRepository.delete(saved);

		Optional<Token> deleted = tokenRepository.findById(saved.getId());
		assertTrue(deleted.isEmpty());
	}

	@Test
	void testUniqueAccessTokenConstraint() {
		// Use unique values per test run to avoid conflicts with other tests
		String duplicateAccess = "uniqueAccess_" + UUID.randomUUID();
		Token token1 = new Token(duplicateAccess, "refresh1_" + UUID.randomUUID());
		tokenRepository.save(token1);

		Token token2 = new Token(duplicateAccess, "refresh2_" + UUID.randomUUID());
		assertThrows(DataIntegrityViolationException.class, () -> tokenRepository.saveAndFlush(token2));
	}

	@Test
	void testUniqueRefreshTokenConstraint() {
		// Use unique values per test run to avoid conflicts with other tests
		String duplicateRefresh = "refreshUnique_" + UUID.randomUUID();
		Token token1 = new Token("access1_" + UUID.randomUUID(), duplicateRefresh);
		tokenRepository.save(token1);

		Token token2 = new Token("access2_" + UUID.randomUUID(), duplicateRefresh);
		assertThrows(DataIntegrityViolationException.class, () -> tokenRepository.saveAndFlush(token2));
	}
}