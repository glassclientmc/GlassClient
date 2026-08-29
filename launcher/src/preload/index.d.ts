import type { GlassClientApi } from './index'

declare global {
  interface Window {
    api: GlassClientApi
  }
}
