package com.soundwrapped.soundwrapped_backend;

import com.soundwrapped.entity.Token;
import com.soundwrapped.repository.TokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class TokenRepositoryIntegrationTest {
	@Autowired
	private TokenRepository tokenRepository;
	
	@BeforeEach
    void cleanDatabase() {
        tokenRepository.deleteAll();
    }

	@Test
    void testSaveAndFindByAccessToken() {
        Token token = new Token("abc123", "refresh123");
        tokenRepository.save(token);

        Optional<Token> foundOpt = tokenRepository.findByAccessToken("abc123");
        assertTrue(foundOpt.isPresent());
        Token found = foundOpt.get();
        assertEquals("refresh123", found.getRefreshToken());
    }
	
	@Test
    void testSaveAndFindByRefreshToken() {
        Token token = new Token("access123", "refresh123");
        tokenRepository.save(token);

        Optional<Token> foundOpt = tokenRepository.findByRefreshToken("refresh123");
        assertTrue(foundOpt.isPresent());
        Token found = foundOpt.get();
        assertEquals("access123", found.getAccessToken());
    }

    @Test
    void testFindAllTokens() {
        Token token1 = new Token("access1", "refresh1");
        Token token2 = new Token("access2", "refresh2");

        tokenRepository.save(token1);
        tokenRepository.save(token2);

        List<Token> tokens = tokenRepository.findAll();
        assertEquals(2, tokens.size());
    }

    @Test
    void testUpdateToken() {
        Token token = new Token("oldAccess", "oldRefresh");
        tokenRepository.save(token);

        Optional<Token> savedOpt = tokenRepository.findByAccessToken("oldAccess");
        assertTrue(savedOpt.isPresent());
        
        Token saved = savedOpt.get();
        saved.setAccessToken("newAccess");
        tokenRepository.save(saved);

        Optional<Token> updatedOpt = tokenRepository.findByAccessToken("newAccess");
        assertTrue(updatedOpt.isPresent());

        Token updated = updatedOpt.get();
        assertNotNull(updated);
        assertEquals("oldRefresh", updated.getRefreshToken());
    }

    @Test
    void testDeleteToken() {
        Token token = new Token("toDelete", "refreshX");
        tokenRepository.save(token);

        Optional<Token> savedOpt = tokenRepository.findByAccessToken("toDelete");
        assertTrue(savedOpt.isPresent());

        Token saved = savedOpt.get();
        tokenRepository.delete(saved);

        Optional<Token> deleted = tokenRepository.findById(saved.getId());
        assertTrue(deleted.isEmpty());
    }
    
    @Test
    void testUniqueAccessTokenConstraint() {
        Token token1 = new Token("uniqueAccess", "refresh1");
        tokenRepository.save(token1);

        Token token2 = new Token("uniqueAccess", "refresh2");
        assertThrows(Exception.class, () -> tokenRepository.saveAndFlush(token2));
    }
}