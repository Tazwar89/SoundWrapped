package com.soundwrapped.exception;

import java.time.ZonedDateTime;

/**
 * Standardized error response for API exceptions.
 */
public class ErrorResponse {
	private ZonedDateTime timestamp;
	private int status;
	private String error;
	private String message;
	private String path;
	private String stackTrace; // optional, can be null in production

	public ErrorResponse(
			ZonedDateTime timestamp,
			int status,
			String error,
			String message,
			String path,
			String stackTrace) {
		this.timestamp = timestamp;
		this.status = status;
		this.error = error;
		this.message = message;
		this.path = path;
		this.stackTrace = stackTrace;
	}

	public ZonedDateTime getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(ZonedDateTime timestamp) {
		this.timestamp = timestamp;
	}

	public int getStatus() {
		return status;
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public String getError() {
		return error;
	}

	public void setError(String error) {
		this.error = error;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
	
	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}
	
	public String getStackTrace() {
		return stackTrace;
	}

	public void setStackTrace(String stackTrace) {
		this.stackTrace = stackTrace;
	}
}