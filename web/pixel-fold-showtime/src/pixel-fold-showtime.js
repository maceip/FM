import * as THREE from "three";
import { GLTFLoader } from "three/addons/loaders/GLTFLoader.js";
import { RoomEnvironment } from "three/addons/environments/RoomEnvironment.js";
import { RectAreaLightUniformsLib } from "three/addons/lights/RectAreaLightUniformsLib.js";

const clamp = (value, min = 0, max = 1) => Math.min(max, Math.max(min, value));
const mix = (a, b, t) => a + (b - a) * t;
const smoother = (edge0, edge1, value) => {
  const t = clamp((value - edge0) / (edge1 - edge0));
  return t * t * t * (t * (t * 6 - 15) + 10);
};

class PixelFoldShowtime extends HTMLElement {
  static observedAttributes = ["model-url", "exit-mode"];

  constructor() {
    super();
    this.attachShadow({ mode: "open" });
    this.modelUrl = "./assets/pixel10_fold_v18.glb";
    this.exitMode = "fog";
    this.progress = 0;
    this.smoothProgress = 0;
    this.pointer = new THREE.Vector2();
    this.targetPointer = new THREE.Vector2();
    this.meshMaterials = [];
    this.clock = new THREE.Clock();
    this.frame = 0;
    this.reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
  }

  connectedCallback() {
    this.modelUrl = this.getAttribute("model-url") || this.modelUrl;
    this.exitMode = this.getAttribute("exit-mode") || this.exitMode;
    this.renderShell();
    this.mountThree();
    this.bindEvents();
    this.loadModel();
    this.updateScroll();
    this.animate();
  }

  disconnectedCallback() {
    cancelAnimationFrame(this.frame);
    this.resizeObserver?.disconnect();
    window.removeEventListener("scroll", this.handleScroll);
    window.removeEventListener("pointermove", this.handlePointerMove);
    this.renderer?.dispose();
  }

  attributeChangedCallback(name, oldValue, newValue) {
    if (name === "model-url" && oldValue !== newValue && newValue) {
      this.modelUrl = newValue;
    }
    if (name === "exit-mode" && oldValue !== newValue && newValue) {
      this.exitMode = newValue;
    }
  }

