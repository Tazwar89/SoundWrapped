import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../services/api'

// Query keys for React Query
export const musicQueryKeys = {
  tracks: ['tracks'] as const,
  artists: ['artists'] as const,
  playlists: ['playlists'] as const,
  wrapped: ['wrapped'] as const,
  musicTasteMap: ['musicTasteMap'] as const,
  featuredTrack: ['featured', 'track'] as const,
  featuredArtist: ['featured', 'artist'] as const,
  featuredGenre: ['featured', 'genre'] as const,
  popularTracks: (limit: number) => ['popular', 'tracks', limit] as const,
}

// Types
export interface Track {
  id: string
  title: string
  artist: string
  duration: number
  playCount: number
  reposts: number
  likes: number
  createdAt: string
  artwork?: string
  platform: 'soundcloud' | 'spotify'
}

export interface Artist {
  name: string
  playCount: number
  listeningHours: number
  trackCount: number
}

export interface Playlist {
  id: string
  title: string
  trackCount: number
  likes: number
  createdAt: string
  artwork?: string
}

export interface WrappedData {
  profile: {
    username: string
    accountAgeYears: number
    followers: number
    tracksUploaded: number
    playlistsCreated: number
  }
  topTracks: Array<{
    rank: number
    title: string
    artist: string
    playCount: number
  }>
  topArtists: Array<{
    rank: number
    artist: string
  }>
  topRepostedTracks: Array<{
    title: string
    reposts: number
  }>
  stats: {
    totalListeningHours: number
    likesGiven: number
    tracksUploaded: number
    commentsPosted: number
    booksYouCouldHaveRead: number
  }
  funFact: string
  peakYear: string
  globalTasteComparison: string
  stories: string[]
  undergroundSupportPercentage?: number
  yearInReviewPoetry?: string
  trendsetterScore?: {
    score: number
    badge: string
    description: string
    visionaryTracks: number
    earlyAdopterTracks: number
  }
  repostKingScore?: {
    repostedTracks: number
    trendingTracks: number
    percentage: number
    badge: string
    description: string
  }
  sonicArchetype?: string
  musicAge?: string
}

export interface MusicTasteLocation {
  city: string
  country: string
  similarity: number
  userCount: number
  topGenres: string[]
  coordinates: {
    lat: number
    lng: number
  }
}

// Transform API responses
const transformTrack = (track: any): Track => ({
  id: track.id?.toString() || '',
  title: track.title || 'Unknown Track',
  artist: track.user?.username || 'Unknown Artist',
  duration: track.duration || 0,
  playCount: track.user_play_count || track.playback_count || 0,
  reposts: track.reposts_count || 0,
  likes: track.likes_count || 0,
  createdAt: track.created_at || new Date().toISOString(),
  artwork: track.artwork_url || track.user?.avatar_url || 'https://via.placeholder.com/300x300/ff5500/ffffff?text=Track',
  platform: 'soundcloud' as const,
})

const transformPlaylist = (playlist: any): Playlist => ({
  id: playlist.id?.toString() || '',
  title: playlist.title || 'Unknown Playlist',
  trackCount: playlist.track_count || 0,
  likes: playlist.likes_count || 0,
  createdAt: playlist.created_at || new Date().toISOString(),
  artwork: playlist.artwork_url || playlist.user?.avatar_url || 'https://via.placeholder.com/300x300/ff6b6b/ffffff?text=Playlist',
})

// React Query hooks
export const useTracks = () => {
  return useQuery({
    queryKey: musicQueryKeys.tracks,
    queryFn: async () => {
      const response = await api.get('/soundcloud/tracks')
      return response.data.map(transformTrack)
    },
    staleTime: 5 * 60 * 1000, // 5 minutes
    enabled: !!localStorage.getItem('accessToken'),
  })
}

export const useArtists = (tracks?: Track[]) => {
  return useQuery({
    queryKey: musicQueryKeys.artists,
    queryFn: async () => {
      if (!tracks || tracks.length === 0) return []
      
      const artistMap = new Map<string, { playCount: number; listeningHours: number; trackCount: number }>()
      
      tracks.forEach(track => {
        const artist = track.artist
        if (artist) {
          const existing = artistMap.get(artist) || { playCount: 0, listeningHours: 0, trackCount: 0 }
          existing.playCount += track.playCount
          existing.listeningHours += track.duration / 1000 / 60 / 60
          existing.trackCount += 1
          artistMap.set(artist, existing)
        }
      })

      return Array.from(artistMap.entries()).map(([name, data]): Artist => ({
        name,
        playCount: data.playCount,
        listeningHours: data.listeningHours,
        trackCount: data.trackCount
      })).sort((a, b) => b.listeningHours - a.listeningHours)
    },
    enabled: !!tracks && tracks.length > 0,
    staleTime: 5 * 60 * 1000,
  })
}

