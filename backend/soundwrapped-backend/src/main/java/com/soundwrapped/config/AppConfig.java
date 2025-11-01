package com.soundwrapped.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for RestTemplate with proper timeouts
 */
@Configuration
public class AppConfig {
	@Bean
	public RestTemplate restTemplate() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(10000); // 10 seconds
		factory.setReadTimeout(30000); // 30 seconds
		
		RestTemplate restTemplate = new RestTemplate(factory);
		return restTemplate;
	}
}