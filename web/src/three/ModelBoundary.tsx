/**
 * Error boundary around the GLB phone model. Until the .glb file is dropped
 * into public/models/, useGLTF throws on the 404 — this catches it so the
 * intro still renders (background + outline) instead of crashing the page.
 */
import { Component, type ReactNode } from "react";

export class ModelBoundary extends Component<
  { children: ReactNode; fallback: ReactNode },
  { failed: boolean }
> {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  render() {
    return this.state.failed ? this.props.fallback : this.props.children;
  }
}