export const usePlaylists = () => {
  return useQuery({
    queryKey: musicQueryKeys.playlists,
    queryFn: async () => {
      const response = await api.get('/soundcloud/playlists')
      return response.data.map(transformPlaylist)
    },
    staleTime: 5 * 60 * 1000,
    enabled: !!localStorage.getItem('accessToken'),
  })
}

export const useWrappedData = () => {
  return useQuery({
    queryKey: musicQueryKeys.wrapped,
    queryFn: async () => {
      const response = await api.get('/soundcloud/wrapped/full', {
        timeout: 60000, // 60 seconds
      })
      return response.data as WrappedData
    },
    staleTime: 10 * 60 * 1000, // 10 minutes - wrapped data doesn't change often
    enabled: !!localStorage.getItem('accessToken'),
    retry: 1,
  })
}

export const useMusicTasteMap = () => {
  return useQuery({
    queryKey: musicQueryKeys.musicTasteMap,
    queryFn: async () => {
      // Update location (non-blocking)
      try {
        await api.post('/tracking/update-location')
      } catch (e) {
        // Silently fail
      }
      
      const response = await api.get('/soundcloud/music-taste-map')
      if (response?.data && Array.isArray(response.data)) {
        return response.data.map((loc: any): MusicTasteLocation => ({
          city: loc.city || 'Unknown',
          country: loc.country || 'Unknown',
          similarity: typeof loc.similarity === 'number' ? loc.similarity : 0,
          userCount: typeof loc.userCount === 'number' ? loc.userCount : 0,
          topGenres: Array.isArray(loc.topGenres) ? loc.topGenres : [],
          coordinates: loc.coordinates || { lat: 0, lng: 0 }
        }))
      }
      return []
    },
    staleTime: 10 * 60 * 1000,
    enabled: !!localStorage.getItem('accessToken'),
  })
}

// Featured content hooks (for homepage - no auth required)
export const useFeaturedTrack = () => {
  return useQuery({
    queryKey: musicQueryKeys.featuredTrack,
    queryFn: async () => {
      const response = await api.get('/soundcloud/featured/track')
      return response?.data && Object.keys(response.data).length > 0 ? response.data : null
    },
    staleTime: 30 * 60 * 1000, // 30 minutes - featured content changes less frequently
    retry: 1,
  })
}

export const useFeaturedArtist = () => {
  return useQuery({
    queryKey: musicQueryKeys.featuredArtist,
    queryFn: async () => {
      const response = await api.get('/soundcloud/featured/artist')
      return response?.data && Object.keys(response.data).length > 0 ? response.data : null
    },
    staleTime: 30 * 60 * 1000,
    retry: 1,
  })
}

export const useFeaturedGenre = () => {
  return useQuery({
    queryKey: musicQueryKeys.featuredGenre,
    queryFn: async () => {
      const response = await api.get('/soundcloud/featured/genre')
      return response?.data || null
    },
    staleTime: 30 * 60 * 1000,
    retry: 1,
  })
}

export const usePopularTracks = (limit: number = 5) => {
  return useQuery({
    queryKey: musicQueryKeys.popularTracks(limit),
    queryFn: async () => {
      const response = await api.get(`/soundcloud/popular/tracks?limit=${limit}`)
      return Array.isArray(response.data) && response.data.length > 0 ? response.data : []
    },
    staleTime: 15 * 60 * 1000, // 15 minutes
    retry: 1,
  })
}

// Prefetch hooks for link hover
export const usePrefetchWrapped = () => {
  const queryClient = useQueryClient()
  return () => {
    queryClient.prefetchQuery({
      queryKey: musicQueryKeys.wrapped,
      queryFn: async () => {
        const response = await api.get('/soundcloud/wrapped/full', { timeout: 60000 })
        return response.data as WrappedData
      },
    })
  }
}

export const usePrefetchDashboard = () => {
  const queryClient = useQueryClient()
  return () => {
    queryClient.prefetchQuery({ queryKey: musicQueryKeys.tracks })
    queryClient.prefetchQuery({ queryKey: musicQueryKeys.playlists })
  }
}

