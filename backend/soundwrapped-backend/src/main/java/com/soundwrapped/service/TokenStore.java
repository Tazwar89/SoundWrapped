package com.soundwrapped.service;

import com.soundwrapped.entity.Token;
import com.soundwrapped.repository.TokenRepository;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class TokenStore {
	private final TokenRepository tokenRepository;

	public TokenStore(TokenRepository tokenRepository) {
		this.tokenRepository = tokenRepository;
	}

	public void saveTokens(String accessToken, String refreshToken) {
		saveTokens(accessToken, refreshToken, null);
	}

	public void saveTokens(String accessToken, String refreshToken, Integer expiresInSeconds) {
		Token existing = tokenRepository.findByRefreshToken(refreshToken)
				.or(() -> tokenRepository.findByAccessToken(accessToken))
				.orElse(null);

		if (existing != null) {
			existing.setAccessToken(accessToken);
			existing.setRefreshToken(refreshToken);
			if (expiresInSeconds != null) {
				// Update expiration time: refresh proactively 1 hour before expiration
				int refreshBeforeSeconds = Math.max(3600, expiresInSeconds - 3600);
				existing.setExpiresAt(java.time.LocalDateTime.now().plusSeconds(refreshBeforeSeconds));
			} else {
				// Default: refresh after 10 hours
				existing.setExpiresAt(java.time.LocalDateTime.now().plusHours(10));
			}
			tokenRepository.save(existing);
		} else {
			tokenRepository.deleteAll();
			Token newToken = expiresInSeconds != null 
				? new Token(accessToken, refreshToken, expiresInSeconds)
				: new Token(accessToken, refreshToken);
			tokenRepository.save(newToken);
		}
	}

	public String getAccessToken() {
		Optional<Token> token = tokenRepository.findAll().stream().findFirst();

		return token.map(Token::getAccessToken).orElse(null);
	}

	public String getRefreshToken() {
		Optional<Token> token = tokenRepository.findAll().stream().findFirst();

		return token.map(Token::getRefreshToken).orElse(null);
	}

	public Optional<Token> getToken() {
		return tokenRepository.findAll().stream().findFirst();
	}

	public boolean hasValidToken() {
		Optional<Token> token = getToken();
		return token.isPresent() && !token.get().isExpired();
	}

	public boolean needsRefresh() {
		Optional<Token> token = getToken();
		return token.isEmpty() || token.get().isExpiredOrExpiringSoon();
	}
}