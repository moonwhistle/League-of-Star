import * as THREE from 'three'

const CHARACTER_IMAGE_URL = new URL('../../img/character-cutout.png', import.meta.url).href
const CHARACTER_TARGET_SCALE = 1.46

export interface ThreeGalaxyBackgroundSceneController {
  dispose: () => void
  update: (elapsedMs: number, hpPercent?: number, currentHp?: number) => void
}

interface ThreeGalaxyBackgroundSceneCallbacks {
  onReadyChange: (isReady: boolean) => void
}

interface ThreeGalaxyVisualStart {
  cameraPitch: number
  cameraRoll: number
  cameraYaw: number
  galaxyRoll: number
  timeOffsetSeconds: number
  targetTimeOffsetMs: number
}

interface SwooshTarget {
  from: THREE.Vector3
  group: THREE.Group
  halo: THREE.Sprite
  history: THREE.Vector3[]
  hpBarBackground: THREE.Mesh<THREE.PlaneGeometry, THREE.MeshBasicMaterial>
  hpBarFill: THREE.Mesh<THREE.PlaneGeometry, THREE.MeshBasicMaterial>
  hpBarGroup: THREE.Group
  hpTextCanvas: HTMLCanvasElement
  hpTextContext: CanvasRenderingContext2D
  hpTextLastValue: string
  hpTextSprite: THREE.Sprite
  hpTextTexture: THREE.CanvasTexture
  lastElapsedMs: number | null
  position: THREE.Vector3
  segmentDurationMs: number
  segmentStartedAtMs: number
  starCore: THREE.Sprite
  target: THREE.Vector3
  trails: THREE.Sprite[]
  velocity: THREE.Vector3
}