  renderShell() {
    this.shadowRoot.innerHTML = `
      <style>
        :host {
          display: block;
          position: relative;
          color: #15181d;
          background:
            radial-gradient(circle at 18% 16%, rgba(255,255,255,0.86), rgba(255,255,255,0) 24rem),
            radial-gradient(circle at 82% 70%, rgba(153,169,184,0.45), rgba(153,169,184,0) 32rem),
            linear-gradient(145deg, #eef1f4 0%, #c7cfd7 44%, #f4f6f7 100%);
          overflow: clip;
        }

        .stage {
          position: sticky;
          top: 0;
          height: 100svh;
          min-height: 620px;
          overflow: hidden;
          isolation: isolate;
        }

        canvas {
          display: block;
          width: 100%;
          height: 100%;
        }

        .wash,
        .grain,
        .haze {
          position: absolute;
          inset: 0;
          pointer-events: none;
        }

        .wash {
          z-index: -3;
          background:
            linear-gradient(100deg, rgba(255,255,255,0.72), rgba(255,255,255,0) 36%),
            linear-gradient(260deg, rgba(109,123,137,0.24), rgba(109,123,137,0) 45%);
          transform: translate3d(var(--wash-x, 0px), var(--wash-y, 0px), 0) scale(1.08);
        }

        .haze {
          z-index: -2;
          background:
            radial-gradient(circle at var(--fog-x, 50%) 52%, rgba(255,255,255,0.72), rgba(255,255,255,0) 18rem),
            radial-gradient(circle at 50% 50%, rgba(172,184,194,0.42), rgba(172,184,194,0) 34rem);
          opacity: var(--haze-opacity, 0.2);
          filter: blur(14px);
        }

        .grain {
          z-index: 2;
          opacity: 0.018;
          mix-blend-mode: multiply;
          background-image:
            repeating-linear-gradient(0deg, rgba(20,24,29,0.18) 0 1px, transparent 1px 3px),
            repeating-linear-gradient(90deg, rgba(255,255,255,0.12) 0 1px, transparent 1px 5px);
        }

        .brand {
          position: absolute;
          left: clamp(24px, 5vw, 72px);
          top: clamp(22px, 5vw, 56px);
          z-index: 3;
          font-size: clamp(18px, 2vw, 28px);
          font-weight: 650;
          letter-spacing: 0;
          color: rgba(25,29,34,0.82);
          text-wrap: balance;
        }

        :host([exit-mode="screen-zoom"]) .brand {
          opacity: calc(1 - var(--screen-zoom, 0));
          transform: translate3d(0, calc(var(--screen-zoom, 0) * -18px), 0);
        }

        :host([exit-mode="screen-zoom"]) .meter {
          opacity: calc(1 - var(--screen-zoom, 0));
        }

        .meter {
          position: absolute;
          right: clamp(20px, 4vw, 48px);
          bottom: clamp(22px, 4vw, 44px);
          z-index: 3;
          width: min(180px, 28vw);
          height: 2px;
          background: rgba(25,29,34,0.16);
          overflow: hidden;
        }

        .meter span {
          display: block;
          height: 100%;
          width: calc(var(--progress, 0) * 100%);
          background: rgba(25,29,34,0.72);
        }

        .transition-sheen {
          position: absolute;
          inset: 0;
          z-index: 1;
          pointer-events: none;
          opacity: var(--screen-zoom, 0);
          background:
            linear-gradient(110deg, rgba(255,255,255,0) 28%, rgba(255,255,255,0.28) 45%, rgba(255,255,255,0) 61%),
            radial-gradient(circle at 50% 50%, rgba(255,255,255,0), rgba(232,237,242,0.5) 76%);
          transform: translate3d(calc((1 - var(--screen-zoom, 0)) * -26vw), 0, 0);
          mix-blend-mode: screen;
        }

        .screen-takeover {
          position: absolute;
          left: 50%;
          top: 50%;
          z-index: 4;
          width: min(32vw, 410px);
          height: min(72vh, 760px);
          border-radius: var(--screen-radius, 34px);
          background:
            linear-gradient(128deg, rgba(255,255,255,0.20), rgba(255,255,255,0) 32%),
            radial-gradient(circle at 62% 76%, rgba(84,91,104,0.30), rgba(84,91,104,0) 34%),
            linear-gradient(180deg, #16181f 0%, #090b10 100%);
          box-shadow:
            0 0 0 1px rgba(255,255,255,0.18) inset,
            0 34px 90px rgba(14,18,24,0.22);
          opacity: var(--screen-takeover-opacity, 0);
          pointer-events: none;
          transform:
            translate3d(calc(-50% + var(--screen-shift-x, 0px)), calc(-50% + var(--screen-shift-y, 0px)), 0)
            scale(var(--screen-scale, 0.42));
          transform-origin: center;
          overflow: hidden;
          will-change: transform, opacity, border-radius;
        }

        .screen-takeover::before {
          content: "";
          position: absolute;
          inset: -8%;
          background:
            linear-gradient(116deg, rgba(255,255,255,0) 30%, rgba(255,255,255,0.24) 42%, rgba(255,255,255,0) 56%),
            linear-gradient(180deg, rgba(52,58,70,0), rgba(111,123,145,0.16));
          opacity: var(--screen-sheen, 0);
          transform: translate3d(calc((1 - var(--screen-zoom, 0)) * -46%), 0, 0);
        }

        .fallback {
          position: absolute;
          inset: auto clamp(24px, 6vw, 80px) clamp(32px, 8vw, 96px);
          z-index: 4;
          max-width: 34rem;
          font-size: 15px;
          line-height: 1.45;
          color: rgba(25,29,34,0.82);
          visibility: hidden;
        }

        :host([data-error]) .fallback {
          visibility: visible;
        }
      </style>
      <div class="stage">
        <div class="wash"></div>
        <div class="haze"></div>
        <canvas part="canvas"></canvas>
        <div class="transition-sheen"></div>
        <div class="screen-takeover" aria-hidden="true"></div>
        <div class="brand">Pixel 10 Pro Fold</div>
        <div class="meter" aria-hidden="true"><span></span></div>
        <div class="grain"></div>
        <div class="fallback">The 3D model could not be loaded in this browser session.</div>
      </div>
    `;

    this.stage = this.shadowRoot.querySelector(".stage");
    this.canvas = this.shadowRoot.querySelector("canvas");
  }

