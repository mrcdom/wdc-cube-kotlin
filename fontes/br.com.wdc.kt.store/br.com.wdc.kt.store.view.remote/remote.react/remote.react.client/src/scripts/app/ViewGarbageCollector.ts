import type { Application } from './Application'
import { ViewBinding } from './ViewBinding'
import { BROWSER_VSID } from './constants'

export class ViewGarbageCollector {
  private readonly app: Application
  private readonly garbageViewMap = new Map<string, ViewBinding>()
  private readonly holdCount = new Map<string, number>()
  private readonly autoHeldByScope = new Map<string, Set<string>>()

  private taskHandler = 0

  constructor(app: Application) {
    this.app = app
  }

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

  mark(binding: ViewBinding) {
    const vsid = binding.getId()
    if (vsid !== BROWSER_VSID) {
      this.garbageViewMap.set(vsid, binding)
      this.scheduleCollection()
    }
  }

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