export function createThreeGalaxyBackgroundScene(
  canvas: HTMLCanvasElement,
  callbacks: ThreeGalaxyBackgroundSceneCallbacks,
): ThreeGalaxyBackgroundSceneController | null {
  if (typeof window.WebGLRenderingContext === 'undefined') {
    callbacks.onReadyChange(false)
    return null
  }

  const renderer = new THREE.WebGLRenderer({
    canvas,
    antialias: true,
    alpha: false,
    powerPreference: 'high-performance',
    preserveDrawingBuffer: true,
  })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
  renderer.outputColorSpace = THREE.SRGBColorSpace

  const scene = new THREE.Scene()
  scene.background = new THREE.Color(0x07040f)
  const visualStart = createRandomVisualStart()

  const camera = new THREE.PerspectiveCamera(72, 1, 0.05, 180)
  camera.position.set(0, 0, 0)
  camera.lookAt(0, 0, -1)
  scene.add(camera)

  const glowTexture = createGlowTexture()
  const starTexture = createStarTexture()
  const characterTexture = createCharacterTexture()
  const galaxyRoot = new THREE.Group()
  const milkyWay = createImmersiveGalaxyBelt(glowTexture)
  const nebulaFields = [
    createNebulaCloud({
      count: 540,
      color: 0xffd1a8,
      opacity: 0.16,
      radiusMax: 66,
      radiusMin: 10,
      size: 11.4,
      texture: glowTexture,
      xOffset: -18,
      yOffset: -2.8,
      zOffset: -24,
    }),
    createNebulaCloud({
      count: 420,
      color: 0xffb78d,
      opacity: 0.15,
      radiusMax: 54,
      radiusMin: 12,
      size: 8.8,
      texture: glowTexture,
      xOffset: -11,
      yOffset: -1.8,
      zOffset: -18,
    }),
    createNebulaCloud({
      count: 360,
      color: 0xff3fd8,
      opacity: 0.14,
      radiusMax: 58,
      radiusMin: 14,
      size: 9.8,
      texture: glowTexture,
      xOffset: 3,
      yOffset: 1.6,
      zOffset: -22,
    }),
    createNebulaCloud({
      count: 340,
      color: 0x7c8dff,
      opacity: 0.12,
      radiusMax: 62,
      radiusMin: 15,
      size: 10.6,
      texture: glowTexture,
      xOffset: 12,
      yOffset: -2.3,
      zOffset: -25,
    }),
    createNebulaCloud({
      count: 300,
      color: 0x4f6eff,
      opacity: 0.09,
      radiusMax: 72,
      radiusMin: 18,
      size: 12.2,
      texture: glowTexture,
      xOffset: 22,
      yOffset: 0.8,
      zOffset: -30,
    }),
    createNebulaCloud({
      count: 260,
      color: 0xffe3a6,
      opacity: 0.11,
      radiusMax: 48,
      radiusMin: 10,
      size: 7,
      texture: glowTexture,
      xOffset: -2,
      yOffset: 4.6,
      zOffset: -20,
    }),
  ]
  const sparkleFields = [
    createSharpGalaxyClusters({
      count: 9800,
      opacity: 0.96,
      size: 0.095,
      texture: glowTexture,
    }),
    createSparkleRibbon({
      count: 14500,
      opacity: 0.92,
      size: 0.14,
      texture: glowTexture,
    }),
    createForegroundBloomStars({
      count: 220,
      opacity: 0.92,
      size: 1.2,
      texture: glowTexture,
    }),
  ]
  const starFields = [
    createForwardStarMist({
      count: 17500,
      color: 0xfff1bd,
      farDepth: -82,
      nearDepth: -3,
      opacity: 0.98,
      size: 0.11,
      texture: glowTexture,
    }),
    createForwardStarMist({
      count: 13800,
      color: 0xff55d4,
      farDepth: -86,
      nearDepth: -4,
      opacity: 0.9,
      size: 0.1,
      texture: glowTexture,
    }),
    createImmersiveStarSphere({
      count: 9600,
      color: 0x8ea4ff,
      opacity: 0.56,
      radiusMax: 74,
      radiusMin: 8,
      size: 0.07,
      texture: glowTexture,
    }),
    createImmersiveStarSphere({
      count: 2600,
      color: 0xffffff,
      opacity: 0.5,
      radiusMax: 88,
      radiusMin: 10,
      size: 0.046,
      texture: glowTexture,
    }),
    createBrightStarField({
      count: 170,
      color: 0xffffdd,
      opacity: 1,
      radiusMax: 46,
      radiusMin: 7,
      size: 0.82,
      texture: glowTexture,
    }),
    createBrightStarField({
      count: 120,
      color: 0xff47d6,
      opacity: 0.96,
      radiusMax: 42,
      radiusMin: 6,
      size: 0.72,
      texture: glowTexture,
    }),
  ]
  const swooshTarget = createSwooshTarget({
    characterTexture,
    glowTexture,
    starTexture,
  })

  galaxyRoot.add(...nebulaFields, milkyWay, ...sparkleFields, ...starFields)
  galaxyRoot.rotation.z = visualStart.galaxyRoll
  scene.add(galaxyRoot)
  camera.add(swooshTarget.group)

  let resizeObserver: ResizeObserver | null = null

  function resizeScene(): void {
    const rect = canvas.getBoundingClientRect()
    const width = Math.max(1, Math.floor(rect.width))
    const height = Math.max(1, Math.floor(rect.height))
    renderer.setSize(width, height, false)
    camera.aspect = width / height
    camera.updateProjectionMatrix()
  }

  function update(elapsedMs: number, hpPercent = 100, currentHp = 0): void {
    const time = elapsedMs / 1000 + visualStart.timeOffsetSeconds
    camera.rotation.x = visualStart.cameraPitch + Math.sin(time * 0.04) * 0.14
    camera.rotation.y = visualStart.cameraYaw + time * 0.038
    camera.rotation.z = visualStart.cameraRoll + Math.sin(time * 0.032) * 0.055
    galaxyRoot.rotation.x = Math.sin(time * 0.025) * 0.1
    galaxyRoot.rotation.y = time * 0.018
    galaxyRoot.rotation.z = visualStart.galaxyRoll + time * 0.012
    milkyWay.rotation.z = Math.sin(time * 0.035) * 0.018
    nebulaFields.forEach((field, index) => {
      field.rotation.x = Math.sin(time * 0.011 + index) * 0.045
      field.rotation.y = time * (0.003 + index * 0.00055)
      field.rotation.z = -time * (0.002 + index * 0.0006)
    })
    sparkleFields.forEach((field, index) => {
      field.rotation.x = Math.sin(time * 0.016 + index) * 0.026
      field.rotation.y = time * (0.006 + index * 0.001)
      field.rotation.z = time * (0.009 + index * 0.0014)
    })
    starFields.forEach((field, index) => {
      field.rotation.x = Math.sin(time * 0.018 + index) * 0.035
      field.rotation.y = time * (0.0025 + index * 0.00085)
      field.rotation.z = time * (0.004 + index * 0.0009)
    })
    updateSwooshTarget(
      swooshTarget,
      elapsedMs + visualStart.targetTimeOffsetMs,
      hpPercent,
      currentHp,
    )
    renderer.render(scene, camera)
  }

  function dispose(): void {
    resizeObserver?.disconnect()
    resizeObserver = null
    scene.traverse((object) => {
      const maybeMesh = object as THREE.Mesh
      maybeMesh.geometry?.dispose()

      if (maybeMesh.material !== undefined) {
        const materials = Array.isArray(maybeMesh.material)
          ? maybeMesh.material
          : [maybeMesh.material]
        materials.forEach((material) => {
          material.dispose()
        })
      }
    })
    glowTexture.dispose()
    starTexture.dispose()
    characterTexture.dispose()
    swooshTarget.hpTextTexture.dispose()
    renderer.dispose()
    callbacks.onReadyChange(false)
  }

  resizeScene()
  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(resizeScene)
    resizeObserver.observe(canvas)
  }

  callbacks.onReadyChange(true)
  update(0)

  return {
    dispose,
    update,
  }
}