  mountThree() {
    this.scene = new THREE.Scene();
    this.scene.background = new THREE.Color(0xdce1e6);
    this.scene.fog = new THREE.FogExp2(0xd9dee4, 0.011);

    this.camera = new THREE.PerspectiveCamera(38, 1, 0.01, 60);
    this.camera.position.set(0, 0.1, 6.4);

    this.renderer = new THREE.WebGLRenderer({
      canvas: this.canvas,
      antialias: true,
      alpha: false,
      powerPreference: "high-performance",
    });
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.0;

    const pmrem = new THREE.PMREMGenerator(this.renderer);
    this.scene.environment = pmrem.fromScene(new RoomEnvironment(this.renderer), 0.04).texture;
    pmrem.dispose();

    RectAreaLightUniformsLib.init();

    this.scene.add(new THREE.HemisphereLight(0xf6f8fb, 0x7d8793, 1.45));

    this.keyLight = new THREE.DirectionalLight(0xffffff, 3.2);
    this.keyLight.position.set(3.2, 4.4, 5.2);
    this.scene.add(this.keyLight);

    this.rimLight = new THREE.DirectionalLight(0xbfd6ff, 2.4);
    this.rimLight.position.set(-4.2, 1.4, -2.8);
    this.scene.add(this.rimLight);

    this.cameraBumpStripLight = new THREE.RectAreaLight(0xffffff, 5.8, 2.2, 0.34);
    this.cameraBumpStripLight.position.set(-1.85, 2.2, 3.0);
    this.cameraBumpStripLight.lookAt(-0.25, 0.18, 0);
    this.scene.add(this.cameraBumpStripLight);

    this.edgeGrazingLight = new THREE.RectAreaLight(0xd8e7ff, 3.2, 0.22, 2.6);
    this.edgeGrazingLight.position.set(2.2, -0.9, 2.8);
    this.edgeGrazingLight.lookAt(0.1, 0.0, 0);
    this.scene.add(this.edgeGrazingLight);

    this.showGroup = new THREE.Group();
    this.phoneGroup = new THREE.Group();
    this.showGroup.add(this.phoneGroup);
    this.scene.add(this.showGroup);

    this.createFogCloud();
    this.resizeObserver = new ResizeObserver(() => this.resize());
    this.resizeObserver.observe(this.stage);
    this.resize();
  }

  bindEvents() {
    this.handleScroll = () => this.updateScroll();
    this.handlePointerMove = (event) => {
      const rect = this.getBoundingClientRect();
      this.targetPointer.x = ((event.clientX - rect.left) / Math.max(rect.width, 1) - 0.5) * 2;
      this.targetPointer.y = ((event.clientY - rect.top) / Math.max(rect.height, 1) - 0.5) * 2;
    };

    window.addEventListener("scroll", this.handleScroll, { passive: true });
    window.addEventListener("pointermove", this.handlePointerMove, { passive: true });
  }

  loadModel() {
    const loader = new GLTFLoader();
    loader.load(
      this.modelUrl,
      (gltf) => this.prepareModel(gltf.scene),
      undefined,
      () => {
        this.setAttribute("data-error", "");
      },
    );
  }

