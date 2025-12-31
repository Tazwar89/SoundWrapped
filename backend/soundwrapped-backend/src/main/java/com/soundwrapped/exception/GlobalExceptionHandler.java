package com.soundwrapped.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import java.io.*;
import java.time.ZonedDateTime;

/**
 * Global handler for application-specific and general exceptions.
 */
@ControllerAdvice
public class GlobalExceptionHandler {
	private ResponseEntity<ErrorResponse> buildErrorResponse(
			HttpStatus status,
			String error,
			String message,
			HttpServletRequest request,
			Exception e) {
		String path = request.getRequestURI();

		//Convert stack trace to string
		StringWriter stringWriter = new StringWriter();
		e.printStackTrace(new PrintWriter(stringWriter));
		String stackTrace = stringWriter.toString();

		ErrorResponse body = new ErrorResponse(
				ZonedDateTime.now(),
				status.value(),
				error,
				message,
				path,
				stackTrace);

		// Add CORS headers to all error responses
		HttpHeaders headers = new HttpHeaders();
		headers.set("Access-Control-Allow-Origin", "*");
		headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
		headers.set("Access-Control-Allow-Headers", "*");

		return new ResponseEntity<ErrorResponse>(body, headers, status);
	}

	@ExceptionHandler(TokenExchangeException.class)
	public ResponseEntity<ErrorResponse> handleTokenExchange(
			TokenExchangeException tee,
			HttpServletRequest request) {
		return buildErrorResponse(
				HttpStatus.BAD_REQUEST,
				"Token exchange failed",
				tee.getMessage(),
				request,
				tee);
	}

	@ExceptionHandler(TokenRefreshException.class)
	public ResponseEntity<ErrorResponse> handleTokenRefresh(
			TokenRefreshException tre,
			HttpServletRequest request) {
		return buildErrorResponse(
				HttpStatus.UNAUTHORIZED,
				"Token refresh failed",
				tre.getMessage(),
				request,
				tre);
	}

	@ExceptionHandler(ApiRequestException.class)
	public ResponseEntity<ErrorResponse> handleApiRequest(
			ApiRequestException are,
			HttpServletRequest request) {
		return buildErrorResponse(
				HttpStatus.BAD_GATEWAY,
				"API request failed",
				are.getMessage(),
				request,
				are);
	}

	//Fallback for any other uncaught exceptions
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleGeneral(
			Exception e,
			HttpServletRequest request) {
		return buildErrorResponse(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Unexpected error",
				e.getMessage(),
				request,
				e);
	}
}