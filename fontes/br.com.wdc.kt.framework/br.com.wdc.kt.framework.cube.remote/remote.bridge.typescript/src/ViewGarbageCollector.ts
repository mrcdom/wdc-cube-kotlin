import type { ScopeCoordinator } from './ScopeCoordinator'
import { ViewBinding } from './ViewBinding'
import { BROWSER_VSID } from './constants'

/**
 * Drops the bindings of views the server released or that left the screen. The technology binding reports a view
 * entering the screen (`mount`) and leaving it (`mark`), and holds views it keeps alive without drawing them
 * (`hold`/`release`).
 */
export class ViewGarbageCollector {
  private readonly app: ScopeCoordinator
  private readonly garbageViewMap = new Map<string, ViewBinding>()
  private readonly holdCount = new Map<string, number>()
  private readonly autoHeldByScope = new Map<string, Set<string>>()
  /** The views on screen now. */
  private readonly mountedViews = new Set<string>()
  /** The views the server let go of that were still on screen: dropped when they leave it. */
  private readonly releasedByServer = new Set<string>()

  private taskHandler = 0

  constructor(app: ScopeCoordinator) {
    this.app = app
  }

  /** Keeps a view from being collected until the matching `release`. */
  hold(vsid: string) {
    this.holdCount.set(vsid, (this.holdCount.get(vsid) ?? 0) + 1)
    this.recover(vsid)
  }

  release(vsid: string) {
    const count = (this.holdCount.get(vsid) ?? 0) - 1
    if (count <= 0) {
      this.holdCount.delete(vsid)
    } else {
      this.holdCount.set(vsid, count)
    }
  }

  /**
   * Updates automatic holds based on ViewId references in a view's scope.
   * Releases holds for references that were removed, holds new references.
   */
  updateAutoHolds(binding: ViewBinding) {
    const scopeId = binding.getId()
    const prevRefs = this.autoHeldByScope.get(scopeId) ?? new Set()
    const newRefs = binding.getReferencedViewIds()

    for (const vsid of prevRefs) {
      if (!newRefs.has(vsid)) {
        this.release(vsid)
      }
    }

    for (const vsid of newRefs) {
      if (!prevRefs.has(vsid)) {
        this.hold(vsid)
      }
    }

    if (newRefs.size > 0) {
      this.autoHeldByScope.set(scopeId, newRefs)
    } else {
      this.autoHeldByScope.delete(scopeId)
    }
  }

  /**
   * Releases all automatic holds for a scope being collected.
   */
  private releaseAutoHolds(scopeId: string) {
    const refs = this.autoHeldByScope.get(scopeId)
    if (refs) {
      for (const vsid of refs) {
        this.release(vsid)
      }
      this.autoHeldByScope.delete(scopeId)
    }
  }

  /** The view is on screen. */
  mount(vsid: string) {
    this.mountedViews.add(vsid)
  }

  /**
   * The server let go of these views (`releasedViews`): it will never send them again. Each one is dropped now, or
   * as soon as it leaves the screen.
   */
  released(vsids: string[]) {
    for (const vsid of vsids) {
      if (this.mountedViews.has(vsid)) {
        this.releasedByServer.add(vsid)
      } else {
        this.collect(vsid)
      }
    }
  }

  private collect(vsid: string) {
    this.releaseAutoHolds(vsid)
    this.app.viewMap.delete(vsid)
    this.garbageViewMap.delete(vsid)
    this.releasedByServer.delete(vsid)
    this.cancelSchedulingIfEmpty()
  }

  /** The view left the screen: dropped now if the server released it, otherwise collected later unless held. */
  mark(binding: ViewBinding) {
    const vsid = binding.getId()
    this.mountedViews.delete(vsid)
    if (this.releasedByServer.has(vsid)) {
      this.collect(vsid)
      return
    }
    if (vsid !== BROWSER_VSID) {
      this.garbageViewMap.set(vsid, binding)
      this.scheduleCollection()
    }
  }

  /** Takes back a view marked for collection; `null` if it is not marked. */
  recover(vsid: string) {
    const binding = this.garbageViewMap.get(vsid)
    if (binding) {
      this.app.viewMap.set(vsid, binding)
      this.garbageViewMap.delete(vsid)
      this.cancelSchedulingIfEmpty()
      return binding
    } else {
      return null
    }
  }

  private scheduleCollection() {
    clearTimeout(this.taskHandler)
    this.taskHandler = setTimeout(this.doCollection, 4000)
  }

  private readonly doCollection = () => {
    clearTimeout(this.taskHandler)

    if (this.garbageViewMap.size > 0) {
      const toCollect: string[] = []

      this.garbageViewMap.forEach((_, vsid) => {
        if (!this.holdCount.has(vsid)) {
          toCollect.push(vsid)
        }
      })

      for (const vsid of toCollect) {
        this.releaseAutoHolds(vsid)
        this.app.viewMap.delete(vsid)
        this.garbageViewMap.delete(vsid)
      }
    }
  }

  private cancelSchedulingIfEmpty() {
    if (this.garbageViewMap.size === 0) {
      clearTimeout(this.taskHandler)
    }
  }
}
