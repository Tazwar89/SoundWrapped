import React, { useEffect, useRef } from 'react'

interface AnimatedParticleBackgroundProps {
  className?: string
  particleCount?: number
}

const AnimatedParticleBackground: React.FC<AnimatedParticleBackgroundProps> = (props) => {
  const { className = '', particleCount = 20 } = props
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const container = containerRef.current
    if (!container) return

    // Colors from the original design
    const colors = ['#583C87', '#E45A84', '#FFACAC']
    const particleSize = 20 // vmin
    const animationDuration = 6 // seconds
    const count = particleCount ?? 20

    const generateParticles = () => {
      if (!container) return
      
      // Clear existing particles
      container.innerHTML = ''

      // Calculate vmin in pixels for box-shadow (vmin = min(vw, vh))
      const vmin = Math.min(window.innerWidth, window.innerHeight) / 100

      // Generate particles
      for (let i = 1; i <= count; i++) {
        const span = document.createElement('span')
        
        // Random color
        const color = colors[Math.floor(Math.random() * colors.length)]
        
        // Random position
        const top = Math.random() * 100
        const left = Math.random() * 100
        
        // Random animation duration (10s to 16s)
        const duration = (Math.random() * animationDuration * 10) / 10 + 10
        
        // Random animation delay (negative, up to -(animationDuration + 10s))
        const delay = -(Math.random() * (animationDuration + 10) * 10) / 10
        
        // Random transform origin (in vw/vh units)
        const originX = (Math.random() * 50 - 25) // vw units
        const originY = (Math.random() * 50 - 25) // vh units
        
        // Random blur radius and direction
        const blurRadius = ((Math.random() + 0.5) * particleSize * 0.5) * vmin
        const x = Math.random() > 0.5 ? -1 : 1
        const boxShadowX = (particleSize * 2 * x) * vmin
        
        // Set styles
        span.style.width = `${particleSize}vmin`
        span.style.height = `${particleSize}vmin`
        span.style.borderRadius = `${particleSize}vmin`
        span.style.backfaceVisibility = 'hidden'
        span.style.position = 'absolute'
        span.style.color = color
        span.style.backgroundColor = color
        span.style.opacity = '0.6'
        span.style.top = `${top}%`
        span.style.left = `${left}%`
        span.style.animationName = 'particle-move'
        span.style.animationDuration = `${duration}s`
        span.style.animationTimingFunction = 'linear'
        span.style.animationIterationCount = 'infinite'
        span.style.animationDelay = `${delay}s`
        span.style.transformOrigin = `${originX}vw ${originY}vh`
        span.style.boxShadow = `${boxShadowX}px 0 ${blurRadius}px ${color}`
        
        container.appendChild(span)
      }
    }

    // Generate particles initially
    generateParticles()

    // Regenerate particles on resize
    const handleResize = () => {
      generateParticles()
    }

    window.addEventListener('resize', handleResize)

    // Cleanup
    return () => {
      window.removeEventListener('resize', handleResize)
      if (container) {
        container.innerHTML = ''
      }
    }
  }, [particleCount])

  return (
    <>
      <style>{`
        @keyframes particle-move {
          100% {
            transform: translate3d(0, 0, 1px) rotate(360deg);
          }
        }
      `}</style>
      <div
        ref={containerRef}
        className={`fixed inset-0 w-full h-full pointer-events-none overflow-hidden ${className}`}
        style={{
          background: '#3E1E68',
          zIndex: 0
        }}
      />
    </>
  )
}

export default AnimatedParticleBackground
