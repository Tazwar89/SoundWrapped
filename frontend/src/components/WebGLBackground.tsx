import React, { useEffect, useRef } from 'react'

interface WebGLBackgroundProps {
  className?: string
}

const WebGLBackground: React.FC<WebGLBackgroundProps> = ({ className = '' }) => {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const animationFrameRef = useRef<number>()

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return

    const gl = canvas.getContext('webgl2', {
      alpha: true,
      antialias: true,
      premultipliedAlpha: false,
      powerPreference: 'high-performance'
    })

    if (!gl) {
      // Fallback: Hide canvas if WebGL2 not supported
      canvas.style.display = 'none'
      return
    }

    // Set canvas size
    const resizeCanvas = () => {
      const dpr = window.devicePixelRatio || 1
      canvas.width = window.innerWidth * dpr
      canvas.height = window.innerHeight * dpr
      canvas.style.width = `${window.innerWidth}px`
      canvas.style.height = `${window.innerHeight}px`
      gl.viewport(0, 0, canvas.width, canvas.height)
    }

    resizeCanvas()
    window.addEventListener('resize', resizeCanvas)

    // Vertex shader source
    const vertexShaderSource = `#version 300 es
      in vec2 a_position;
      in vec2 a_texCoord;
      
      out vec2 v_texCoord;
      
      void main() {
        gl_Position = vec4(a_position, 0.0, 1.0);
        v_texCoord = a_texCoord;
      }
    `

    // Fragment shader source - Luma-inspired dynamic gradient with particles
    const fragmentShaderSource = `#version 300 es
      precision highp float;
      
      in vec2 v_texCoord;
      out vec4 fragColor;
      
      uniform float u_time;
      uniform vec2 u_resolution;
      uniform vec2 u_mouse;
      
      // Luma-inspired color palette: purple → pink/red → yellow/orange
      // Based on Luma's default-gradient: #8a18a8 → #ce2756 → #e7a90d
      vec3 purple = vec3(0.529, 0.094, 0.659);      // #8a18a8
      vec3 pink = vec3(0.808, 0.153, 0.337);        // #ce2756
      vec3 red = vec3(0.812, 0.165, 0.333);         // #cf2a55
      vec3 yellow = vec3(0.906, 0.663, 0.051);     // #e7a90d
      
      // Improved noise function
      float random(vec2 st) {
        return fract(sin(dot(st.xy, vec2(12.9898, 78.233))) * 43758.5453123);
      }
      
      float noise(vec2 p) {
        vec2 i = floor(p);
        vec2 f = fract(p);
        f = f * f * (3.0 - 2.0 * f);
        
        float a = random(i);
        float b = random(i + vec2(1.0, 0.0));
        float c = random(i + vec2(0.0, 1.0));
        float d = random(i + vec2(1.0, 1.0));
        
        return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
      }
      
      float fbm(vec2 p) {
        float value = 0.0;
        float amplitude = 0.5;
        float frequency = 1.0;
        
        for (int i = 0; i < 5; i++) {
          value += amplitude * noise(p * frequency);
          frequency *= 2.0;
          amplitude *= 0.5;
        }
        
        return value;
      }
      
      // Bokeh-style particles (inspired by Luma's particle effects)
      float bokeh(vec2 uv, vec2 center, float size, float intensity) {
        float dist = distance(uv, center);
        float circle = 1.0 - smoothstep(0.0, size, dist);
        return circle * intensity;
      }
      
      void main() {
        vec2 uv = v_texCoord;
        vec2 center = vec2(0.5, 0.5);
        float dist = distance(uv, center);
        
        // Luma-style diagonal gradient (-45deg, like CSS linear-gradient(-45deg, ...))
        // For -45deg: gradient runs from top-left (0,0) to bottom-right (1,1)
        // Position along diagonal: normalize (x + y) from [0, 2] to [0, 1]
        float diagonal = (uv.x + uv.y) * 0.5;
        
        // Subtle animation - slow drift
        float timeOffset = sin(u_time * 0.15) * 0.03;
        float gradientPos = diagonal + timeOffset;
        gradientPos = mod(gradientPos, 1.0);
        
        // Luma gradient stops: purple(0%) → pink(51.59%) → red(51.6%) → yellow(100%)
        vec3 gradientColor;
        if (gradientPos < 0.5159) {
          // Purple to pink
          float t = gradientPos / 0.5159;
          gradientColor = mix(purple, pink, t);
        } else if (gradientPos < 0.516) {
          // Pink to red (very small transition)
          gradientColor = mix(pink, red, 0.5);
        } else {
          // Red to yellow
          float t = (gradientPos - 0.516) / (1.0 - 0.516);
          gradientColor = mix(red, yellow, t);
        }
        
        // Add subtle flowing movement
        vec2 flowUV = uv * 2.0;
        flowUV.x += sin(u_time * 0.3 + uv.y * 2.0) * 0.05;
        flowUV.y += cos(u_time * 0.25 + uv.x * 2.0) * 0.05;
        
        // Subtle noise texture for depth
        float n = fbm(flowUV * 1.5 + u_time * 0.1);
        gradientColor = mix(gradientColor, gradientColor * 1.1, n * 0.15);
        
        // Bokeh particles (subtle, elegant like Luma's champagne/bokeh particles)
        float particles = 0.0;
        vec2 particleUV = uv * 2.5;
        
        // Multiple subtle particle layers with varying sizes and speeds
        for (int i = 0; i < 6; i++) {
          float angle = float(i) * 1.047; // ~60 degrees spacing
          float speed = 0.08 + float(i) * 0.03;
          vec2 offset = vec2(
            cos(angle + u_time * speed),
            sin(angle + u_time * (speed * 1.3))
          ) * (0.25 + float(i) * 0.05);
          
          vec2 particleCenter = vec2(0.5) + offset;
          float size = 0.12 + float(i) * 0.02;
          float intensity = 0.04 + float(i) * 0.01;
          float pulse = 0.6 + 0.4 * sin(u_time * 1.5 + float(i) * 0.8);
          
          particles += bokeh(particleUV, particleCenter, size, intensity) * pulse;
        }
        
        // Soft, warm particle overlay (champagne-like)
        gradientColor += particles * vec3(1.0, 0.95, 0.85) * 0.35;
        
        // Radial glow from center (like Luma's subtle depth)
        float radialGlow = 1.0 - smoothstep(0.0, 0.8, dist);
        gradientColor = mix(gradientColor * 0.7, gradientColor, radialGlow);
        
        // Mouse interaction (subtle, like Luma's interactive feel)
        vec2 mouseUV = u_mouse / u_resolution;
        if (mouseUV.x > 0.0 && mouseUV.y > 0.0) {
          float mouseDist = distance(uv, mouseUV);
          float mouseGlow = smoothstep(0.4, 0.0, mouseDist);
          gradientColor += mouseGlow * vec3(0.15, 0.1, 0.2) * 0.3;
        }
        
        // Soft vignette for depth
        float vignette = 1.0 - smoothstep(0.4, 1.2, dist);
        gradientColor *= (0.75 + vignette * 0.25);
        
        // Subtle brightness adjustment for elegance
        gradientColor = pow(gradientColor, vec3(0.95));
        
        // Luma-style opacity - subtle but visible
        fragColor = vec4(gradientColor, 0.5);
      }
    `

    // Compile shader
    const compileShader = (source: string, type: number): WebGLShader | null => {
      const shader = gl.createShader(type)
      if (!shader) return null

      gl.shaderSource(shader, source)
      gl.compileShader(shader)

      if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
        console.error('Shader compile error:', gl.getShaderInfoLog(shader))
        gl.deleteShader(shader)
        return null
      }

      return shader
    }

    const vertexShader = compileShader(vertexShaderSource, gl.VERTEX_SHADER)
    const fragmentShader = compileShader(fragmentShaderSource, gl.FRAGMENT_SHADER)

    if (!vertexShader || !fragmentShader) return

    // Create program
    const program = gl.createProgram()
    if (!program) return

    gl.attachShader(program, vertexShader)
    gl.attachShader(program, fragmentShader)
    gl.linkProgram(program)

    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
      console.error('Program link error:', gl.getProgramInfoLog(program))
      return
    }

    // Setup geometry (full screen quad)
    const positionBuffer = gl.createBuffer()
    gl.bindBuffer(gl.ARRAY_BUFFER, positionBuffer)
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([
      -1, -1,  // bottom left
       1, -1,  // bottom right
      -1,  1,  // top left
       1,  1   // top right
    ]), gl.STATIC_DRAW)

    const texCoordBuffer = gl.createBuffer()
    gl.bindBuffer(gl.ARRAY_BUFFER, texCoordBuffer)
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([
      0, 0,  // bottom left
      1, 0,  // bottom right
      0, 1,  // top left
      1, 1   // top right
    ]), gl.STATIC_DRAW)

    // Get attribute locations
    const positionLocation = gl.getAttribLocation(program, 'a_position')
    const texCoordLocation = gl.getAttribLocation(program, 'a_texCoord')
    const timeLocation = gl.getUniformLocation(program, 'u_time')
    const resolutionLocation = gl.getUniformLocation(program, 'u_resolution')
    const mouseLocation = gl.getUniformLocation(program, 'u_mouse')

    // Mouse position
    let mouseX = 0
    let mouseY = 0

    const handleMouseMove = (e: MouseEvent) => {
      mouseX = e.clientX
      mouseY = e.clientY
    }

    window.addEventListener('mousemove', handleMouseMove)

    // Animation loop
    let startTime = Date.now()

    const animate = () => {
      const currentTime = Date.now()
      const elapsed = (currentTime - startTime) / 1000.0

      gl.useProgram(program)

      // Set uniforms
      gl.uniform1f(timeLocation, elapsed)
      gl.uniform2f(resolutionLocation, canvas.width, canvas.height)
      gl.uniform2f(mouseLocation, mouseX, canvas.height - mouseY)

      // Enable blending for transparency
      gl.enable(gl.BLEND)
      gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA)

      // Bind and draw
      gl.bindBuffer(gl.ARRAY_BUFFER, positionBuffer)
      gl.enableVertexAttribArray(positionLocation)
      gl.vertexAttribPointer(positionLocation, 2, gl.FLOAT, false, 0, 0)

      gl.bindBuffer(gl.ARRAY_BUFFER, texCoordBuffer)
      gl.enableVertexAttribArray(texCoordLocation)
      gl.vertexAttribPointer(texCoordLocation, 2, gl.FLOAT, false, 0, 0)

      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4)

      animationFrameRef.current = requestAnimationFrame(animate)
    }

    animate()

    // Cleanup
    return () => {
      window.removeEventListener('resize', resizeCanvas)
      window.removeEventListener('mousemove', handleMouseMove)
      if (animationFrameRef.current) {
        cancelAnimationFrame(animationFrameRef.current)
      }
      gl.deleteProgram(program)
      gl.deleteShader(vertexShader)
      gl.deleteShader(fragmentShader)
      gl.deleteBuffer(positionBuffer)
      gl.deleteBuffer(texCoordBuffer)
    }
  }, [])

  return (
    <canvas
      ref={canvasRef}
      className={`fixed inset-0 w-full h-full pointer-events-none ${className}`}
      style={{ 
        zIndex: 0,
        mixBlendMode: 'normal',
        willChange: 'transform'
      }}
    />
  )
}

export default WebGLBackground
