// Check connection status
async function checkStatus() {
    try {
        const response = await fetch('http://localhost:8080/api/tracking/health');
        if (response.ok) {
            document.getElementById('status').textContent = '✅ Connected to SoundWrapped';
            document.getElementById('status').className = 'status connected';
        } else {
            throw new Error('Not connected');
        }
    } catch (error) {
        document.getElementById('status').textContent = '❌ Not connected to SoundWrapped';
        document.getElementById('status').className = 'status disconnected';
    }
}

// Check auth status
async function checkAuth() {
    try {
        const response = await fetch('http://localhost:8080/api/soundcloud/debug/tokens');
        if (response.ok) {
            const data = await response.json();
            if (data.hasAccessToken) {
                document.getElementById('status').textContent = '✅ Connected & Authenticated';
                document.getElementById('status').className = 'status connected';
            } else {
                document.getElementById('status').textContent = '⚠️ Connected but not authenticated';
                document.getElementById('status').className = 'status disconnected';
            }
        }
    } catch (error) {
        // Ignore
    }
}

// Open SoundWrapped
document.getElementById('openSoundWrapped').addEventListener('click', () => {
    chrome.tabs.create({ url: 'http://localhost:3000' });
});

// Initialize
checkStatus();
checkAuth();