export function createNoopThreeGalaxyBackgroundSceneController(): ThreeGalaxyBackgroundSceneController {
  return {
    dispose: () => {},
    update: () => {},
  }
}

function createRandomVisualStart(): ThreeGalaxyVisualStart {
  return {
    cameraPitch: randomBetween(-0.18, 0.18),
    cameraRoll: randomBetween(-0.12, 0.12),
    cameraYaw: randomBetween(-Math.PI, Math.PI),
    galaxyRoll: randomBetween(-Math.PI, Math.PI),
    targetTimeOffsetMs: randomBetween(0, 4200),
    timeOffsetSeconds: randomBetween(0, 140),
  }
}

function createImmersiveGalaxyBelt(texture: THREE.Texture): THREE.Points {
  const count = 24000
  const positions = new Float32Array(count * 3)
  const colors = new Float32Array(count * 3)
  const colorA = new THREE.Color(0xffffd7)
  const colorB = new THREE.Color(0xff62d8)
  const colorC = new THREE.Color(0xa8b2ff)

  for (let index = 0; index < count; index += 1) {
    const progress = Math.random()
    const angle = progress * Math.PI * 2
    const radius = 5.5 + Math.pow(Math.random(), 0.62) * 74
    const armWave = Math.sin(angle * 2.15 + radius * 0.055)
    const dustWidth = 0.7 + (1 - radius / 80) * 1.4
    const x = Math.cos(angle) * radius + randomNormal() * (1.5 + dustWidth)
    const y = armWave * (0.85 + Math.random() * 0.9) + randomNormal() * (0.6 + dustWidth * 1.25)
    const z = Math.sin(angle) * radius - 14 + randomNormal() * (1.8 + dustWidth * 1.3)
    positions[index * 3] = x
    positions[index * 3 + 1] = y
    positions[index * 3 + 2] = z

    const color =
      progress < 0.74
        ? colorA.clone().lerp(colorB, progress / 0.74)
        : colorB.clone().lerp(colorC, (progress - 0.74) / 0.26)
    colors[index * 3] = color.r
    colors[index * 3 + 1] = color.g
    colors[index * 3 + 2] = color.b
  }

  const geometry = new THREE.BufferGeometry()
  geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))
  geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3))

  return new THREE.Points(
    geometry,
    new THREE.PointsMaterial({
      alphaMap: texture,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
      map: texture,
      size: 0.12,
      sizeAttenuation: true,
      transparent: true,
      opacity: 0.98,
      vertexColors: true,
    }),
  )
}

function createForwardStarMist(options: {
  color: number
  count: number
  farDepth: number
  nearDepth: number
  opacity: number
  size: number
  texture: THREE.Texture
}): THREE.Points {
  const positions = new Float32Array(options.count * 3)

  for (let index = 0; index < options.count; index += 1) {
    const depthProgress = Math.pow(Math.random(), 0.82)
    const z = options.nearDepth + depthProgress * (options.farDepth - options.nearDepth)
    const depth = Math.abs(z)
    const spreadX = depth * 0.92
    const spreadY = depth * 0.56
    const centerBias = Math.random() < 0.72 ? Math.pow(Math.random(), 1.75) : Math.random()
    const direction = Math.random() * Math.PI * 2
    const x = Math.cos(direction) * spreadX * centerBias + randomNormal() * 1.2
    const y = Math.sin(direction) * spreadY * centerBias + randomNormal() * 0.9
    positions[index * 3] = x
    positions[index * 3 + 1] = y
    positions[index * 3 + 2] = z
  }

  const geometry = new THREE.BufferGeometry()
  geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))

  return new THREE.Points(
    geometry,
    new THREE.PointsMaterial({
      alphaMap: options.texture,
      blending: THREE.AdditiveBlending,
      color: options.color,
      depthWrite: false,
      map: options.texture,
      opacity: options.opacity,
      size: options.size,
      sizeAttenuation: true,
      transparent: true,
    }),
  )
}

