# SoundWrapped
SoundWrapped is a personal music insights tool inspired by Spotify Wrapped. It uses SoundCloud's public API to generate yearly playback summaries for users, highlighting their most-played tracks, top artists, and overall listening habits.

## API Usage
1. Personal Listening Stats
	-	Top Artists / Tracks / Playlists → Rank user’s top 5–10 for the year.
	-	Minutes Listened → Aggregate track durations × play counts.
	-	Listening Habits → Most active day/time.

2. Fun/Engagement Features
	-	“Roast My SoundCloud” → Lighthearted comments on their taste.
	- Aesthetic Wrapped Pages → Generate a shareable, visual summary like Spotify Wrapped (charts, graphics, etc.).

3. Global & Community Stats
	-	Most Popular Artists on SoundCloud.
	-	Taste Twin Map → Like Spotify Wrapped, cluster users by overlap in liked artists/tracks.

4. Recommendation Features
	-	Related Tracks API → Use /tracks/{track_urn}/related to suggest “Your Top Track + Recommended Tracks” → creates a discovery section.
	-	Playlist Auto-Building → “Your Wrapped Playlist” = user’s top tracks + related ones.

5. Extended Add-ons
	•	Year-over-Year Comparison → Compare this year’s Wrapped vs last year’s (“Your listening shifted from EDM to Lo-fi”).
	•	Hidden Gems Section → Identify lesser-known artists among their favorites (low follower counts).

## Tech Stack
- **Frontend:** React (Next.js optional)
- **Backend:** Java + Spring Boot
- **API:** SoundCloud API

## Getting Started
1. Clone the repo:
   ```bash
   git clone https://github.com/<your-username>/SoundWrapped.git
   ```
