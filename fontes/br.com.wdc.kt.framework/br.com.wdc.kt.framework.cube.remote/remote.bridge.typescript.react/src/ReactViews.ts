import React from 'react'
import type { ScopeCoordinator } from '@wdc/remote-bridge'
import type { IViewFactory } from './types'

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

    const [, setUpdateCount] = React.useState(0)
    binding.forceUpdate = () => setUpdateCount((count) => count + 1)

    React.useEffect(() => {
      // attached
      coordinator.viewGarbageCollector.mount(vsid)
      return () => {
        // detached
        coordinator.viewGarbageCollector.mark(binding)
      }
    }, [])

    return { scope: binding.getScope() as T, binding }
  }

  /** Hook: keeps a view the component does not draw from being collected. */
  useHold(vsid: string | undefined) {
    const collector = this.coordinator.viewGarbageCollector
    React.useEffect(() => {
      if (vsid) {
        collector.hold(vsid)
      }
      return () => {
        if (vsid) {
          collector.release(vsid)
        }
      }
    }, [vsid])
  }
}