function createImmersiveStarSphere(options: {
  color: number
  count: number
  opacity: number
  radiusMax: number
  radiusMin: number
  size: number
  texture: THREE.Texture
}): THREE.Points {
  const positions = new Float32Array(options.count * 3)

  for (let index = 0; index < options.count; index += 1) {
    const direction = new THREE.Vector3(randomNormal(), randomNormal(), randomNormal()).normalize()
    const radius =
      options.radiusMin + Math.pow(Math.random(), 0.75) * (options.radiusMax - options.radiusMin)
    const position = direction.multiplyScalar(radius)
    position.z -= 6
    positions[index * 3] = position.x
    positions[index * 3 + 1] = position.y
    positions[index * 3 + 2] = position.z
  }

  const geometry = new THREE.BufferGeometry()
  geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))

  return new THREE.Points(
    geometry,
    new THREE.PointsMaterial({
      alphaMap: options.texture,
      blending: THREE.AdditiveBlending,
      color: options.color,
      depthWrite: false,
      map: options.texture,
      opacity: options.opacity,
      size: options.size,
      sizeAttenuation: true,
      transparent: true,
    }),
  )
}

function createNebulaCloud(options: {
  color: number
  count: number
  opacity: number
  radiusMax: number
  radiusMin: number
  size: number
  texture: THREE.Texture
  xOffset: number
  yOffset: number
  zOffset: number
}): THREE.Points {
  const positions = new Float32Array(options.count * 3)

  for (let index = 0; index < options.count; index += 1) {
    const direction = new THREE.Vector3(
      randomNormal() * 1.6,
      randomNormal() * 0.82,
      -Math.abs(randomNormal()) - 0.28,
    ).normalize()
    const radius =
      options.radiusMin + Math.pow(Math.random(), 0.74) * (options.radiusMax - options.radiusMin)
    const position = direction.multiplyScalar(radius)
    positions[index * 3] = position.x + options.xOffset + randomNormal() * 4.5
    positions[index * 3 + 1] = position.y + options.yOffset + randomNormal() * 2.8
    positions[index * 3 + 2] = position.z + options.zOffset + randomNormal() * 5
  }

  const geometry = new THREE.BufferGeometry()
  geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))

  return new THREE.Points(
    geometry,
    new THREE.PointsMaterial({
      alphaMap: options.texture,
      blending: THREE.AdditiveBlending,
      color: options.color,
      depthWrite: false,
      map: options.texture,
      opacity: options.opacity,
      size: options.size,
      sizeAttenuation: true,
      transparent: true,
    }),
  )
}

function createSparkleRibbon(options: {
  count: number
  opacity: number
  size: number
  texture: THREE.Texture
}): THREE.Points {
  const positions = new Float32Array(options.count * 3)
  const colors = new Float32Array(options.count * 3)
  const warm = new THREE.Color(0xfff3bd)
  const pink = new THREE.Color(0xff5adf)
  const violet = new THREE.Color(0x9da6ff)

  for (let index = 0; index < options.count; index += 1) {
    const progress = Math.random()
    const leftDensity = Math.pow(1 - progress, 1.35)
    const ribbonWave = Math.sin(progress * Math.PI * 2.6 - 0.3) * 5.2
    const x = -32 + progress * 66 + randomNormal() * (2.2 + progress * 5.2 + leftDensity * 3.8)
    const y =
      10.5 -
      progress * 18 +
      ribbonWave +
      randomNormal() * (1.5 + progress * 2.2 + leftDensity * 3.1)
    const z = -10 - Math.pow(Math.random(), 0.72) * 70
    positions[index * 3] = x
    positions[index * 3 + 1] = y
    positions[index * 3 + 2] = z

    const color =
      progress < 0.5
        ? warm.clone().lerp(pink, progress / 0.5)
        : pink.clone().lerp(violet, (progress - 0.5) / 0.5)
    colors[index * 3] = color.r
    colors[index * 3 + 1] = color.g
    colors[index * 3 + 2] = color.b
  }

  const geometry = new THREE.BufferGeometry()
  geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))
  geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3))

  return new THREE.Points(
    geometry,
    new THREE.PointsMaterial({
      alphaMap: options.texture,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
      map: options.texture,
      opacity: options.opacity,
      size: options.size,
      sizeAttenuation: true,
      transparent: true,
      vertexColors: true,
    }),
  )
}

