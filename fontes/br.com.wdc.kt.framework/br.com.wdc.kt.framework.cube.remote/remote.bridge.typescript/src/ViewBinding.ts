import * as LangUtils from './utils/LangUtils'
import { NOOP_VOID } from './constants'

/** Holds one view's scope, as the server last published it. */
export class ViewBinding {
  private readonly __svid: string
  private readonly __scope: Record<string, unknown> = {}
  private __referencedViewIds: Set<string> = new Set()

  /** Repaints the view; set by the technology binding that draws it, called when a new scope arrives. */
  forceUpdate = NOOP_VOID

  constructor(svid: string) {
    this.__svid = svid
  }

  getId() {
    return this.__svid
  }

  getScope() {
    return this.__scope
  }

  getReferencedViewIds() {
    return this.__referencedViewIds
  }

  setScope(newScope: Record<string, unknown>) {
    const newRefs = ViewBinding.extractViewIdRefs(newScope)
    this.__referencedViewIds = newRefs

    LangUtils.deleteProperties(this.__scope)
    Object.assign(this.__scope, newScope)
    this.forceUpdate()
  }

  private static extractViewIdRefs(scope: Record<string, unknown>): Set<string> {
    const refs = new Set<string>()
    for (const key in scope) {
      if (key.endsWith('ViewId')) {
        const value = scope[key]
        if (typeof value === 'string' && value.length > 0) {
          refs.add(value)
        }
      }
    }
    return refs
  }
}