  prepareModel(asset) {
    asset.updateWorldMatrix(true, true);
    const initialBox = new THREE.Box3().setFromObject(asset);
    const center = initialBox.getCenter(new THREE.Vector3());
    asset.position.sub(center);
    asset.updateWorldMatrix(true, true);

    this.leftPivot = new THREE.Group();
    this.rightPivot = new THREE.Group();
    this.hingeGroup = new THREE.Group();
    this.phoneGroup.add(asset, this.leftPivot, this.rightPivot, this.hingeGroup);
    this.modelRoot = asset;

    const hingeBox = this.getNamedBox(asset, (name) => name.includes("hinge"));
    const centeredBox = new THREE.Box3().setFromObject(asset);
    const hingeCenter = hingeBox?.getCenter(new THREE.Vector3()) || new THREE.Vector3(0, 0, 0);
    const size = centeredBox.getSize(new THREE.Vector3());
    this.baseScale = 2.15 / Math.max(size.x, size.z, 0.001);

    this.leftPivot.position.copy(hingeCenter);
    this.rightPivot.position.copy(hingeCenter);
    this.hingeGroup.position.copy(hingeCenter);
    this.leftPivot.updateWorldMatrix(true, false);
    this.rightPivot.updateWorldMatrix(true, false);
    this.hingeGroup.updateWorldMatrix(true, false);

    const meshes = [];
    asset.traverse((node) => {
      if (!node.isMesh) return;
      node.castShadow = true;
      node.receiveShadow = true;
      meshes.push(node);
      const materials = Array.isArray(node.material) ? node.material : [node.material];
      materials.forEach((material) => {
        if (!material) return;
        material = material.clone();
      });
      this.prepareMaterial(node);
    });

    meshes.forEach((mesh) => {
      const bucket = this.bucketForMesh(mesh, hingeCenter);
      if (bucket === "left") this.leftPivot.attach(mesh);
      if (bucket === "right") this.rightPivot.attach(mesh);
      if (bucket === "hinge") this.hingeGroup.attach(mesh);
    });

    this.phoneGroup.rotation.x = 0;
    this.phoneGroup.scale.setScalar(this.baseScale * 0.34);
    this.phoneGroup.position.set(0, -0.02, 0);
    this.applyFold(0.96);
    this.removeAttribute("data-error");
  }

  prepareMaterial(mesh) {
    const clone = (material) => {
      if (!material) return material;
      const next = material.clone();
      const name = mesh.name.toLowerCase();
      const materialName = material.name?.toLowerCase() || "";

      next.envMapIntensity = name.includes("screen") ? 0.9 : 1.35;

      if (name.includes("visor_body") || materialName.includes("camera_bump")) {
        if (materialName.includes("side_shadow") || materialName.includes("metal_side")) {
          next.color?.set(0x7f8994);
          next.metalness = 0.36;
          next.roughness = 0.42;
          next.envMapIntensity = 1.45;
        } else {
          next.color?.set(0xa0abb6);
          next.metalness = 0.34;
          next.roughness = 0.34;
          next.envMapIntensity = 1.75;
        }
        if ("clearcoat" in next) next.clearcoat = 0.18;
        if ("clearcoatRoughness" in next) next.clearcoatRoughness = 0.36;
      } else if (name.includes("lens_top") || name.includes("lens_bottom")) {
        next.color?.set(0x050607);
        next.metalness = 0;
        next.roughness = 0.42;
        next.envMapIntensity = 0.42;
      } else if (name.includes("hinge_spine_antenna_band") || name.includes("folded_middle_antenna_band")) {
        next.color?.set(0x171a1f);
        next.metalness = 0.08;
        next.roughness = 0.58;
        next.envMapIntensity = 0.72;
      } else if (name.includes("antenna")) {
        next.color?.set(0x56626e);
        next.metalness = 0.2;
        next.roughness = 0.68;
        next.envMapIntensity = 0.5;
      } else if (name.includes("hinge") || materialName.includes("hinge_aluminum")) {
        next.color?.set(0xc2c9d0);
        next.metalness = 0.9;
        next.roughness = 0.16;
        next.envMapIntensity = 2.1;
      } else if (materialName.includes("frame_aluminum") || name.includes("body_left") || name.includes("body_right")) {
        next.metalness = Math.max(next.metalness ?? 0, 0.72);
        next.roughness = Math.min(next.roughness ?? 0.4, 0.28);
        next.envMapIntensity = 1.7;
      } else if (name.includes("back_grey") || name.includes("g_logo")) {
        next.envMapIntensity = 1.1;
      }

      next.needsUpdate = true;
      this.meshMaterials.push(next);
      return next;
    };

    mesh.material = Array.isArray(mesh.material)
      ? mesh.material.map((material) => clone(material))
      : clone(mesh.material);
  }

