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
		//Clear old tokens
		tokenRepository.deleteAll();
		tokenRepository.save(new Token(accessToken, refreshToken));
	}

	public String getAccessToken() {
		Optional<Token> token = tokenRepository.findAll().stream().findFirst();

		return token.map(Token::getAccessToken).orElse(null);
	}

	public String getRefreshToken() {
		Optional<Token> token = tokenRepository.findAll().stream().findFirst();

		return token.map(Token::getRefreshToken).orElse(null);
	}
}