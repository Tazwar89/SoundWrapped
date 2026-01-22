package com.soundwrapped.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.cache.Cache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Custom CaffeineCache implementation since Spring Boot 3.5 may not include it.
 * This wraps Caffeine cache to implement Spring's Cache interface.
 */
class CaffeineCache implements Cache {
	private final String name;
	private final com.github.benmanes.caffeine.cache.Cache<Object, Object> cache;

	public CaffeineCache(String name, com.github.benmanes.caffeine.cache.Cache<Object, Object> cache) {
		this.name = name;
		this.cache = cache;
	}

	@Override
	@NonNull
	public String getName() {
		return name;
	}

	@Override
	@NonNull
	public Object getNativeCache() {
		return cache;
	}

	@Override
	@Nullable
	public Cache.ValueWrapper get(@NonNull Object key) {
		Object value = cache.getIfPresent(key);
		return value != null ? new SimpleValueWrapper(value) : null;
	}

	@Override
	@Nullable
	public <T> T get(@NonNull Object key, @Nullable Class<T> type) {
		Object value = cache.getIfPresent(key);
		return type != null && value != null && type.isInstance(value) ? type.cast(value) : null;
	}

	@Override
	@SuppressWarnings("unchecked")
	@NonNull
	public <T> T get(@NonNull Object key, @NonNull java.util.concurrent.Callable<T> valueLoader) {
		try {
			return (T) cache.get(key, k -> {
				try {
					return valueLoader.call();
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void put(@NonNull Object key, @Nullable Object value) {
		cache.put(key, value);
	}

	@Override
	public void evict(@NonNull Object key) {
		cache.invalidate(key);
	}

	@Override
	public void clear() {
		cache.invalidateAll();
	}

	private static class SimpleValueWrapper implements Cache.ValueWrapper {
		private final Object value;

		public SimpleValueWrapper(Object value) {
			this.value = value;
		}

		@Override
		public Object get() {
			return value;
		}
	}
}

/**
 * Configuration for caching expensive operations.
 * Uses Caffeine for in-memory caching with TTL-based eviction.
 */
@Configuration
@EnableCaching
public class CacheConfig {

	/**
	 * Cache manager for API responses and expensive computations.
	 * 
	 * Cache names:
	 * - "groqDescriptions": Groq API responses (1 hour TTL)
	 * - "enhancedArtists": TheAudioDB artist info (24 hours TTL)
	 * - "similarArtists": Last.fm similar artists (12 hours TTL)
	 * - "lyrics": Lyrics from Lyrics.ovh (7 days TTL - lyrics don't change)
	 * - "popularTracks": Popular tracks list (30 minutes TTL)
	 */
	@Bean
	public CacheManager cacheManager() {
		SimpleCacheManager cacheManager = new SimpleCacheManager();
		
		// Configure cache for Groq descriptions (1 hour TTL)
		Cache groqDescriptionsCache = new CaffeineCache("groqDescriptions",
			Caffeine.newBuilder()
				.maximumSize(1000)
				.expireAfterWrite(1, TimeUnit.HOURS)
				.recordStats()
				.build()
		);
		
		// Configure cache for enhanced artist info (24 hours TTL)
		Cache enhancedArtistsCache = new CaffeineCache("enhancedArtists",
			Caffeine.newBuilder()
				.maximumSize(500)
				.expireAfterWrite(24, TimeUnit.HOURS)
				.recordStats()
				.build()
		);
		
		// Configure cache for similar artists (12 hours TTL)
		Cache similarArtistsCache = new CaffeineCache("similarArtists",
			Caffeine.newBuilder()
				.maximumSize(500)
				.expireAfterWrite(12, TimeUnit.HOURS)
				.recordStats()
				.build()
		);
		
		// Configure cache for lyrics (7 days TTL - lyrics rarely change)
		Cache lyricsCache = new CaffeineCache("lyrics",
			Caffeine.newBuilder()
				.maximumSize(2000)
				.expireAfterWrite(7, TimeUnit.DAYS)
				.recordStats()
				.build()
		);
		
		// Configure cache for popular tracks (30 minutes TTL)
		Cache popularTracksCache = new CaffeineCache("popularTracks",
			Caffeine.newBuilder()
				.maximumSize(10)
				.expireAfterWrite(30, TimeUnit.MINUTES)
				.recordStats()
				.build()
		);
		
		cacheManager.setCaches(Arrays.asList(
			groqDescriptionsCache,
			enhancedArtistsCache,
			similarArtistsCache,
			lyricsCache,
			popularTracksCache
		));
		
		return cacheManager;
	}
}