  bucketForMesh(mesh, hingeCenter) {
    const name = mesh.name.toLowerCase();
    if (name.includes("hinge")) return "hinge";
    if (/_l\d/.test(name) || name.includes("_left") || name.endsWith("left")) return "left";
    if (/_r\d/.test(name) || name.includes("_right") || name.endsWith("right")) return "right";
    if (
      name.includes("body_left") ||
      name.includes("back_grey") ||
      name.includes("visor") ||
      name.includes("lens") ||
      name.includes("flash") ||
      name.includes("camera_mic") ||
      name.includes("g_logo") ||
      name.includes("sim_tray")
    ) {
      return "left";
    }
    if (
      name.includes("body_right") ||
      name.includes("cover_") ||
      name.includes("front_camera") ||
      name.includes("inner_front") ||
      name.includes("usb") ||
      name.includes("speaker") ||
      name.includes("bottom_mic") ||
      name.includes("power") ||
      name.includes("volume") ||
      name.includes("button")
    ) {
      return "right";
    }

    if (name.includes("antenna") || name.includes("screen") || name.includes("bezel") || name.includes("body")) {
      const box = new THREE.Box3().setFromObject(mesh);
      const center = box.getCenter(new THREE.Vector3());
      return center.x < hingeCenter.x ? "left" : "right";
    }

    return "hinge";
  }

  getNamedBox(root, predicate) {
    const box = new THREE.Box3();
    let hasMatch = false;
    root.traverse((node) => {
      if (!node.isMesh || !predicate(node.name.toLowerCase())) return;
      box.expandByObject(node);
      hasMatch = true;
    });
    return hasMatch ? box : null;
  }

  createFogCloud() {
    const texture = this.createFogTexture();
    this.fogCloud = new THREE.Group();
    this.fogSprites = [];

    const rng = this.seededRandom(42);
    for (let i = 0; i < 96; i += 1) {
      const material = new THREE.SpriteMaterial({
        map: texture,
        color: new THREE.Color().setHSL(0.58, 0.18, mix(0.68, 0.94, rng())),
        transparent: true,
        opacity: 0,
        depthWrite: false,
        depthTest: true,
        blending: THREE.NormalBlending,
      });
      const sprite = new THREE.Sprite(material);
      const radius = Math.pow(rng(), 0.72) * 1.35;
      const angle = rng() * Math.PI * 2;
      sprite.position.set(
        Math.cos(angle) * radius * 0.85,
        (rng() - 0.5) * 0.9,
        Math.sin(angle) * radius * 0.42,
      );
      const scale = mix(0.22, 0.58, rng());
      sprite.scale.set(scale, scale, scale);
      sprite.userData.drift = new THREE.Vector3((rng() - 0.5) * 0.16, (rng() - 0.5) * 0.12, (rng() - 0.5) * 0.1);
      this.fogCloud.add(sprite);
      this.fogSprites.push(sprite);
    }

    this.fogCloud.visible = false;
    this.scene.add(this.fogCloud);
  }

  createFogTexture() {
    const canvas = document.createElement("canvas");
    canvas.width = 128;
    canvas.height = 128;
    const ctx = canvas.getContext("2d");
    const gradient = ctx.createRadialGradient(64, 64, 4, 64, 64, 62);
    gradient.addColorStop(0, "rgba(255,255,255,0.88)");
    gradient.addColorStop(0.34, "rgba(245,248,250,0.34)");
    gradient.addColorStop(1, "rgba(235,240,244,0)");
    ctx.fillStyle = gradient;
    ctx.fillRect(0, 0, 128, 128);
    const texture = new THREE.CanvasTexture(canvas);
    texture.colorSpace = THREE.SRGBColorSpace;
    return texture;
  }

  seededRandom(seed) {
    let value = seed;
    return () => {
      value = (value * 1664525 + 1013904223) % 4294967296;
      return value / 4294967296;
    };
  }

