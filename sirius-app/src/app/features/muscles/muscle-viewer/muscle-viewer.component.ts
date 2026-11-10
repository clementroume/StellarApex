import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  input,
  output,
  signal,
  viewChild
} from '@angular/core';
import {NgIcon} from '@ng-icons/core';
import {TranslatePipe} from '@ngx-translate/core';
import {HlmButton} from '@spartan-ng/helm/button';
import {DoubleSide, Mesh, Object3D, PerspectiveCamera, Scene, ShaderMaterial, Vector3, WebGLRenderer} from 'three';
import {DRACOLoader} from 'three/addons/loaders/DRACOLoader.js';
import {GLTFLoader} from 'three/addons/loaders/GLTFLoader.js';
import {OrbitControls} from 'three/addons/controls/OrbitControls.js';
import {Theme} from '../../../core/services/theme.service';
import {DRACO_PATH, MODEL_URL, MuscleModelEntry} from '../muscle-model/muscle-model';

/** Camera directions, in the axes of the model: the same as the images (tools/muscle-model/common.py). */
const VIEWS: Record<string, [number, number, number]> = {
  FRONT: [0, 0.1, 1],
  BACK: [0, 0.1, -1],
  SIDE_LEFT: [-1, 0.1, 0],
  FRONT_LEFT: [-0.65, 0.15, 0.75],
  BACK_LEFT: [-0.65, 0.15, -0.75]
};
const GLASS: Record<Theme, {ghost: number[]; ghostOpacity: number; bone: number[]; boneOpacity: number}> = {
  dark: {ghost: [0.42, 0.52, 0.68], ghostOpacity: 0.55, bone: [0.72, 0.74, 0.76], boneOpacity: 0.8},
  light: {ghost: [0.2, 0.27, 0.37], ghostOpacity: 0.72, bone: [0.34, 0.37, 0.42], boneOpacity: 0.72}
};
const FIELD_OF_VIEW = 31;
const FLIGHT_MS = 500;
const VERTEX_SHADER = `
  varying vec3 vN; varying vec3 vV;
  void main() {
    vec4 p = modelViewMatrix * vec4(position, 1.0);
    vN = normalMatrix * normal; vV = -p.xyz;
    gl_Position = projectionMatrix * p;
  }`;
// The red of the muscle is part of the illustration: it does not follow the palettes.
const RELIEF_SHADER = `
  varying vec3 vN; varying vec3 vV;
  vec3 ramp(float t) {
    const vec3 a = vec3(0.16, 0.008, 0.016), b = vec3(0.38, 0.025, 0.035), c = vec3(0.74, 0.075, 0.065),
      d = vec3(0.95, 0.20, 0.13), e = vec3(1.0, 0.36, 0.26);
    if (t < 0.30) return mix(a, b, smoothstep(0.02, 0.30, t));
    if (t < 0.60) return mix(b, c, (t - 0.30) / 0.30);
    if (t < 0.84) return mix(c, d, (t - 0.60) / 0.24);
    return mix(d, e, (t - 0.84) / 0.14);
  }
  void main() {
    vec3 N = normalize(vN); vec3 V = normalize(vV);
    if (!gl_FrontFacing) N = -N;
    float l = clamp(dot(N, normalize(vec3(-0.62, 0.60, 0.50))) * 2.8 - 0.92, 0.0, 1.0);
    float facing = 1.0 - abs(dot(N, V));
    gl_FragColor = vec4(ramp(l) * (1.0 - 0.55 * facing * facing), 1.0);
    #include <colorspace_fragment>
  }`;
const GLASS_SHADER = `
  uniform vec3 uColor; uniform float uOpacity; varying vec3 vN; varying vec3 vV;
  void main() {
    float f = pow(1.0 - abs(dot(normalize(vN), normalize(vV))), 2.5) * uOpacity;
    gl_FragColor = vec4(uColor, f);
    #include <colorspace_fragment>
  }`;

/** Where the camera stands to frame a muscle exactly as its image does. */
export function cameraPositionOf(entry: MuscleModelEntry): [number, number, number] {
  const [x, y, z] = VIEWS[entry.view] ?? VIEWS['FRONT'];
  const length = Math.hypot(x, y, z);
  const distance = (entry.size * entry.zoom) / length;
  return [entry.center[0] + x * distance, entry.center[1] + y * distance, entry.center[2] + z * distance];
}

function reliefMaterial(): ShaderMaterial {
  return new ShaderMaterial({
    vertexShader: VERTEX_SHADER,
    fragmentShader: RELIEF_SHADER,
    side: DoubleSide // a part may be seen from inside when the camera zooms in
  });
}

