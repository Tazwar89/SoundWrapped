package com.soundwrapped.exception;

public class TokenExchangeException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public TokenExchangeException(String message) {
		super(message);
	}

	public TokenExchangeException(String message, Throwable cause) {
		super(message, cause);
	}
}