  updateScroll() {
    const forcedProgress = new URLSearchParams(window.location.search).get("progress");
    if (forcedProgress !== null) {
      this.progress = clamp(Number(forcedProgress) || 0);
      return;
    }

    const rect = this.getBoundingClientRect();
    const travel = Math.max(rect.height - window.innerHeight, 1);
    this.progress = clamp(-rect.top / travel);
  }

  resize() {
    const rect = this.stage.getBoundingClientRect();
    const width = Math.max(1, rect.width);
    const height = Math.max(1, rect.height);
    this.camera.aspect = width / height;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(width, height, false);
  }

  animate() {
    this.frame = requestAnimationFrame(() => this.animate());
    const dt = Math.min(this.clock.getDelta(), 0.05);
    const speed = this.reducedMotion.matches ? 1 : 1 - Math.pow(0.001, dt);
    this.smoothProgress = mix(this.smoothProgress, this.progress, speed);
    this.pointer.lerp(this.targetPointer, 0.08);
    this.applyTimeline(this.smoothProgress, dt);
    this.renderer.render(this.scene, this.camera);
  }

  applyTimeline(progress, dt) {
    const p = this.reducedMotion.matches ? 0.48 : progress;
    const pop = smoother(0.02, 0.24, p);
    const unfold = smoother(0.23, 0.44, p);
    const fold = smoother(0.55, 0.72, p);
    const spin = smoother(0.72, 0.90, p);
    const vanish = smoother(0.80, 1.0, p);
    const screenZoom = this.exitMode === "screen-zoom" ? smoother(0.76, 1.0, p) : 0;
    const fogVanish = this.exitMode === "screen-zoom" ? 0 : vanish;

    const closed = p < 0.53 ? mix(0.96, 0, unfold) : mix(0, 1.0, fold);
    this.applyFold(closed);

    const scaleOut = this.exitMode === "screen-zoom" ? mix(1, 2.95, screenZoom) : mix(1, 0.24, vanish);
    const scale = this.baseScale ? this.baseScale * mix(0.32, 1.08, pop) * scaleOut : 1;
    this.phoneGroup.scale.setScalar(scale);
    this.phoneGroup.position.z = mix(-2.1, 0.05, pop) + (this.exitMode === "screen-zoom" ? mix(0, 0.82, screenZoom) : mix(0, -0.65, vanish));
    this.phoneGroup.position.y = mix(-0.04, 0.02, pop) + mix(0, 0.02, screenZoom);
    this.phoneGroup.position.x = mix(0, 0.12, screenZoom);

    const spinAmount = this.exitMode === "screen-zoom" ? spin * (1 - screenZoom) : spin;
    this.showGroup.rotation.x = mix(0.08, -0.04, pop) + this.pointer.y * 0.035 * (1 - screenZoom) + spinAmount * Math.PI * 0.5;
    this.showGroup.rotation.y = mix(-0.58, 0.12, pop) + this.pointer.x * 0.06 * (1 - screenZoom) + spinAmount * Math.PI * 5.1;
    this.showGroup.rotation.z = mix(-0.08, 0.02, pop) + spinAmount * Math.PI * 0.72;
    if (this.exitMode === "screen-zoom") {
      this.showGroup.rotation.x = mix(this.showGroup.rotation.x, -0.02, screenZoom);
      this.showGroup.rotation.y = mix(this.showGroup.rotation.y, 0.1, screenZoom);
      this.showGroup.rotation.z = mix(this.showGroup.rotation.z, 0.02, screenZoom);
    }

    this.camera.position.z = mix(9.5, 4.8, pop) + mix(0, -2.15, screenZoom);
    this.camera.position.x = mix(-0.28, 0.0, pop) + this.pointer.x * 0.08;
    this.camera.position.y = mix(0.18, 0.08, pop) - this.pointer.y * 0.04;
    if (this.exitMode === "screen-zoom") {
      this.camera.position.x = mix(this.camera.position.x, -0.02, screenZoom);
      this.camera.position.y = mix(this.camera.position.y, 0.0, screenZoom);
    }
    this.camera.lookAt(0, 0, 0);

    this.scene.fog.density = this.exitMode === "screen-zoom" ? mix(0.011, 0.004, screenZoom) : mix(0.011, 0.058, vanish);
    this.keyLight.intensity = this.exitMode === "screen-zoom" ? mix(3.2, 2.2, screenZoom) : mix(3.2, 1.25, vanish);
    this.rimLight.intensity = mix(2.4, 3.6, spin);
    if (this.cameraBumpStripLight) {
      this.cameraBumpStripLight.intensity = mix(5.8, 2.2, screenZoom);
    }
    if (this.edgeGrazingLight) {
      this.edgeGrazingLight.intensity = mix(3.2, 1.2, screenZoom);
    }
    this.setModelOpacity(this.exitMode === "screen-zoom" ? 1 : clamp(1 - vanish * 1.18));
    this.applyFogCloud(fogVanish, spin, dt);
    this.applyCssParallax(p, pop, this.exitMode === "screen-zoom" ? screenZoom : vanish);
  }