function createSharpGalaxyClusters(options: {
  count: number
  opacity: number
  size: number
  texture: THREE.Texture
}): THREE.Points {
  const positions = new Float32Array(options.count * 3)
  const colors = new Float32Array(options.count * 3)
  const clusterAnchors: [
    THREE.Vector3,
    THREE.Vector3,
    THREE.Vector3,
    THREE.Vector3,
    THREE.Vector3,
    THREE.Vector3,
    THREE.Vector3,
  ] = [
    new THREE.Vector3(-24, 7.5, -18),
    new THREE.Vector3(-18, -2.5, -20),
    new THREE.Vector3(-10, 4.2, -24),
    new THREE.Vector3(-3, -4.2, -26),
    new THREE.Vector3(6, 2.8, -29),
    new THREE.Vector3(15, -1.8, -34),
    new THREE.Vector3(24, -5.2, -38),
  ]
  const palette: [THREE.Color, THREE.Color, THREE.Color, THREE.Color] = [
    new THREE.Color(0xffffd2),
    new THREE.Color(0xffd08d),
    new THREE.Color(0xff65dc),
    new THREE.Color(0xb9bcff),
  ]

  for (let index = 0; index < options.count; index += 1) {
    const anchor = clusterAnchors[index % clusterAnchors.length] ?? clusterAnchors[0]
    const clusterProgress = index / options.count
    const tightCore = Math.random() < 0.58
    const spread = tightCore ? 1.05 + clusterProgress * 1.2 : 2.4 + clusterProgress * 2.7
    const x = anchor.x + randomNormal() * spread * 2.1
    const y = anchor.y + randomNormal() * spread
    const z = anchor.z + randomNormal() * spread * 1.8
    positions[index * 3] = x
    positions[index * 3 + 1] = y
    positions[index * 3 + 2] = z

    const color = palette[index % palette.length] ?? palette[0]
    colors[index * 3] = color.r
    colors[index * 3 + 1] = color.g
    colors[index * 3 + 2] = color.b
  }

  const geometry = new THREE.BufferGeometry()
  geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))
  geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3))

  return new THREE.Points(
    geometry,
    new THREE.PointsMaterial({
      alphaMap: options.texture,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
      map: options.texture,
      opacity: options.opacity,
      size: options.size,
      sizeAttenuation: true,
      transparent: true,
      vertexColors: true,
    }),
  )
}

function createForegroundBloomStars(options: {
  count: number
  opacity: number
  size: number
  texture: THREE.Texture
}): THREE.Points {
  const positions = new Float32Array(options.count * 3)
  const colors = new Float32Array(options.count * 3)
  const palette: [THREE.Color, THREE.Color, THREE.Color] = [
    new THREE.Color(0xffffdd),
    new THREE.Color(0xff8be9),
    new THREE.Color(0xfff0b0),
  ]

  for (let index = 0; index < options.count; index += 1) {
    const leftDensity = Math.random() < 0.64
    const x = leftDensity ? -28 + Math.random() * 35 : -4 + Math.random() * 44
    const y = -16 + Math.random() * 31
    const z = -7 - Math.random() * 42
    positions[index * 3] = x + randomNormal() * 2.5
    positions[index * 3 + 1] = y + randomNormal() * 1.6
    positions[index * 3 + 2] = z

    const color = palette[index % palette.length] ?? palette[0]
    colors[index * 3] = color.r
    colors[index * 3 + 1] = color.g
    colors[index * 3 + 2] = color.b
  }

  const geometry = new THREE.BufferGeometry()
  geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))
  geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3))

  return new THREE.Points(
    geometry,
    new THREE.PointsMaterial({
      alphaMap: options.texture,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
      map: options.texture,
      opacity: options.opacity,
      size: options.size,
      sizeAttenuation: true,
      transparent: true,
      vertexColors: true,
    }),
  )
}

function createBrightStarField(options: {
  color: number
  count: number
  opacity: number
  radiusMax: number
  radiusMin: number
  size: number
  texture: THREE.Texture
}): THREE.Points {
  const positions = new Float32Array(options.count * 3)

  for (let index = 0; index < options.count; index += 1) {
    const direction = new THREE.Vector3(
      randomNormal(),
      randomNormal(),
      -Math.abs(randomNormal()) - 0.25,
    ).normalize()
    const radius =
      options.radiusMin + Math.pow(Math.random(), 0.68) * (options.radiusMax - options.radiusMin)
    const position = direction.multiplyScalar(radius)
    positions[index * 3] = position.x
    positions[index * 3 + 1] = position.y
    positions[index * 3 + 2] = position.z
  }

  const geometry = new THREE.BufferGeometry()
  geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))

  return new THREE.Points(
    geometry,
    new THREE.PointsMaterial({
      alphaMap: options.texture,
      blending: THREE.AdditiveBlending,
      color: options.color,
      depthWrite: false,
      map: options.texture,
      opacity: options.opacity,
      size: options.size,
      sizeAttenuation: true,
      transparent: true,
    }),
  )
}

