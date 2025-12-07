/**
 * SoundWrapped Browser Extension - Content Script
 * Monitors SoundCloud web player and tracks listening activity
 */

(function() {
    'use strict';

    // Configuration
    const API_BASE_URL = 'http://localhost:8080/api/tracking';
    const CHECK_INTERVAL = 2000; // Check every 2 seconds
    const MIN_PLAY_DURATION = 5000; // Minimum 5 seconds to count as a play

    // State
    let currentTrack = null;
    let playStartTime = null;
    let lastTrackId = null;
    let isPlaying = false;
    let userAuthenticated = false;

    // Initialize
    function init() {
        console.log('%c[SoundWrapped] 🎵 Extension loaded successfully!', 'color: #ff5500; font-weight: bold; font-size: 14px;');
        console.log('[SoundWrapped] Page URL:', window.location.href);
        console.log('[SoundWrapped] Extension version: 1.0.0');
        checkAuthStatus();
        startMonitoring();
        setupButtonListeners(); // Add button click listeners for play/pause detection
        
        // Visual indicator in page
        createVisualIndicator();
    }

    // Create visual indicator that extension is running
    function createVisualIndicator() {
        // Remove existing indicator if any
        const existing = document.getElementById('soundwrapped-indicator');
        if (existing) existing.remove();
        
        const indicator = document.createElement('div');
        indicator.id = 'soundwrapped-indicator';
        indicator.style.cssText = `
            position: fixed;
            top: 10px;
            right: 10px;
            background: #ff5500;
            color: white;
            padding: 8px 12px;
            border-radius: 6px;
            font-size: 12px;
            font-weight: bold;
            z-index: 999999;
            box-shadow: 0 2px 8px rgba(0,0,0,0.3);
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
        `;
        indicator.textContent = '🎵 SoundWrapped Tracking';
        document.body.appendChild(indicator);
        
        // Hide after 5 seconds
        setTimeout(() => {
            if (indicator) {
                indicator.style.opacity = '0.5';
                indicator.style.transition = 'opacity 0.3s';
            }
        }, 5000);
    }

    // Check if user is authenticated with SoundWrapped
    async function checkAuthStatus() {
        try {
            // Use background script to make API call (bypasses CORS)
            const response = await chrome.runtime.sendMessage({
                type: 'CHECK_AUTH'
            });
            
            if (response && response.authenticated) {
                userAuthenticated = true;
                console.log('[SoundWrapped] ✅ Authenticated with SoundWrapped');
            } else {
                userAuthenticated = false;
                console.log('[SoundWrapped] ⚠️ Not authenticated - log into SoundWrapped at http://localhost:3000');
            }
        } catch (error) {
            console.log('[SoundWrapped] ⚠️ Auth check failed:', error.message);
            userAuthenticated = false;
        }
    }

    // Start monitoring SoundCloud player
    function startMonitoring() {
        // Check immediately and after a delay (in case track is already playing)
        setTimeout(() => {
            detectPlayback();
            setupAudioListeners(); // Listen directly to audio element events
        }, 1000);
        
        setInterval(() => {
            detectPlayback();
        }, CHECK_INTERVAL);
    }

    // Track which audio elements we've already set up listeners for
    const audioElementsWithListeners = new WeakSet();
    
    // Setup direct audio element event listeners for more reliable detection
    function setupAudioListeners() {
        console.log('[SoundWrapped] 🔧 Setting up audio listeners...');
        
        // Find audio elements and add event listeners
        const audioElements = document.querySelectorAll('audio');
        console.log('[SoundWrapped] Found', audioElements.length, 'audio element(s)');
        
        audioElements.forEach((audio, index) => {
            // Skip if we already added listeners to this element
            if (audioElementsWithListeners.has(audio)) {
                console.log('[SoundWrapped] Audio element', index, 'already has listeners');
                return;
            }
            
            // Mark as having listeners
            audioElementsWithListeners.add(audio);
            console.log('[SoundWrapped] ✅ Added listeners to audio element', index);
            
            // Listen for play events
            audio.addEventListener('play', () => {
                console.log('[SoundWrapped] 🔊 Audio play event detected on element', index);
                setTimeout(() => {
                    const trackInfo = extractTrackInfo();
                    if (trackInfo && trackInfo.trackId && (!isPlaying || trackInfo.trackId !== lastTrackId)) {
                        onTrackStart(trackInfo);
                    }
                }, 100);
            }, { once: false, passive: true });
            
            // Listen for pause events
            audio.addEventListener('pause', () => {
                console.log('[SoundWrapped] ⏸️ Audio pause event detected on element', index);
                if (isPlaying && currentTrack) {
                    setTimeout(() => {
                        onTrackStop(currentTrack);
                    }, 100);
                }
            }, { once: false, passive: true });
            
            // Listen for ended events
            audio.addEventListener('ended', () => {
                console.log('[SoundWrapped] 🏁 Audio ended event detected on element', index);
                if (isPlaying && currentTrack) {
                    onTrackStop(currentTrack);
                }
            }, { once: false, passive: true });
            
            // Also listen to playing state changes
            audio.addEventListener('playing', () => {
                console.log('[SoundWrapped] ▶️ Audio playing event detected');
            }, { once: false, passive: true });
        });
        
        // Also listen at document level for any media play/pause (catch-all)
        if (!window.__soundwrapped_doc_listeners_set) {
            document.addEventListener('play', (e) => {
                if (e.target.tagName === 'AUDIO' || e.target.tagName === 'VIDEO') {
                    console.log('[SoundWrapped] 🔊 Document-level play event:', e.target.tagName);
                    setTimeout(() => {
                        const trackInfo = extractTrackInfo();
                        if (trackInfo && trackInfo.trackId && (!isPlaying || trackInfo.trackId !== lastTrackId)) {
                            onTrackStart(trackInfo);
                        }
                    }, 100);
                }
            }, true); // Use capture phase
            
            document.addEventListener('pause', (e) => {
                if (e.target.tagName === 'AUDIO' || e.target.tagName === 'VIDEO') {
                    console.log('[SoundWrapped] ⏸️ Document-level pause event:', e.target.tagName);
                    if (isPlaying && currentTrack) {
                        setTimeout(() => {
                            onTrackStop(currentTrack);
                        }, 100);
                    }
                }
            }, true); // Use capture phase
            
            document.addEventListener('ended', (e) => {
                if (e.target.tagName === 'AUDIO' || e.target.tagName === 'VIDEO') {
                    console.log('[SoundWrapped] 🏁 Document-level ended event:', e.target.tagName);
                    if (isPlaying && currentTrack) {
                        onTrackStop(currentTrack);
                    }
                }
            }, true); // Use capture phase
            
            window.__soundwrapped_doc_listeners_set = true;
            console.log('[SoundWrapped] ✅ Document-level listeners set up');
        }
        
        // Also watch for new audio elements being added (SoundCloud might inject them dynamically)
        if (!window.__soundwrapped_observer) {
            const observer = new MutationObserver((mutations) => {
                mutations.forEach((mutation) => {
                    mutation.addedNodes.forEach((node) => {
                        if (node.nodeName === 'AUDIO' || (node.querySelector && node.querySelector('audio'))) {
                            console.log('[SoundWrapped] 🔍 New audio element detected, re-setting up listeners');
                            setTimeout(() => {
                                setupAudioListeners();
                            }, 500);
                        }
                    });
                });
            });
            
            observer.observe(document.body, {
                childList: true,
                subtree: true
            });
            
            window.__soundwrapped_observer = observer;
            console.log('[SoundWrapped] ✅ MutationObserver set up');
        }
    }

    // Detect current playback state
    function detectPlayback() {
        try {
            // Get track info first (this works on any SoundCloud page)
            const trackInfo = extractTrackInfo();
            
            if (!trackInfo || !trackInfo.trackId) {
                // No track info available - might be on stream or search page
                return;
            }

            const currentlyPlaying = isCurrentlyPlaying();
            
            // Track started playing (or was already playing when extension loaded)
            if (currentlyPlaying && !isPlaying) {
                // Check if this is a different track or if we need to start tracking
                if (trackInfo.trackId !== lastTrackId || lastTrackId === null) {
                    console.log('[SoundWrapped] 🎵 Track started:', trackInfo);
                    onTrackStart(trackInfo);
                } else {
                    // Same track, but we're not tracking - might have missed the start
                    console.log('[SoundWrapped] 🔄 Detected already-playing track, starting timer');
                    onTrackStart(trackInfo);
                }
            }
            // Track is playing (continuing)
            else if (currentlyPlaying && isPlaying && trackInfo.trackId === lastTrackId) {
                // Track is continuing to play - update position if needed
                // Progress logging is now handled by audio event listeners
            }
            // Track stopped (detected via polling)
            else if (!currentlyPlaying && isPlaying) {
                // Make sure it's the same track
                if (trackInfo.trackId === lastTrackId) {
                    console.log('[SoundWrapped] ⏸️ Track stopped (via polling):', trackInfo);
                    onTrackStop(trackInfo);
                } else {
                    // Track changed while we thought it was playing
                    console.log('[SoundWrapped] 🔄 Track changed while playing');
                    if (currentTrack && playStartTime) {
                        onTrackStop(currentTrack);
                    }
                }
            }
            // Track changed while playing
            else if (currentlyPlaying && isPlaying && trackInfo.trackId !== lastTrackId) {
                console.log('[SoundWrapped] 🔄 Track changed:', lastTrackId, '→', trackInfo.trackId);
                // Stop previous track
                if (currentTrack && playStartTime) {
                    onTrackStop(currentTrack);
                }
                // Start new track
                onTrackStart(trackInfo);
            }
            // Debug: Track detected but not playing yet
            else if (!currentlyPlaying && !isPlaying && trackInfo.trackId !== lastTrackId) {
                // Track is available but not playing - this is normal, don't log
            }
        } catch (error) {
            console.error('[SoundWrapped] Error detecting playback:', error);
        }
    }

    // Extract track information from SoundCloud page
    function extractTrackInfo() {
        try {
            // Method 1: Try to get from window.__sc_hydration (most reliable)
            if (window.__sc_hydration) {
                try {
                    const hydration = window.__sc_hydration;
                    // SoundCloud stores data in various formats
                    for (const key in hydration) {
                        const data = hydration[key];
                        if (data && typeof data === 'object') {
                            // Look for track data
                            if (data.track) {
                                const track = data.track;
                                const trackId = track.id || track.permalink || track.permalink_url?.split('/').pop();
                                if (trackId) {
                                    return {
                                        trackId: trackId.toString(),
                                        title: track.title || 'Unknown Track',
                                        artist: track.user?.username || track.user?.full_name || 'Unknown Artist',
                                        url: track.permalink_url || track.uri || window.location.href
                                    };
                                }
                            }
                            // Look for current track in stream
                            if (data.currentTrack || data.trackId) {
                                const trackId = data.currentTrack?.id || data.trackId;
                                if (trackId) {
                                    return {
                                        trackId: trackId.toString(),
                                        title: data.currentTrack?.title || data.title || 'Unknown Track',
                                        artist: data.currentTrack?.user?.username || data.user?.username || 'Unknown Artist',
                                        url: window.location.href
                                    };
                                }
                            }
                        }
                    }
                } catch (e) {
                    console.log('[SoundWrapped] Error reading hydration:', e);
                }
            }

            // Method 2: Try to get from metadata tags (most common)
            const ogTitle = document.querySelector('meta[property="og:title"]');
            const ogUrl = document.querySelector('meta[property="og:url"]');
            
            if (ogTitle && ogUrl) {
                const title = ogTitle.getAttribute('content');
                const url = ogUrl.getAttribute('content');
                
                // Extract track ID from URL
                // SoundCloud URLs: https://soundcloud.com/artist/track-name
                const trackIdMatch = url.match(/soundcloud\.com\/[^\/]+\/([^\/\?#]+)/);
                const trackId = trackIdMatch ? trackIdMatch[1] : null;
                
                if (trackId) {
                    // Try multiple selectors for artist name
                    const artistElement = document.querySelector('.soundTitle__username, .sc-artwork-username, a[itemprop="byArtist"]') ||
                                          document.querySelector('span[class*="username"], a[class*="username"]');
                    const artist = artistElement ? artistElement.textContent.trim() : 
                                  url.split('/')[3] || 'Unknown Artist';
                    
                    return {
                        trackId: trackId,
                        title: title,
                        artist: artist,
                        url: url
                    };
                }
            }

            // Method 2: Try to get from page content
            const trackTitle = document.querySelector('.soundTitle__title, h1[itemprop="name"]');
            const trackArtist = document.querySelector('.soundTitle__username, .sc-artwork-username, [itemprop="byArtist"]');
            const trackLink = document.querySelector('link[rel="canonical"]');
            
            if (trackTitle && trackLink) {
                const title = trackTitle.textContent.trim();
                const artist = trackArtist ? trackArtist.textContent.trim() : 'Unknown Artist';
                const url = trackLink.getAttribute('href');
                
                // Extract track ID from URL
                const trackIdMatch = url.match(/soundcloud\.com\/[^\/]+\/([^\/\?#]+)/);
                const trackId = trackIdMatch ? trackIdMatch[1] : null;
                
                if (trackId) {
                    return {
                        trackId: trackId,
                        title: title,
                        artist: artist,
                        url: url
                    };
                }
            }

            // Method 3: Try to get from window object (if SoundCloud exposes it)
            if (window.__sc_hydration) {
                const hydration = window.__sc_hydration;
                // SoundCloud stores track data in hydration
                for (const key in hydration) {
                    if (hydration[key] && hydration[key].track) {
                        const track = hydration[key].track;
                        return {
                            trackId: track.permalink || track.id,
                            title: track.title,
                            artist: track.user ? track.user.username : 'Unknown Artist',
                            url: track.permalink_url
                        };
                    }
                }
            }
        } catch (error) {
            console.error('[SoundWrapped] Error extracting track info:', error);
        }
        
        return null;
    }

    // Check if track is currently playing
    function isCurrentlyPlaying() {
        try {
            // Method 1: Check for audio element (most reliable)
            const audioElements = document.querySelectorAll('audio');
            if (audioElements.length > 0) {
                for (const audio of audioElements) {
                    // Check if audio is actually playing
                    if (!audio.paused && !audio.ended && audio.currentTime > 0 && audio.readyState > 0) {
                        // Double check - audio might be paused but not updated yet
                        if (audio.currentTime > 0) {
                            return true;
                        }
                    }
                }
                // If we found audio elements but none are playing, it's paused
                return false;
            }

            // Method 2: Check button states - look for pause button (means playing) or play button (means paused)
            // First, check for pause button (indicates playing)
            const pauseButtons = document.querySelectorAll(
                'button[aria-label*="Pause"], button[aria-label*="pause"], ' +
                '.sc-button-pause, button[class*="pause"], button[class*="Pause"], ' +
                '[data-testid="pause-button"], [aria-label="Pause"], [aria-label="pause"]'
            );
            
            let foundPauseButton = false;
            for (const button of pauseButtons) {
                const ariaLabel = (button.getAttribute('aria-label') || '').toLowerCase();
                const classList = button.classList;
                const isVisible = button.offsetParent !== null; // Check if button is visible
                
                // If button explicitly says "pause" and is visible, track is playing
                if (isVisible && (ariaLabel.includes('pause') || 
                    classList.contains('sc-button-pause') ||
                    classList.contains('pause'))) {
                    foundPauseButton = true;
                    return true;
                }
            }
            
            // Then check for play button (indicates paused/stopped)
            const playButtons = document.querySelectorAll(
                'button[aria-label*="Play"], button[aria-label*="play"], ' +
                '.sc-button-play, button[class*="play"], button[class*="Play"], ' +
                '[data-testid="play-button"], .playControl, [aria-label="Play"], [aria-label="play"]'
            );
            
            let foundPlayButton = false;
            for (const button of playButtons) {
                const ariaLabel = (button.getAttribute('aria-label') || '').toLowerCase();
                const classList = button.classList;
                const isVisible = button.offsetParent !== null; // Check if button is visible
                
                // If button says "play" (and not "pause") and is visible, track is paused
                if (isVisible && ariaLabel.includes('play') && !ariaLabel.includes('pause')) {
                    foundPlayButton = true;
                    // But check if it has a "playing" class which might override
                    if (classList.contains('playing') || 
                        classList.contains('sc-button-pause') ||
                        button.querySelector('.sc-button-pause')) {
                        return true; // Playing despite "play" label
                    }
                }
            }
            
            // If we found a play button (not pause) and no pause button, it's paused
            if (foundPlayButton && !foundPauseButton) {
                return false; // Only play button visible, no pause button = paused
            }
            
            // If we found a pause button, it's playing
            if (foundPauseButton) {
                return true;
            }

            // Method 3: Check for progress bar animation
            const progressBars = document.querySelectorAll(
                '.playbackTimeline__progress, .sc-player-progress, ' +
                '[class*="progress"], [class*="Progress"]'
            );
            
            for (const bar of progressBars) {
                const style = window.getComputedStyle(bar);
                const width = parseFloat(style.width) || 0;
                // If progress bar has width > 0 and < 100, might be playing
                if (width > 0 && width < 100) {
                    // Check if it's animating (this is more reliable)
                    const animation = style.animationName || style.webkitAnimationName;
                    if (animation && animation !== 'none') {
                        if (style.animationPlayState === 'running' || 
                            style.webkitAnimationPlayState === 'running') {
                            return true;
                        }
                    }
                }
            }
        } catch (error) {
            console.error('[SoundWrapped] Error checking playback state:', error);
        }
        
        return false;
    }

    // Get current playback position
    function getPlaybackPosition() {
        try {
            const progressBar = document.querySelector('.playbackTimeline__progress, .sc-player-progress');
            if (progressBar) {
                const style = window.getComputedStyle(progressBar);
                const width = parseFloat(style.width) || 0;
                const maxWidth = parseFloat(style.maxWidth) || 100;
                return (width / maxWidth) * 100;
            }

            const audio = document.querySelector('audio');
            if (audio && audio.duration) {
                return (audio.currentTime / audio.duration) * 100;
            }
        } catch (error) {
            console.error('[SoundWrapped] Error getting playback position:', error);
        }
        return 0;
    }

    // Handle track start
    function onTrackStart(trackInfo) {
        console.log('[SoundWrapped] 🎵 Track started:', {
            trackId: trackInfo.trackId,
            title: trackInfo.title,
            artist: trackInfo.artist
        });
        currentTrack = trackInfo;
        playStartTime = Date.now();
        lastTrackId = trackInfo.trackId;
        isPlaying = true;
        console.log('[SoundWrapped] ⏱️ Play timer started at:', new Date(playStartTime).toLocaleTimeString());
    }

    // Handle track stop
    function onTrackStop(trackInfo) {
        if (currentTrack && playStartTime) {
            const playDuration = Date.now() - playStartTime;
            const durationSeconds = Math.round(playDuration / 1000);
            
            console.log('[SoundWrapped] ⏸️ Track stopped. Duration:', durationSeconds, 'seconds');
            
            // Only track if played for minimum duration
            if (playDuration >= MIN_PLAY_DURATION) {
                console.log('[SoundWrapped] ✅ Track played for', durationSeconds, 'seconds (minimum:', MIN_PLAY_DURATION / 1000, 's)');
                console.log('[SoundWrapped] 📤 Sending playback event...');
                sendPlaybackEvent(currentTrack, playDuration);
            } else {
                console.log('[SoundWrapped] ⏭️ Track played too briefly:', durationSeconds, 'seconds (minimum:', MIN_PLAY_DURATION / 1000, 's)');
                console.log('[SoundWrapped] 💡 Play for at least', MIN_PLAY_DURATION / 1000, 'seconds to track');
            }
        } else {
            console.log('[SoundWrapped] ⚠️ Track stopped but no start time recorded');
            console.log('[SoundWrapped] Debug - currentTrack:', currentTrack, 'playStartTime:', playStartTime);
        }
        
        currentTrack = null;
        playStartTime = null;
        isPlaying = false;
        lastTrackId = null; // Reset so next track can start
    }

    // Update playback position
    function updatePlaybackPosition(trackInfo) {
        // This can be used for real-time tracking if needed
        // For now, we just track on stop
    }

    // Send playback event to SoundWrapped backend
    async function sendPlaybackEvent(trackInfo, durationMs) {
        if (!userAuthenticated) {
            console.log('[SoundWrapped] ⚠️ User not authenticated, skipping tracking');
            console.log('[SoundWrapped] 💡 Make sure you\'re logged into SoundWrapped at http://localhost:3000');
            return;
        }

        try {
            const eventData = {
                trackId: trackInfo.trackId,
                artist: trackInfo.artist,
                title: trackInfo.title,
                durationMs: durationMs,
                playbackPositionMs: 0,
                isPlaying: false,
                source: 'browser-extension',
                platform: navigator.platform.toLowerCase().includes('win') ? 'windows' :
                         navigator.platform.toLowerCase().includes('mac') ? 'macos' : 'linux'
            };

            console.log('[SoundWrapped] 📤 Sending playback event:', {
                trackId: eventData.trackId,
                title: eventData.title.substring(0, 30) + '...',
                duration: Math.round(durationMs / 1000) + 's'
            });

            // Use background script to send API request (bypasses CORS)
            const response = await chrome.runtime.sendMessage({
                type: 'TRACK_PLAYBACK',
                data: eventData
            });

            if (response && response.success) {
                console.log('[SoundWrapped] ✅ Playback event tracked successfully:', response);
            } else {
                console.error('[SoundWrapped] ❌ Failed to track playback:', response?.error || 'Unknown error');
            }
        } catch (error) {
            console.error('[SoundWrapped] ❌ Error sending playback event:', error);
            console.error('[SoundWrapped] Make sure backend is running on http://localhost:8080');
        }
    }

    // Detect play/pause button clicks (backup method)
    function setupButtonListeners() {
        document.addEventListener('click', (e) => {
            // Check for play/pause buttons
            const playPauseButton = e.target.closest(
                'button[aria-label*="Play"], ' +
                'button[aria-label*="Pause"], ' +
                'button[aria-label*="play"], ' +
                'button[aria-label*="pause"], ' +
                '.playControl, ' +
                '.sc-button-play, ' +
                '.sc-button-pause, ' +
                '[data-testid="play-button"], ' +
                '[data-testid="pause-button"], ' +
                'button[class*="play"], ' +
                'button[class*="Play"], ' +
                'button[class*="pause"], ' +
                'button[class*="Pause"]'
            );
            
            if (playPauseButton) {
                const ariaLabel = (playPauseButton.getAttribute('aria-label') || '').toLowerCase();
                const classList = playPauseButton.classList;
                const isPauseButton = ariaLabel.includes('pause') || 
                                     classList.contains('sc-button-pause') ||
                                     classList.contains('pause') ||
                                     playPauseButton.querySelector('.sc-button-pause');
                
                console.log('[SoundWrapped] 🔘 Button clicked:', {
                    ariaLabel,
                    isPauseButton,
                    isPlaying,
                    hasCurrentTrack: !!currentTrack,
                    classes: Array.from(classList)
                });
                
                // Give it a moment for the player state to update
                setTimeout(() => {
                    const trackInfo = extractTrackInfo();
                    const currentlyPlaying = isCurrentlyPlaying();
                    
                    // If pause button clicked and we have a track being tracked, stop it
                    if (isPauseButton && currentTrack && isPlaying) {
                        console.log('[SoundWrapped] ⏸️ Pause button clicked - stopping track');
                        onTrackStop(currentTrack);
                    } 
                    // If pause button clicked but we don't have currentTrack, try to get it
                    else if (isPauseButton && trackInfo && trackInfo.trackId && isPlaying) {
                        console.log('[SoundWrapped] ⏸️ Pause button clicked - stopping track (from trackInfo)');
                        onTrackStop(trackInfo);
                    }
                    // If play button clicked and track is not playing, start it
                    else if (!isPauseButton && !currentlyPlaying && trackInfo && trackInfo.trackId) {
                        console.log('[SoundWrapped] ▶️ Play button clicked - starting track');
                        if (trackInfo.trackId !== lastTrackId || lastTrackId === null) {
                            onTrackStart(trackInfo);
                        }
                    }
                }, 300); // Increased delay to allow state to update
            }
            
            // Also handle like button clicks
            const likeButton = e.target.closest('.sc-button-like, .playbackSoundBadge__likeButton, [data-testid="like-button"]');
            if (likeButton && currentTrack) {
                sendLikeEvent(currentTrack.trackId);
            }
        }, true); // Use capture phase
    }
    
    // Detect likes
    function detectLike() {
        // This is now handled in setupButtonListeners
    }

    // Send like event
    async function sendLikeEvent(trackId) {
        if (!userAuthenticated) return;

        try {
            // Use background script to send API request (bypasses CORS)
            const response = await chrome.runtime.sendMessage({
                type: 'TRACK_LIKE',
                trackId: trackId
            });

            if (response && response.success) {
                console.log('[SoundWrapped] ✅ Like event tracked');
            }
        } catch (error) {
            console.error('[SoundWrapped] Error sending like event:', error);
        }
    }

    // Initialize on page load
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

    // Also initialize after delays (SoundCloud loads dynamically)
    setTimeout(init, 2000);
    setTimeout(init, 5000);
    setTimeout(() => {
        console.log('[SoundWrapped] 🔍 Diagnostic check:');
        const trackInfo = extractTrackInfo();
        const playing = isCurrentlyPlaying();
        console.log('  - Track info:', trackInfo);
        console.log('  - Is playing:', playing);
        console.log('  - Authenticated:', userAuthenticated);
        console.log('  - Current URL:', window.location.href);
        console.log('  - Extension state:', {
            isPlaying: isPlaying,
            lastTrackId: lastTrackId,
            currentTrack: currentTrack ? currentTrack.trackId : null,
            playStartTime: playStartTime ? new Date(playStartTime).toLocaleTimeString() : null
        });
    }, 10000);

    // Detect likes
    detectLike();

})();