  applyFold(closed) {
    if (!this.leftPivot || !this.rightPivot) return;
    const angle = closed * 1.48;
    this.leftPivot.rotation.y = -angle;
    this.rightPivot.rotation.y = angle;
    this.leftPivot.rotation.z = 0;
    this.rightPivot.rotation.z = 0;
  }

  setModelOpacity(opacity) {
    this.meshMaterials.forEach((material) => {
      material.transparent = opacity < 0.995;
      material.opacity = opacity;
      material.depthWrite = opacity > 0.3;
    });
  }

  applyFogCloud(vanish, spin, dt) {
    if (!this.fogCloud) return;
    this.fogCloud.visible = vanish > 0.01;
    this.fogCloud.position.set(0, 0, mix(0.02, -0.18, vanish));
    this.fogCloud.scale.setScalar(mix(0.5, 2.9, vanish));
    this.fogCloud.rotation.y += dt * mix(0.12, 1.2, spin);
    this.fogSprites.forEach((sprite, index) => {
      const pulse = 0.75 + Math.sin(performance.now() * 0.0007 + index) * 0.25;
      sprite.position.addScaledVector(sprite.userData.drift, dt * vanish);
      sprite.material.opacity = vanish * mix(0.05, 0.28, pulse);
    });
  }

  applyCssParallax(progress, pop, vanish) {
    const washX = mix(-36, 28, pop) + this.pointer.x * 18;
    const washY = mix(28, -16, pop) + this.pointer.y * 10;
    this.stage.style.setProperty("--wash-x", `${washX.toFixed(2)}px`);
    this.stage.style.setProperty("--wash-y", `${washY.toFixed(2)}px`);
    this.stage.style.setProperty("--fog-x", `${mix(42, 50, vanish).toFixed(1)}%`);
    this.stage.style.setProperty("--haze-opacity", String(mix(0.18, 0.78, vanish)));
    this.stage.style.setProperty("--screen-zoom", this.exitMode === "screen-zoom" ? vanish.toFixed(4) : "0");
    if (this.exitMode === "screen-zoom") {
      const takeover = smoother(0.18, 1, vanish);
      this.stage.style.setProperty("--screen-takeover-opacity", String(takeover));
      this.stage.style.setProperty("--screen-scale", String(mix(0.74, 4.9, takeover)));
      this.stage.style.setProperty("--screen-radius", `${mix(34, 0, takeover).toFixed(2)}px`);
      this.stage.style.setProperty("--screen-shift-x", `${mix(82, 0, takeover).toFixed(2)}px`);
      this.stage.style.setProperty("--screen-shift-y", `${mix(8, 0, takeover).toFixed(2)}px`);
      this.stage.style.setProperty("--screen-sheen", String(mix(0.3, 0.82, smoother(0.1, 0.74, takeover))));
    } else {
      this.stage.style.setProperty("--screen-takeover-opacity", "0");
      this.stage.style.setProperty("--screen-scale", "0.42");
      this.stage.style.setProperty("--screen-radius", "34px");
      this.stage.style.setProperty("--screen-shift-x", "0px");
      this.stage.style.setProperty("--screen-shift-y", "0px");
      this.stage.style.setProperty("--screen-sheen", "0");
    }
    this.style.setProperty("--progress", progress.toFixed(4));
  }
}

customElements.define("pixel-fold-showtime", PixelFoldShowtime);
