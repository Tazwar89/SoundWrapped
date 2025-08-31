package com.soundwrapped.soundwrapped_backend;

import com.soundwrapped.entity.Token;
import com.soundwrapped.repository.TokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class TokenRepositoryIntegrationTest {
	@Autowired
	private TokenRepository tokenRepository;

	@Test
    void testSaveAndFindById() {
        Token token = new Token("abc123", "refresh123");
        tokenRepository.save(token);

        Token found = tokenRepository.findById(token.getId()).orElse(null);

        assertNotNull(found);
        assertEquals("abc123", found.getAccessToken());
    }

    @Test
    void testFindByAccessToken() {
        Token token = new Token("accessKey", "refreshKey");
        tokenRepository.save(token);

        Token found = tokenRepository.findByAccessToken("accessKey").orElse(null);

        assertNotNull(found);
        assertEquals("refreshKey", found.getRefreshToken());
    }

    @Test
    void testFindByRefreshToken() {
        Token token = new Token("accessX", "refreshX");
        tokenRepository.save(token);

        Token found = tokenRepository.findByRefreshToken("refreshX").orElse(null);

        assertNotNull(found);
        assertEquals("accessX", found.getAccessToken());
    }

    @Test
    void testUpdateToken() {
        Token token = new Token("access1", "refresh1");
        tokenRepository.save(token);

        Token found = tokenRepository.findByAccessToken("access1").orElseThrow();
        found.setAccessToken("access2");
        tokenRepository.save(found);

        Token updated = tokenRepository.findByAccessToken("access2").orElse(null);

        assertNotNull(updated);
        assertEquals("refresh1", updated.getRefreshToken());
    }

    @Test
    void testDeleteToken() {
        Token token = new Token("toDelete", "refreshDel");
        tokenRepository.save(token);

        tokenRepository.deleteById(token.getId());

        assertTrue(tokenRepository.findById(token.getId()).isEmpty());
    }

    @Test
    void testFindAllTokens() {
        tokenRepository.save(new Token("a1", "r1"));
        tokenRepository.save(new Token("a2", "r2"));

        List<Token> all = tokenRepository.findAll();

        assertFalse(all.isEmpty());
        assertTrue(all.size() >= 2);
    }
}