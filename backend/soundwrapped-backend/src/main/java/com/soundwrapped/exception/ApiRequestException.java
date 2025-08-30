package com.soundwrapped.exception;

/**
 * Thrown when an API request to SoundCloud fails
 * for reasons unrelated to OAuth token exchange/refresh.
 */
public class ApiRequestException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public ApiRequestException(String message) {
		super(message);
	}

	public ApiRequestException(String message, Throwable cause) {
		super(message, cause);
	}
}