function createSwooshTarget(options: {
  characterTexture: THREE.Texture
  glowTexture: THREE.Texture
  starTexture: THREE.Texture
}): SwooshTarget {
  const group = new THREE.Group()
  const from = new THREE.Vector3(-3.8, 2.2, -8)
  const target = createSwooshTargetPosition()
  const halo = new THREE.Sprite(
    new THREE.SpriteMaterial({
      blending: THREE.AdditiveBlending,
      color: 0xffdf78,
      depthTest: false,
      depthWrite: false,
      map: options.glowTexture,
      opacity: 0.68,
      transparent: true,
    }),
  )
  const starCore = new THREE.Sprite(
    new THREE.SpriteMaterial({
      alphaTest: 0.04,
      color: 0xfff2bf,
      depthTest: false,
      depthWrite: false,
      map: options.characterTexture,
      opacity: 1,
      toneMapped: false,
      transparent: true,
    }),
  )
  const hpBarGroup = new THREE.Group()
  const hpBarBackground = new THREE.Mesh(
    new THREE.PlaneGeometry(1.52, 0.18),
    new THREE.MeshBasicMaterial({
      color: 0x13090b,
      depthTest: false,
      depthWrite: false,
      opacity: 0.82,
      transparent: true,
    }),
  )
  const hpBarFill = new THREE.Mesh(
    new THREE.PlaneGeometry(1.42, 0.1),
    new THREE.MeshBasicMaterial({
      color: 0xff304c,
      depthTest: false,
      depthWrite: false,
      opacity: 0.96,
      transparent: true,
    }),
  )
  const hpBarGlow = new THREE.Mesh(
    new THREE.PlaneGeometry(1.56, 0.22),
    new THREE.MeshBasicMaterial({
      blending: THREE.AdditiveBlending,
      color: 0xffc75a,
      depthTest: false,
      depthWrite: false,
      opacity: 0.18,
      transparent: true,
    }),
  )
  const hpTextCanvas = document.createElement('canvas')
  hpTextCanvas.width = 256
  hpTextCanvas.height = 96
  const hpTextContext = hpTextCanvas.getContext('2d')

  if (hpTextContext === null) {
    throw new Error('Failed to create HP text canvas context')
  }

  const hpTextTexture = new THREE.CanvasTexture(hpTextCanvas)
  hpTextTexture.colorSpace = THREE.SRGBColorSpace
  const hpTextSprite = new THREE.Sprite(
    new THREE.SpriteMaterial({
      depthTest: false,
      depthWrite: false,
      map: hpTextTexture,
      opacity: 1,
      transparent: true,
    }),
  )
  const trails = Array.from({ length: 8 }, (_, index) => {
    const trail = new THREE.Sprite(
      new THREE.SpriteMaterial({
        blending: THREE.AdditiveBlending,
        color: index < 3 ? 0xfff2a6 : 0xffb52e,
        depthTest: false,
        depthWrite: false,
        map: options.starTexture,
        opacity: 0.42 - index * 0.038,
        transparent: true,
      }),
    )
    trail.scale.setScalar(0.86 - index * 0.054)
    group.add(trail)
    return trail
  })

  halo.scale.setScalar(2.34)
  starCore.scale.setScalar(CHARACTER_TARGET_SCALE)
  halo.renderOrder = 8
  starCore.renderOrder = 12
  hpBarBackground.renderOrder = 20
  hpBarFill.renderOrder = 21
  hpBarGlow.renderOrder = 19
  hpTextSprite.renderOrder = 22
  hpTextSprite.position.set(0, 0.34, 0.02)
  hpTextSprite.scale.set(1.46, 0.54, 1)
  hpBarGroup.add(hpBarGlow, hpBarBackground, hpBarFill, hpTextSprite)
  group.add(halo, starCore, hpBarGroup)

  return {
    from,
    group,
    halo,
    history: Array.from({ length: trails.length * 3 }, () => from.clone()),
    hpBarBackground,
    hpBarFill,
    hpBarGroup,
    hpTextCanvas,
    hpTextContext,
    hpTextLastValue: '',
    hpTextSprite,
    hpTextTexture,
    lastElapsedMs: null,
    position: from.clone(),
    segmentDurationMs: 1200,
    segmentStartedAtMs: 0,
    starCore,
    target,
    trails,
    velocity: new THREE.Vector3(3.4, -1.2, 0),
  }
}

