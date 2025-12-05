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

    // Vertex shader (adapted from provided shader)
    const vertexShaderSource = `#version 300 es
      precision highp float;
      
      in vec3 position;
      in vec2 uv;
      out vec2 vUv;
      
      void main() {
        vUv = uv;
        gl_Position = vec4(position, 1.0);
      }
    `

    // Fragment shader (from provided shader)
    const fragmentShaderSource = `#version 300 es
      precision highp float;
      
      in vec2 vUv;
      out vec4 pc_fragColor;
      
      uniform float uTime;
      uniform float uSpeed;
      uniform float uNoiseDensity;
      uniform float uNoiseStrength;
      uniform float uBrightness;
      uniform float uAlpha;
      uniform vec3 uColor1;
      uniform vec3 uColor2;
      uniform vec3 uColor3;
      uniform vec2 uResolution;
      uniform vec2 uAspectRatio;
      uniform vec2 uOffset;
      
      vec3 mod289(vec3 x) {
        return x - floor(x * (1.0 / 289.0)) * 289.0;
      }
      
      vec4 mod289(vec4 x) {
        return x - floor(x * (1.0 / 289.0)) * 289.0;
      }
      
      vec4 permute(vec4 x) {
        return mod289(((x*34.0)+1.0)*x);
      }
      
      vec4 taylorInvSqrt(vec4 r) {
        return 1.79284291400159 - 0.85373472095314 * r;
      }
      
      vec3 fade(vec3 t) {
        return t*t*t*(t*(t*6.0-15.0)+10.0);
      }
      
      float cnoise(vec3 P) {
        vec3 Pi0 = floor(P);
        vec3 Pi1 = Pi0 + vec3(1.0);
        Pi0 = mod289(Pi0);
        Pi1 = mod289(Pi1);
        vec3 Pf0 = fract(P);
        vec3 Pf1 = Pf0 - vec3(1.0);
        vec4 ix = vec4(Pi0.x, Pi1.x, Pi0.x, Pi1.x);
        vec4 iy = vec4(Pi0.yy, Pi1.yy);
        vec4 iz0 = Pi0.zzzz;
        vec4 iz1 = Pi1.zzzz;
        
        vec4 ixy = permute(permute(ix) + iy);
        vec4 ixy0 = permute(ixy + iz0);
        vec4 ixy1 = permute(ixy + iz1);
        
        vec4 gx0 = ixy0 * (1.0 / 7.0);
        vec4 gy0 = fract(floor(gx0) * (1.0 / 7.0)) - 0.5;
        gx0 = fract(gx0);
        vec4 gz0 = vec4(0.5) - abs(gx0) - abs(gy0);
        vec4 sz0 = step(gz0, vec4(0.0));
        gx0 -= sz0 * (step(0.0, gx0) - 0.5);
        gy0 -= sz0 * (step(0.0, gy0) - 0.5);
        
        vec4 gx1 = ixy1 * (1.0 / 7.0);
        vec4 gy1 = fract(floor(gx1) * (1.0 / 7.0)) - 0.5;
        gx1 = fract(gx1);
        vec4 gz1 = vec4(0.5) - abs(gx1) - abs(gy1);
        vec4 sz1 = step(gz1, vec4(0.0));
        gx1 -= sz1 * (step(0.0, gx1) - 0.5);
        gy1 -= sz1 * (step(0.0, gy1) - 0.5);
        
        vec3 g000 = vec3(gx0.x,gy0.x,gz0.x);
        vec3 g100 = vec3(gx0.y,gy0.y,gz0.y);
        vec3 g010 = vec3(gx0.z,gy0.z,gz0.z);
        vec3 g110 = vec3(gx0.w,gy0.w,gz0.w);
        vec3 g001 = vec3(gx1.x,gy1.x,gz1.x);
        vec3 g101 = vec3(gx1.y,gy1.y,gz1.y);
        vec3 g011 = vec3(gx1.z,gy1.z,gz1.z);
        vec3 g111 = vec3(gx1.w,gy1.w,gz1.w);
        
        vec4 norm0 = taylorInvSqrt(vec4(dot(g000, g000), dot(g010, g010), dot(g100, g100), dot(g110, g110)));
        g000 *= norm0.x;
        g010 *= norm0.y;
        g100 *= norm0.z;
        g110 *= norm0.w;
        vec4 norm1 = taylorInvSqrt(vec4(dot(g001, g001), dot(g011, g011), dot(g101, g101), dot(g111, g111)));
        g001 *= norm1.x;
        g011 *= norm1.y;
        g101 *= norm1.z;
        g111 *= norm1.w;
        
        float n000 = dot(g000, Pf0);
        float n100 = dot(g100, vec3(Pf1.x, Pf0.yz));
        float n010 = dot(g010, vec3(Pf0.x, Pf1.y, Pf0.z));
        float n110 = dot(g110, vec3(Pf1.xy, Pf0.z));
        float n001 = dot(g001, vec3(Pf0.xy, Pf1.z));
        float n101 = dot(g101, vec3(Pf1.x, Pf0.y, Pf1.z));
        float n011 = dot(g011, vec3(Pf0.x, Pf1.yz));
        float n111 = dot(g111, Pf1);
        
        vec3 fade_xyz = fade(Pf0);
        vec4 n_z = mix(vec4(n000, n100, n010, n110), vec4(n001, n101, n011, n111), fade_xyz.z);
        vec2 n_yz = mix(n_z.xy, n_z.zw, fade_xyz.y);
        float n_xyz = mix(n_yz.x, n_yz.y, fade_xyz.x);
        return 2.2 * n_xyz;
      }
      
      void main() {
        vec2 uv = vUv;
        
        // Normalize UV coordinates
        uv -= vec2(0.5);
        uv *= uAspectRatio;
        uv += vec2(0.5);
        
        // Scale UV for noise sampling
        vec2 scaledUV = uv * 2.0;
        float t = uTime * uSpeed;
        
        // Create multiple layers of noise for organic, fluid patterns (like Luma)
        // Layer 1: Large-scale flowing patterns (faster)
        float noise1 = cnoise(vec3(scaledUV * 0.5, t * 0.8)) * 0.5 + 0.5;
        
        // Layer 2: Medium-scale waves (faster)
        float noise2 = cnoise(vec3(scaledUV * 1.2 + vec2(t * 0.6, t * 0.5), t * 1.0)) * 0.5 + 0.5;
        
        // Layer 3: Fine details (faster)
        float noise3 = cnoise(vec3(scaledUV * 2.5 - vec2(t * 0.4, t * 0.6), t * 1.2)) * 0.5 + 0.5;
        
        // Combine noise layers with different weights for organic feel
        float combinedNoise = noise1 * 0.5 + noise2 * 0.3 + noise3 * 0.2;
        
        // Create flowing, wavy distortion using multiple octaves (faster)
        vec2 flow = vec2(
          cnoise(vec3(scaledUV * 0.8, t * 0.7)),
          cnoise(vec3(scaledUV * 0.8 + vec2(100.0), t * 0.7))
        ) * 0.3;
        
        // Apply flow to UV for more organic movement
        vec2 distortedUV = scaledUV + flow;
        
        // Create diagonal gradient from orange to black (SoundCloud style)
        // Use the distorted UV for more organic gradient transitions
        float diagonal = (distortedUV.x + distortedUV.y) * 0.3;
        
        // Smooth gradient transitions with multiple steps
        vec3 color = mix(uColor1, uColor2, smoothstep(-1.5, 1.5, diagonal));
        color = mix(color, uColor3, smoothstep(-0.5, 2.5, diagonal));
        
        // Use combined noise to create organic color variations
        // This creates the fluid, cloud-like effect similar to Luma
        float noiseInfluence = (combinedNoise - 0.5) * uNoiseStrength;
        color = mix(color, uColor3, noiseInfluence * 0.4);
        
        // Add subtle highlights using noise
        float highlight = smoothstep(0.6, 0.8, combinedNoise);
        color = mix(color, uColor1 * 1.2, highlight * 0.15);
        
        color *= uBrightness;
        
        pc_fragColor = vec4(color, uAlpha);
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
      -1, -1, 0,  // bottom left
       1, -1, 0,  // bottom right
      -1,  1, 0,  // top left
       1,  1, 0   // top right
    ]), gl.STATIC_DRAW)

    const uvBuffer = gl.createBuffer()
    gl.bindBuffer(gl.ARRAY_BUFFER, uvBuffer)
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([
      0, 0,  // bottom left
      1, 0,  // bottom right
      0, 1,  // top left
      1, 1   // top right
    ]), gl.STATIC_DRAW)

    // Get attribute and uniform locations
    const positionLocation = gl.getAttribLocation(program, 'position')
    const uvLocation = gl.getAttribLocation(program, 'uv')
    
    const uTimeLocation = gl.getUniformLocation(program, 'uTime')
    const uSpeedLocation = gl.getUniformLocation(program, 'uSpeed')
    const uNoiseDensityLocation = gl.getUniformLocation(program, 'uNoiseDensity')
    const uNoiseStrengthLocation = gl.getUniformLocation(program, 'uNoiseStrength')
    const uBrightnessLocation = gl.getUniformLocation(program, 'uBrightness')
    const uAlphaLocation = gl.getUniformLocation(program, 'uAlpha')
    const uColor1Location = gl.getUniformLocation(program, 'uColor1')
    const uColor2Location = gl.getUniformLocation(program, 'uColor2')
    const uColor3Location = gl.getUniformLocation(program, 'uColor3')
    const uResolutionLocation = gl.getUniformLocation(program, 'uResolution')
    const uAspectRatioLocation = gl.getUniformLocation(program, 'uAspectRatio')
    const uOffsetLocation = gl.getUniformLocation(program, 'uOffset')

    // Shader parameters (SoundCloud orange-black gradient with Luma-style fluid animation)
    // SoundCloud official brand colors:
    // - Primary: #FF5500 (RGB: 255, 85, 0) - bright orange
    // - Secondary: #000000 (RGB: 0, 0, 0) - black
    // Animation style inspired by Luma's fluid, organic background
    const params = {
      speed: 1.5,                        // Fast, dynamic movement
      noiseDensity: 1.0,
      noiseStrength: 0.6,               // Increased for more organic variation
      brightness: 1.0,
      alpha: 1.0,                      // Fully opaque for maximum visibility
      color1: [1.0, 0.333, 0.0],         // SoundCloud orange #FF5500 (RGB: 255, 85, 0)
      color2: [0.5, 0.165, 0.0],         // Mid-tone dark orange #802A00 (RGB: 128, 42, 0)
      color3: [0.0, 0.0, 0.0],           // Black #000000
      offset: [0.0, 0.0]
    }

    // Animation loop
    let startTime = Date.now()

    const animate = () => {
      const currentTime = Date.now()
      const elapsed = (currentTime - startTime) / 1000.0

      gl.useProgram(program)

      // Calculate aspect ratio
      const aspectRatio = canvas.width > canvas.height 
        ? [canvas.height / canvas.width, 1.0]
        : [1.0, canvas.width / canvas.height]

      // Set uniforms
      gl.uniform1f(uTimeLocation, elapsed)
      gl.uniform1f(uSpeedLocation, params.speed)
      gl.uniform1f(uNoiseDensityLocation, params.noiseDensity)
      gl.uniform1f(uNoiseStrengthLocation, params.noiseStrength)
      gl.uniform1f(uBrightnessLocation, params.brightness)
      gl.uniform1f(uAlphaLocation, params.alpha)
      gl.uniform3fv(uColor1Location, params.color1)
      gl.uniform3fv(uColor2Location, params.color2)
      gl.uniform3fv(uColor3Location, params.color3)
      gl.uniform2f(uResolutionLocation, canvas.width, canvas.height)
      gl.uniform2fv(uAspectRatioLocation, aspectRatio)
      gl.uniform2fv(uOffsetLocation, params.offset)

      // Enable blending for transparency
      gl.enable(gl.BLEND)
      gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA)

      // Bind and draw
      gl.bindBuffer(gl.ARRAY_BUFFER, positionBuffer)
      gl.enableVertexAttribArray(positionLocation)
      gl.vertexAttribPointer(positionLocation, 3, gl.FLOAT, false, 0, 0)

      gl.bindBuffer(gl.ARRAY_BUFFER, uvBuffer)
      gl.enableVertexAttribArray(uvLocation)
      gl.vertexAttribPointer(uvLocation, 2, gl.FLOAT, false, 0, 0)

      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4)

      animationFrameRef.current = requestAnimationFrame(animate)
    }

    animate()

    // Cleanup
    return () => {
      window.removeEventListener('resize', resizeCanvas)
      if (animationFrameRef.current) {
        cancelAnimationFrame(animationFrameRef.current)
      }
      gl.deleteProgram(program)
      gl.deleteShader(vertexShader)
      gl.deleteShader(fragmentShader)
      gl.deleteBuffer(positionBuffer)
      gl.deleteBuffer(uvBuffer)
    }
  }, [])

  return (
    <canvas
      ref={canvasRef}
      className={`fixed inset-0 w-full h-full pointer-events-none webgl-background ${className}`}
      style={{ 
        zIndex: 0,
        mixBlendMode: 'normal',
        willChange: 'transform',
        background: 'transparent',
        position: 'fixed',
        top: 0,
        left: 0,
        width: '100%',
        height: '100%'
      }}
    />
  )
}

export default WebGLBackground
