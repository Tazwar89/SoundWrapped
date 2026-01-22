import React, { useEffect, useRef, useState } from 'react'
import { motion } from 'framer-motion'

interface GenreNode {
  id: string
  name: string
  x: number
  y: number
  z: number
  size: number
  color: string
}

interface GenreConnection {
  from: string
  to: string
  strength: number
}

type ScreenNode = GenreNode & {
  screenX: number
  screenY: number
}

interface GenreConstellationProps {
  genres: Array<{
    genre: string
    trackCount?: number
    listeningMs?: number
    listeningHours?: number
  }>
  connections?: GenreConnection[]
  width?: number
  height?: number
}

const GenreConstellation: React.FC<GenreConstellationProps> = ({
  genres,
  connections = [],
  width = 800,
  height = 600
}) => {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const animationFrameRef = useRef<number>()
  const nodesRef = useRef<GenreNode[]>([])
  const [hoveredNode, setHoveredNode] = useState<string | null>(null)
  const [selectedNode, setSelectedNode] = useState<string | null>(null)

  // Color palette for genres
  const genreColors = [
    '#ff5500', // Orange (SoundCloud primary)
    '#ff8800', // Light orange
    '#ffaa00', // Yellow-orange
    '#ff6600', // Deep orange
    '#ff3300', // Red-orange
    '#ff9900', // Amber
    '#ff7700', // Burnt orange
    '#ff4400', // Coral
  ]

  // Generate nodes from genres
  useEffect(() => {
    if (!genres || genres.length === 0) return

    const maxCount = Math.max(...genres.map(g => g.trackCount || g.listeningMs || 1))
    const nodes: GenreNode[] = genres.map((genre, index) => {
      // Spherical distribution for 3D effect
      const angle = (index / genres.length) * Math.PI * 2
      const elevation = (index / genres.length) * Math.PI - Math.PI / 2
      const radius = 200

      const x = Math.cos(elevation) * Math.cos(angle) * radius
      const y = Math.sin(elevation) * radius
      const z = Math.cos(elevation) * Math.sin(angle) * radius

      const count = genre.trackCount || genre.listeningMs || 1
      const normalizedSize = Math.max(10, Math.min(40, (count / maxCount) * 30 + 10))
      
      const colorIndex = index % genreColors.length
      const color = genreColors[colorIndex]

      return {
        id: genre.genre || 'Unknown',
        name: genre.genre || 'Unknown',
        x,
        y,
        z,
        size: normalizedSize,
        color
      }
    })

    nodesRef.current = nodes
  }, [genres])

  // 3D to 2D projection
  const project3D = (x: number, y: number, z: number, cameraZ: number = 500): [number, number] => {
    const scale = cameraZ / (cameraZ + z)
    return [
      width / 2 + x * scale,
      height / 2 + y * scale
    ]
  }

  // Draw the constellation
  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return

    const ctx = canvas.getContext('2d')
    if (!ctx) return

    canvas.width = width
    canvas.height = height

    let rotationX = 0
    let rotationY = 0
    let cameraZ = 500

    const animate = () => {
      ctx.clearRect(0, 0, width, height)

      // Slow rotation for visual effect
      rotationY += 0.002
      rotationX += 0.001

      // Rotate nodes around Y axis
      const rotatedNodes = nodesRef.current.map(node => {
        const cosY = Math.cos(rotationY)
        const sinY = Math.sin(rotationY)
        const cosX = Math.cos(rotationX)
        const sinX = Math.sin(rotationX)

        // Rotate around Y axis
        let x = node.x * cosY - node.z * sinY
        let z = node.x * sinY + node.z * cosY
        let y = node.y

        // Rotate around X axis
        const yNew = y * cosX - z * sinX
        z = y * sinX + z * cosX
        y = yNew

        return {
          ...node,
          x,
          y,
          z
        }
      })

      // Draw connections
      if (connections.length > 0) {
        ctx.strokeStyle = 'rgba(255, 85, 0, 0.2)'
        ctx.lineWidth = 1
        connections.forEach(conn => {
          const fromNode = rotatedNodes.find(n => n.id === conn.from)
          const toNode = rotatedNodes.find(n => n.id === conn.to)
          if (fromNode && toNode) {
            const [x1, y1] = project3D(fromNode.x, fromNode.y, fromNode.z, cameraZ)
            const [x2, y2] = project3D(toNode.x, toNode.y, toNode.z, cameraZ)
            ctx.globalAlpha = conn.strength * 0.3
            ctx.beginPath()
            ctx.moveTo(x1, y1)
            ctx.lineTo(x2, y2)
            ctx.stroke()
          }
        })
        ctx.globalAlpha = 1
      } else {
        // Auto-generate connections based on proximity
        rotatedNodes.forEach((node, i) => {
          rotatedNodes.slice(i + 1).forEach(otherNode => {
            const distance = Math.sqrt(
              Math.pow(node.x - otherNode.x, 2) +
              Math.pow(node.y - otherNode.y, 2) +
              Math.pow(node.z - otherNode.z, 2)
            )
            if (distance < 250) {
              const [x1, y1] = project3D(node.x, node.y, node.z, cameraZ)
              const [x2, y2] = project3D(otherNode.x, otherNode.y, otherNode.z, cameraZ)
              const strength = 1 - (distance / 250)
              ctx.strokeStyle = `rgba(255, 85, 0, ${strength * 0.2})`
              ctx.lineWidth = strength * 2
              ctx.beginPath()
              ctx.moveTo(x1, y1)
              ctx.lineTo(x2, y2)
              ctx.stroke()
            }
          })
        })
      }

      // Sort nodes by Z for proper depth rendering
      const sortedNodes = [...rotatedNodes].sort((a, b) => b.z - a.z)

      // Draw nodes
      sortedNodes.forEach(node => {
        const [x, y] = project3D(node.x, node.y, node.z, cameraZ)
        const scale = cameraZ / (cameraZ + node.z)
        const displaySize = node.size * scale

        // Skip if off-screen
        if (x < -displaySize || x > width + displaySize || 
            y < -displaySize || y > height + displaySize) {
          return
        }

        // Glow effect
        const gradient = ctx.createRadialGradient(x, y, 0, x, y, displaySize * 2)
        gradient.addColorStop(0, node.color)
        gradient.addColorStop(0.5, node.color + '80')
        gradient.addColorStop(1, node.color + '00')

        ctx.fillStyle = gradient
        ctx.beginPath()
        ctx.arc(x, y, displaySize * 2, 0, Math.PI * 2)
        ctx.fill()

        // Main node
        ctx.fillStyle = node.color
        ctx.beginPath()
        ctx.arc(x, y, displaySize, 0, Math.PI * 2)
        ctx.fill()

        // Highlight if hovered or selected
        if (hoveredNode === node.id || selectedNode === node.id) {
          ctx.strokeStyle = '#ffffff'
          ctx.lineWidth = 2
          ctx.beginPath()
          ctx.arc(x, y, displaySize + 3, 0, Math.PI * 2)
          ctx.stroke()
        }

        // Label
        if (hoveredNode === node.id || selectedNode === node.id || displaySize > 15) {
          ctx.fillStyle = '#ffffff'
          ctx.font = `${Math.max(10, displaySize * 0.6)}px sans-serif`
          ctx.textAlign = 'center'
          ctx.textBaseline = 'middle'
          ctx.fillText(node.name, x, y + displaySize + 15)
        }
      })

      animationFrameRef.current = requestAnimationFrame(animate)
    }

    animate()

    // Mouse interaction
    const handleMouseMove = (e: MouseEvent) => {
      const rect = canvas.getBoundingClientRect()
      const mouseX = e.clientX - rect.left
      const mouseY = e.clientY - rect.top

      // Find closest node
      let closestNode: ScreenNode | null = null
      let minDistance = Infinity

      const currentNodes: ScreenNode[] = nodesRef.current.map(node => {
        const [x, y] = project3D(node.x, node.y, node.z, cameraZ)
        return { ...node, screenX: x, screenY: y }
      })

      currentNodes.forEach(node => {
        const distance = Math.sqrt(
          Math.pow(mouseX - node.screenX, 2) +
          Math.pow(mouseY - node.screenY, 2)
        )
        if (distance < node.size + 20 && distance < minDistance) {
          minDistance = distance
          closestNode = node
        }
      })

      setHoveredNode(closestNode?.id || null)
    }

    const handleClick = () => {
      if (hoveredNode) {
        setSelectedNode(selectedNode === hoveredNode ? null : hoveredNode)
      }
    }

    canvas.addEventListener('mousemove', handleMouseMove)
    canvas.addEventListener('click', handleClick)

    return () => {
      if (animationFrameRef.current) {
        cancelAnimationFrame(animationFrameRef.current)
      }
      canvas.removeEventListener('mousemove', handleMouseMove)
      canvas.removeEventListener('click', handleClick)
    }
  }, [width, height, hoveredNode, selectedNode, connections])

  if (!genres || genres.length === 0) {
    return (
      <div className="flex items-center justify-center h-full text-white/60">
        <p>No genre data available</p>
      </div>
    )
  }

  return (
    <div className="relative w-full h-full">
      <canvas
        ref={canvasRef}
        className="w-full h-full cursor-pointer"
        style={{ background: 'transparent' }}
      />
      {selectedNode && (
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          className="absolute bottom-4 left-4 right-4 bg-black/80 backdrop-blur-sm rounded-lg p-4 border border-orange-500/30"
        >
          <h4 className="text-orange-400 font-semibold mb-2">{selectedNode}</h4>
          {genres.find(g => g.genre === selectedNode) && (
            <div className="text-sm text-white/80 space-y-1">
              {genres.find(g => g.genre === selectedNode)?.trackCount && (
                <p>Tracks: {genres.find(g => g.genre === selectedNode)?.trackCount}</p>
              )}
              {genres.find(g => g.genre === selectedNode)?.listeningHours && (
                <p>Hours: {genres.find(g => g.genre === selectedNode)?.listeningHours.toFixed(1)}</p>
              )}
            </div>
          )}
        </motion.div>
      )}
    </div>
  )
}

export default GenreConstellation