function updateSwooshTarget(
  target: SwooshTarget,
  elapsedMs: number,
  hpPercent = 100,
  currentHp = 0,
): void {
  const dtSeconds =
    target.lastElapsedMs === null
      ? 0
      : Math.min(0.05, Math.max(0, (elapsedMs - target.lastElapsedMs) / 1000))
  target.lastElapsedMs = elapsedMs

  if (
    elapsedMs - target.segmentStartedAtMs >= target.segmentDurationMs ||
    target.position.distanceTo(target.target) < 0.72
  ) {
    target.from.copy(target.position)
    target.target.copy(createSwooshTargetPosition())
    target.segmentStartedAtMs = elapsedMs
    target.segmentDurationMs = 680 + Math.random() * 980
  }

  updateSwooshTargetSteering(target, dtSeconds)
  const position = getSwooshTargetPosition(target, elapsedMs)
  const pulse = 1 + Math.sin(elapsedMs * 0.027) * 0.16
  target.halo.position.copy(position)
  target.halo.scale.setScalar(2.2 * pulse)
  target.starCore.position.copy(position)
  target.starCore.scale.setScalar(CHARACTER_TARGET_SCALE * pulse)
  target.starCore.material.rotation = Math.sin(elapsedMs * 0.006) * 0.38
  target.hpBarGroup.position.copy(position).add(new THREE.Vector3(0, 1.34, 0.05))
  target.hpBarGroup.scale.setScalar(1 + Math.sin(elapsedMs * 0.018) * 0.025)
  updateSwooshTargetHpBar(target, hpPercent)
  updateSwooshTargetHpText(target, currentHp)

  target.history.unshift(position.clone())
  target.history.length = target.trails.length * 3
  target.trails.forEach((trail, index) => {
    const historyPosition =
      target.history[Math.min(target.history.length - 1, index * 3)] ?? position
    trail.position.copy(historyPosition).add(new THREE.Vector3(0, 0, -0.08))
    trail.renderOrder = 6
    trail.material.opacity = Math.max(0.03, 0.44 - index * 0.046)
  })
}

function updateSwooshTargetSteering(target: SwooshTarget, dtSeconds: number): void {
  if (dtSeconds <= 0) {
    return
  }

  const targetDirection = target.target.clone().sub(target.position)

  if (targetDirection.lengthSq() === 0) {
    return
  }

  const desiredVelocity = targetDirection.normalize().multiplyScalar(5.8)
  target.velocity.lerp(desiredVelocity, Math.min(1, dtSeconds * 4.8))
  target.position.addScaledVector(target.velocity, dtSeconds)
}

function updateSwooshTargetHpBar(target: SwooshTarget, hpPercent: number): void {
  const hpRatio = Math.min(1, Math.max(0, hpPercent / 100))
  const fillWidth = 1.42
  target.hpBarFill.scale.x = hpRatio
  target.hpBarFill.position.x = -(fillWidth * (1 - hpRatio)) / 2
  target.hpBarFill.material.color.setHSL(0.01 + hpRatio * 0.22, 0.95, 0.56)
  target.hpBarBackground.position.set(0, 0, 0)
  target.hpBarFill.position.y = 0
}

function updateSwooshTargetHpText(target: SwooshTarget, currentHp: number): void {
  const nextValue = formatHpValue(currentHp)

  if (nextValue === target.hpTextLastValue) {
    return
  }

  target.hpTextLastValue = nextValue
  target.hpTextContext.clearRect(0, 0, target.hpTextCanvas.width, target.hpTextCanvas.height)
  target.hpTextContext.font = '700 38px Arial, sans-serif'
  target.hpTextContext.textAlign = 'center'
  target.hpTextContext.textBaseline = 'middle'
  target.hpTextContext.lineJoin = 'round'
  target.hpTextContext.shadowColor = 'rgba(0, 0, 0, 0.92)'
  target.hpTextContext.shadowBlur = 10
  target.hpTextContext.shadowOffsetY = 2
  target.hpTextContext.strokeStyle = 'rgba(0, 0, 0, 0.96)'
  target.hpTextContext.lineWidth = 7
  target.hpTextContext.strokeText(nextValue, 128, 48)
  target.hpTextContext.fillStyle = 'rgba(255, 255, 255, 0.98)'
  target.hpTextContext.fillText(nextValue, 128, 48)
  target.hpTextTexture.needsUpdate = true
}

function formatHpValue(currentHp: number): string {
  return Math.max(0, Math.round(currentHp)).toLocaleString('en-US')
}