function glassMaterial(): ShaderMaterial {
  return new ShaderMaterial({
    transparent: true,
    depthWrite: false,
    vertexShader: VERTEX_SHADER,
    fragmentShader: GLASS_SHADER,
    uniforms: {uColor: {value: new Vector3()}, uOpacity: {value: 0.5}}
  });
}

/**
 * The three.js scene of a muscle. Only imported inside a `@defer` block: three.js never reaches another chunk.
 * Renders on demand, stops off screen, frees its WebGL resources when destroyed.
 */
@Component({
  selector: 'app-muscle-viewer',
  standalone: true,
  imports: [HlmButton, NgIcon, TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {class: 'block'},
  template: `
    <canvas #canvas class="block size-full" aria-hidden="true"></canvas>
    @if (loaded()) {
      <button type="button" hlmBtn variant="outline" size="icon" class="absolute bottom-0 right-0 m-1 rounded-full"
              [attr.aria-label]="'MUSCLE.SCENE.RECENTER' | translate" (click)="recenter()">
        <ng-icon name="heroArrowPath" aria-hidden="true"/>
      </button>
    }
  `
})
export class MuscleViewerComponent {
  // --- INJECTIONS ---
  entry = input.required<MuscleModelEntry>();
  theme = input.required<Theme>();
  ready = output<void>();
  failed = output<void>();
  private readonly destroyRef = inject(DestroyRef);
  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');

  // --- STATE (SIGNALS & VARIABLES) ---
  readonly loaded = signal(false);
  private readonly reducedMotion = globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
  private readonly scene = new Scene();
  private readonly camera = new PerspectiveCamera(FIELD_OF_VIEW, 1, 0.01, 30);
  private readonly relief = reliefMaterial();
  private readonly ghost = glassMaterial();
  private readonly bone = glassMaterial();
  private readonly parts = new Map<string, Mesh>();
  private renderer?: WebGLRenderer;
  private controls?: OrbitControls;
  private frame: number | null = null;
  private dirty = false;
  private visible = true;
  private framed = false;
  private selected?: MuscleModelEntry;
  private destroyed = false;
  private flight: {start: number; fromCamera: Vector3; fromTarget: Vector3; toCamera: Vector3; toTarget: Vector3} | null = null;

  // --- LIFECYCLE ---
  constructor() {
    afterNextRender(() => void this.start());
    this.destroyRef.onDestroy(() => this.dispose());
    effect(() => {
      const entry = this.entry();
      if (this.loaded() && entry !== this.selected) this.select(entry);
    });
    effect(() => {
      this.applyTheme(this.theme());
      this.requestRender();
    });
  }

  private async start(): Promise<void> {
    try {
      const canvas = this.canvas().nativeElement;
      const renderer = new WebGLRenderer({canvas, antialias: true, alpha: true, powerPreference: 'high-performance'});
      this.renderer = renderer;
      renderer.setPixelRatio(Math.min(globalThis.devicePixelRatio || 1, 2));
      this.setUpControls(canvas);
      this.watchCanvas(canvas);
      const draco = new DRACOLoader().setDecoderPath(DRACO_PATH).setDecoderConfig({type: 'wasm'});
      const gltf = await new GLTFLoader().setDRACOLoader(draco).loadAsync(MODEL_URL).finally(() => draco.dispose());
      if (this.destroyed) {
        gltf.scene.traverse(object => (object as Mesh).geometry?.dispose());
        return;
      }
      this.addModel(gltf.scene);
      this.loaded.set(true);
      this.select(this.entry());
      this.renderNow();
      this.ready.emit();
    } catch (error) {
      console.warn('3D muscle model unavailable', error);
      if (!this.destroyed) this.failed.emit();
    }
  }

  private setUpControls(canvas: HTMLCanvasElement): void {
    const controls = new OrbitControls(this.camera, canvas);
    this.controls = controls;
    controls.enablePan = false;
    controls.enableDamping = !this.reducedMotion;
    controls.dampingFactor = 0.08;
    controls.minDistance = 0.15;
    controls.maxDistance = 6;
    controls.addEventListener('change', this.requestRender);
    // OrbitControls claims every gesture: give the vertical ones back so that one finger still scrolls the page.
    canvas.style.touchAction = 'pan-y';
    canvas.addEventListener('wheel', this.keepWheelForPage, {capture: true, passive: true});
    canvas.addEventListener('webglcontextlost', this.onContextLost);
  }

  private watchCanvas(canvas: HTMLCanvasElement): void {
    const resize = new ResizeObserver(() => {
      const width = canvas.clientWidth;
      const height = canvas.clientHeight;
      this.renderer?.setSize(width, height, false);
      this.camera.aspect = width / Math.max(height, 1);
      this.camera.updateProjectionMatrix();
      this.requestRender();
    });
    resize.observe(canvas);
    const onScreen = new IntersectionObserver(([entry]) => {
      this.visible = entry.isIntersecting;
      if (this.visible && this.dirty) this.requestRender();
    });
    onScreen.observe(canvas);
    this.destroyRef.onDestroy(() => {
      resize.disconnect();
      onScreen.disconnect();
    });
  }

  private addModel(model: Object3D): void {
    model.traverse(object => {
      const mesh = object as Mesh;
      if (!mesh.isMesh) return;
      mesh.frustumCulled = false;
      mesh.material = mesh.name === 'bones' ? this.bone : this.ghost;
      if (mesh.name !== 'bones' && mesh.name !== 'context') this.parts.set(mesh.name, mesh);
    });
    this.scene.add(model);
    this.applyTheme(this.theme());
  }

  // --- ACTIONS & EVENTS ---
  recenter(): void {
    this.flyTo(this.entry());
  }

  private readonly keepWheelForPage = (event: WheelEvent): void => {
    // Without Ctrl (a trackpad pinch sets it too) the wheel scrolls the page, not the model.
    if (!event.ctrlKey) event.stopImmediatePropagation();
  };

  private readonly onContextLost = (): void => {
    this.failed.emit();
  };

  // --- HELPERS ---
  private select(entry: MuscleModelEntry): void {
    this.selected = entry;
    const chosen = new Set(entry.parts);
    this.parts.forEach((mesh, name) => {
      mesh.material = chosen.has(name) ? this.relief : this.ghost;
    });
    this.flyTo(entry);
  }

  private flyTo(entry: MuscleModelEntry): void {
    const toCamera = new Vector3(...cameraPositionOf(entry));
    const toTarget = new Vector3(...(entry.center as [number, number, number]));
    if (!this.framed || this.reducedMotion) {
      this.framed = true;
      this.flight = null;
      this.camera.position.copy(toCamera);
      this.controls?.target.copy(toTarget);
    } else {
      this.flight = {
        start: performance.now(),
        fromCamera: this.camera.position.clone(),
        fromTarget: this.controls!.target.clone(),
        toCamera,
        toTarget
      };
    }
    this.requestRender();
  }

  private applyTheme(theme: Theme): void {
    const colors = GLASS[theme];
    this.ghost.uniforms['uColor'].value.fromArray(colors.ghost);
    this.ghost.uniforms['uOpacity'].value = colors.ghostOpacity;
    this.bone.uniforms['uColor'].value.fromArray(colors.bone);
    this.bone.uniforms['uOpacity'].value = colors.boneOpacity;
  }

  private readonly requestRender = (): void => {
    this.dirty = true;
    if (this.frame === null && this.visible && this.loaded()) {
      this.frame = requestAnimationFrame(() => this.renderNow());
    }
  };

  private renderNow(): void {
    if (this.frame !== null) cancelAnimationFrame(this.frame);
    this.frame = null;
    this.dirty = false;
    if (this.flight) this.advanceFlight(this.flight);
    this.controls?.update(); // fires 'change' while the damping still moves the camera
    this.renderer?.render(this.scene, this.camera);
  }

  private advanceFlight(flight: NonNullable<typeof this.flight>): void {
    const progress = Math.min((performance.now() - flight.start) / FLIGHT_MS, 1);
    const eased = progress * (2 - progress);
    this.camera.position.lerpVectors(flight.fromCamera, flight.toCamera, eased);
    this.controls!.target.lerpVectors(flight.fromTarget, flight.toTarget, eased);
    if (progress === 1) this.flight = null;
    else this.requestRender();
  }

  private dispose(): void {
    this.destroyed = true;
    if (this.frame !== null) cancelAnimationFrame(this.frame);
    const canvas = this.canvas().nativeElement;
    canvas.removeEventListener('wheel', this.keepWheelForPage, {capture: true});
    canvas.removeEventListener('webglcontextlost', this.onContextLost);
    this.controls?.dispose();
    this.scene.traverse(object => (object as Mesh).geometry?.dispose());
    [this.relief, this.ghost, this.bone].forEach(material => material.dispose());
    this.renderer?.dispose();
    this.renderer?.forceContextLoss();
  }
}
