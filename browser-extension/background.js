/**
 * SoundWrapped Browser Extension - Background Service Worker
 */

// Keep extension alive
chrome.runtime.onInstalled.addListener(() => {
    console.log('[SoundWrapped Background] Extension installed');
});

// Handle messages from content script
chrome.runtime.onMessage?.addListener((message, sender, sendResponse) => {
    // Handle async responses
    if (message && message.type === 'CHECK_AUTH') {
        checkAuthStatus().then(authResult => {
            try {
                sendResponse({ authenticated: authResult });
            } catch (e) {
                // Response already sent or channel closed
                console.log('[SoundWrapped Background] Response channel closed');
            }
        }).catch(error => {
            try {
                sendResponse({ authenticated: false, error: error.message });
            } catch (e) {
                console.log('[SoundWrapped Background] Response channel closed');
            }
        });
        return true; // Keep channel open for async response
    }
    
    if (message && message.type === 'TRACK_PLAYBACK') {
        trackPlayback(message.data).then(result => {
            try {
                sendResponse(result);
            } catch (e) {
                console.log('[SoundWrapped Background] Response channel closed');
            }
        }).catch(error => {
            try {
                sendResponse({ success: false, error: error.message });
            } catch (e) {
                console.log('[SoundWrapped Background] Response channel closed');
            }
        });
        return true; // Keep channel open for async response
    }
    
    if (message && message.type === 'TRACK_LIKE') {
        trackLike(message.trackId).then(result => {
            try {
                sendResponse(result);
            } catch (e) {
                console.log('[SoundWrapped Background] Response channel closed');
            }
        }).catch(error => {
            try {
                sendResponse({ success: false, error: error.message });
            } catch (e) {
                console.log('[SoundWrapped Background] Response channel closed');
            }
        });
        return true; // Keep channel open for async response
    }
    
    return false;
});

// Periodically check if user is authenticated and refresh tokens if needed
if (chrome.alarms) {
    try {
        // Check auth status every 5 minutes
        chrome.alarms.create('checkAuth', { periodInMinutes: 5 });
        // Refresh token proactively every hour (before expiration)
        chrome.alarms.create('refreshToken', { periodInMinutes: 60 });
        
        chrome.alarms.onAlarm.addListener((alarm) => {
            if (alarm.name === 'checkAuth') {
                // Check authentication status
                checkAuthStatus();
            } else if (alarm.name === 'refreshToken') {
                // Proactively refresh token to prevent expiration
                refreshTokenProactively();
            }
        });
    } catch (error) {
        console.log('[SoundWrapped Background] Alarms API not available:', error);
    }
}

async function checkAuthStatus() {
    try {
        // Background scripts can make cross-origin requests without CORS
        const response = await fetch('http://localhost:8080/api/soundcloud/debug/tokens', {
            method: 'GET'
            // Note: credentials not needed - backend uses TokenStore for auth
        });
        
        if (response.ok) {
            const data = await response.json();
            const isAuthenticated = data.hasAccessToken === true;
            const needsRefresh = data.needsRefresh === true;
            
            // If token needs refresh, proactively refresh it
            if (needsRefresh && isAuthenticated) {
                console.log('[SoundWrapped Background] 🔄 Token needs refresh, refreshing now...');
                await refreshTokenProactively();
                // Re-check auth status after refresh
                const refreshResponse = await fetch('http://localhost:8080/api/soundcloud/debug/tokens', {
                    method: 'GET'
                });
                if (refreshResponse.ok) {
                    const refreshData = await refreshResponse.json();
                    const refreshedAuth = refreshData.hasAccessToken === true;
                    if (chrome.storage && chrome.storage.local) {
                        chrome.storage.local.set({ authenticated: refreshedAuth });
                    }
                    return refreshedAuth;
                }
            }
            
            if (chrome.storage && chrome.storage.local) {
                chrome.storage.local.set({ authenticated: isAuthenticated });
            }
            
            return isAuthenticated;
        }
        return false;
    } catch (error) {
        console.log('[SoundWrapped Background] Auth check failed:', error.message);
        if (chrome.storage && chrome.storage.local) {
            chrome.storage.local.set({ authenticated: false });
        }
        return false;
    }
}

async function trackPlayback(eventData) {
    try {
        // Background scripts can make cross-origin requests without CORS
        const response = await fetch('http://localhost:8080/api/tracking/system-playback', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            // Note: credentials not needed - backend uses TokenStore for auth
            body: JSON.stringify(eventData)
        });
        
        if (response.ok) {
            const result = await response.json();
            console.log('[SoundWrapped Background] ✅ Playback tracked:', result);
            return { success: true, ...result };
        } else {
            const errorText = await response.text();
            console.error('[SoundWrapped Background] ❌ Playback tracking failed:', response.status, errorText);
            return { success: false, error: `HTTP ${response.status}: ${errorText}` };
        }
    } catch (error) {
        console.error('[SoundWrapped Background] ❌ Playback tracking error:', error);
        return { success: false, error: error.message };
    }
}

async function trackLike(trackId) {
    try {
        // Background scripts can make cross-origin requests without CORS
        const response = await fetch(`http://localhost:8080/api/tracking/system-like?trackId=${encodeURIComponent(trackId)}`, {
            method: 'POST'
            // Note: credentials not needed - backend uses TokenStore for auth
        });
        
        if (response.ok) {
            const result = await response.json();
            console.log('[SoundWrapped Background] ✅ Like tracked:', result);
            return { success: true, ...result };
        } else {
            const errorText = await response.text();
            console.error('[SoundWrapped Background] ❌ Like tracking failed:', response.status, errorText);
            return { success: false, error: `HTTP ${response.status}: ${errorText}` };
        }
    } catch (error) {
        console.error('[SoundWrapped Background] ❌ Like tracking error:', error);
        return { success: false, error: error.message };
    }
}

async function refreshTokenProactively() {
    try {
        console.log('[SoundWrapped Background] 🔄 Proactively refreshing token...');
        const response = await fetch('http://localhost:8080/api/soundcloud/refresh-token', {
            method: 'POST'
        });
        
        if (response.ok) {
            const result = await response.json();
            if (result.success) {
                console.log('[SoundWrapped Background] ✅ Token refreshed proactively');
                // Update auth status after refresh
                await checkAuthStatus();
            } else {
                console.log('[SoundWrapped Background] ⚠️ Token refresh returned false:', result.message);
            }
            return result;
        } else {
            const errorText = await response.text();
            console.error('[SoundWrapped Background] ❌ Token refresh failed:', response.status, errorText);
            return { success: false, error: `HTTP ${response.status}: ${errorText}` };
        }
    } catch (error) {
        console.error('[SoundWrapped Background] ❌ Token refresh error:', error);
        return { success: false, error: error.message };
    }
}
