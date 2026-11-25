import React from 'react'
import type { ScopeCoordinator } from 'wdc-cube-remote-bridge'
import type { IViewFactory } from './types.js'

/**
 * The React side of a coordinator: the registry of view factories, and the hooks that bind a component to its view's
 * binding and report it entering and leaving the screen to the coordinator's collector.
 */
export class ReactViews {
  private readonly coordinator: ScopeCoordinator
  private readonly factories = new Map<string, IViewFactory>()

  constructor(coordinator: ScopeCoordinator) {
    this.coordinator = coordinator
  }

  register(viewId: string, factory: IViewFactory) {
    this.factories.set(viewId, factory)
  }

  /** The element of a view, by the factory registered under its view id (the part of the vsid before `:`). */
  create(vsid: string, props?: Record<string, unknown>) {
    const viewId = vsid.split(/:/g)[0]
    const factory = this.factories.get(viewId)
    if (!factory) {
      throw new Error(`No view registered for viewId: "${viewId}"`)
    }
    this.coordinator.viewBinding(vsid)
    return factory(vsid, props ?? {})
  }

  /** Hook: binds a function component to its view's scope; it repaints when a new scope arrives. */
  useBinding<T>(vsid: string) {
    const coordinator = this.coordinator
    const binding = coordinator.findView(vsid)
    if (!binding) {
      throw new Error(`Missing View Scope for id(${vsid})`)
    }

    // a hook as a method of the coordinator's view class: called only from function components, in the same order
    // eslint-disable-next-line react-hooks/rules-of-hooks
    const [, setUpdateCount] = React.useState(0)
    binding.forceUpdate = () => setUpdateCount((count) => count + 1)

    // eslint-disable-next-line react-hooks/rules-of-hooks -- as above
    React.useEffect(() => {
      // attached
      coordinator.viewGarbageCollector.mount(vsid)
      return () => {
        // detached
        coordinator.viewGarbageCollector.mark(binding)
      }
      // once per mount: a component draws one view for its whole life
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    return { scope: binding.getScope() as T, binding }
  }

  /** Hook: keeps a view the component does not draw from being collected. */
  useHold(vsid: string | undefined) {
    const collector = this.coordinator.viewGarbageCollector
    // eslint-disable-next-line react-hooks/rules-of-hooks -- a hook as a method, as above
    React.useEffect(() => {
      if (vsid) {
        collector.hold(vsid)
      }
      return () => {
        if (vsid) {
          collector.release(vsid)
        }
      }
      // the collector is the coordinator's, the same for the component's whole life
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [vsid])
  }
}
