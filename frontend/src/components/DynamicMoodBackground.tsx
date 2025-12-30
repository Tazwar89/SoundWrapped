import React from 'react'
import WebGLBackground from './WebGLBackground'

interface DynamicMoodBackgroundProps {
  tracks?: Array<{
    genre?: string
    playback_count?: number
    likes_count?: number
    reposts_count?: number
    [key: string]: any
  }>
  className?: string
}

/**
 * Dynamic Mood Background that changes colors based on track energy.
 * Analyzes the current tracks/genres and adjusts the background colors accordingly.
 */
const DynamicMoodBackground: React.FC<DynamicMoodBackgroundProps> = ({ 
  tracks = [],
  className = ''
}) => {
  // Analyze track energy based on genre and engagement metrics
  const analyzeEnergy = () => {
    if (!tracks || tracks.length === 0) {
      // Default SoundCloud orange-black gradient
      return {
        color1: [1.0, 0.333, 0.0],  // Orange #FF5500
        color2: [0.5, 0.165, 0.0],  // Dark orange
        color3: [0.0, 0.0, 0.0],    // Black
        speed: 1.5
      }
    }

    // High-energy genres
    const highEnergyGenres = ['edm', 'electronic', 'dubstep', 'trap', 'house', 'techno', 'trance', 'hardstyle', 'drum and bass', 'drum & bass']
    const mediumEnergyGenres = ['hip hop', 'hip-hop', 'rap', 'pop', 'rock', 'metal', 'punk', 'indie', 'alternative']
    const lowEnergyGenres = ['ambient', 'chill', 'lo-fi', 'lofi', 'jazz', 'blues', 'classical', 'acoustic', 'folk', 'soul']

    // Calculate energy score
    let totalEnergy = 0
    let trackCount = 0

    tracks.forEach(track => {
      const genre = (track.genre || '').toLowerCase()
      let energy = 0.5 // Default medium energy

      if (highEnergyGenres.some(g => genre.includes(g))) {
        energy = 0.9
      } else if (mediumEnergyGenres.some(g => genre.includes(g))) {
        energy = 0.6
      } else if (lowEnergyGenres.some(g => genre.includes(g))) {
        energy = 0.2
      }

      // Boost energy based on engagement (high engagement = more energy)
      const playbackCount = track.playback_count || 0
      const likesCount = track.likes_count || 0
      const repostsCount = track.reposts_count || 0
      const engagement = (playbackCount + likesCount * 10 + repostsCount * 20) / 10000
      energy = Math.min(1.0, energy + Math.min(0.3, engagement))

      totalEnergy += energy
      trackCount++
    })

    const avgEnergy = trackCount > 0 ? totalEnergy / trackCount : 0.5

    // Map energy to color palette
    // High energy: Fiery reds/oranges (high BPM, energetic)
    // Medium energy: Warm oranges/yellows (moderate tempo)
    // Low energy: Deep blues/purples (chill, ambient)

    if (avgEnergy > 0.7) {
      // High energy - Fiery reds and bright oranges
      return {
        color1: [1.0, 0.2, 0.0],    // Bright red-orange
        color2: [1.0, 0.4, 0.0],     // Orange
        color3: [0.3, 0.0, 0.0],    // Dark red
        speed: 2.0                   // Faster animation
      }
    } else if (avgEnergy > 0.4) {
      // Medium energy - Warm oranges (default SoundCloud style)
      return {
        color1: [1.0, 0.333, 0.0],  // Orange #FF5500
        color2: [0.5, 0.165, 0.0],  // Dark orange
        color3: [0.0, 0.0, 0.0],    // Black
        speed: 1.5
      }
    } else {
      // Low energy - Deep blues and purples (chill vibes)
      return {
        color1: [0.2, 0.3, 0.8],    // Deep blue
        color2: [0.1, 0.1, 0.4],    // Darker blue
        color3: [0.0, 0.0, 0.1],    // Very dark blue
        speed: 0.8                   // Slower, more relaxed animation
      }
    }
  }

  const energyConfig = analyzeEnergy()

  return (
    <WebGLBackground 
      className={className}
      color1={energyConfig.color1 as [number, number, number]}
      color2={energyConfig.color2 as [number, number, number]}
      color3={energyConfig.color3 as [number, number, number]}
      speed={energyConfig.speed}
    />
  )
}

export default DynamicMoodBackground