function getSwooshTargetPosition(target: SwooshTarget, elapsedMs: number): THREE.Vector3 {
  const jitter = new THREE.Vector3(
    Math.sin(elapsedMs * 0.009) * 0.22,
    Math.cos(elapsedMs * 0.011) * 0.18,
    Math.sin(elapsedMs * 0.007) * 0.1,
  )

  return target.position.clone().add(jitter)
}

function createSwooshTargetPosition(): THREE.Vector3 {
  return new THREE.Vector3(
    -5.9 + Math.random() * 11.8,
    -3.8 + Math.random() * 7.6,
    -7.2 - Math.random() * 2.8,
  )
}

function createGlowTexture(): THREE.CanvasTexture {
  const textureCanvas = document.createElement('canvas')
  textureCanvas.width = 128
  textureCanvas.height = 128
  const context = textureCanvas.getContext('2d')

  if (context !== null) {
    const gradient = context.createRadialGradient(64, 64, 0, 64, 64, 64)
    gradient.addColorStop(0, 'rgba(255,255,255,1)')
    gradient.addColorStop(0.16, 'rgba(255,255,255,0.86)')
    gradient.addColorStop(0.38, 'rgba(255,255,255,0.28)')
    gradient.addColorStop(1, 'rgba(255,255,255,0)')
    context.fillStyle = gradient
    context.fillRect(0, 0, 128, 128)
  }

  const texture = new THREE.CanvasTexture(textureCanvas)
  texture.colorSpace = THREE.SRGBColorSpace
  texture.needsUpdate = true

  return texture
}

function createStarTexture(): THREE.CanvasTexture {
  const textureCanvas = document.createElement('canvas')
  textureCanvas.width = 128
  textureCanvas.height = 128
  const context = textureCanvas.getContext('2d')

  if (context !== null) {
    context.clearRect(0, 0, 128, 128)

    const outerGradient = context.createRadialGradient(64, 64, 2, 64, 64, 62)
    outerGradient.addColorStop(0, 'rgba(255, 255, 232, 1)')
    outerGradient.addColorStop(0.24, 'rgba(255, 236, 113, 0.98)')
    outerGradient.addColorStop(0.6, 'rgba(255, 179, 42, 0.48)')
    outerGradient.addColorStop(1, 'rgba(255, 179, 42, 0)')
    context.fillStyle = outerGradient
    drawStarPath(context, 64, 64, 54, 21, 5)
    context.fill()

    const coreGradient = context.createRadialGradient(64, 64, 0, 64, 64, 34)
    coreGradient.addColorStop(0, 'rgba(255, 255, 255, 1)')
    coreGradient.addColorStop(0.42, 'rgba(255, 240, 112, 0.95)')
    coreGradient.addColorStop(1, 'rgba(255, 175, 36, 0.16)')
    context.fillStyle = coreGradient
    drawStarPath(context, 64, 64, 42, 17, 5)
    context.fill()

    context.strokeStyle = 'rgba(255, 255, 226, 0.9)'
    context.lineWidth = 3
    drawStarPath(context, 64, 64, 43, 17, 5)
    context.stroke()
  }

  const texture = new THREE.CanvasTexture(textureCanvas)
  texture.colorSpace = THREE.SRGBColorSpace
  texture.needsUpdate = true

  return texture
}

function createCharacterTexture(): THREE.Texture {
  const texture = new THREE.TextureLoader().load(CHARACTER_IMAGE_URL)
  texture.colorSpace = THREE.SRGBColorSpace
  texture.generateMipmaps = true
  texture.magFilter = THREE.LinearFilter
  texture.minFilter = THREE.LinearMipmapLinearFilter
  texture.needsUpdate = true

  return texture
}

function drawStarPath(
  context: CanvasRenderingContext2D,
  x: number,
  y: number,
  outerRadius: number,
  innerRadius: number,
  points: number,
): void {
  const step = Math.PI / points

  context.beginPath()

  for (let index = 0; index < points * 2; index += 1) {
    const radius = index % 2 === 0 ? outerRadius : innerRadius
    const angle = -Math.PI / 2 + index * step
    const pointX = x + Math.cos(angle) * radius
    const pointY = y + Math.sin(angle) * radius

    if (index === 0) {
      context.moveTo(pointX, pointY)
      continue
    }

    context.lineTo(pointX, pointY)
  }

  context.closePath()
}

function randomNormal(): number {
  return (Math.random() + Math.random() + Math.random() + Math.random() - 2) / 2
}

function randomBetween(min: number, max: number): number {
  return min + Math.random() * (max - min)
}
