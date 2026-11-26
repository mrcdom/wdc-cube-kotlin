import React from 'react'
import bridge from './Bridge.js'
import type { ViewProps } from './types.js'
import type { ViewBinding } from 'wdc-cube-remote-bridge'

const ZERO_DEPS: React.DependencyList = []

/** A view as a class: `FC` turns it into the function component registered under the scope's view id. */
export abstract class BaseViewClass<P extends ViewProps, S> {
  vsid!: string
  scope!: S
  binding!: ViewBinding
  initialized!: boolean

  abstract render(props: P, initial?: boolean): React.ReactNode

  protected forceUpdate() {
    this.binding.forceUpdate()
  }

  static FC<P extends ViewProps, S>(ctor: new (props: P) => BaseViewClass<P, S>, viewId: string) {
    View.VIEW_ID = viewId
    return View

    function View(props: P) {
      // one instance for the component's whole life, made from its first props
      // eslint-disable-next-line react-hooks/exhaustive-deps
      const memo = React.useMemo(() => new ctor(props), ZERO_DEPS)

      const { scope, binding } = bridge.bindView<S>(props.vsid)
      memo.vsid = props.vsid
      memo.scope = scope
      memo.binding = binding

      if (memo.initialized) {
        return memo.render(props, false)
      }

      memo.initialized = true
      return memo.render(props, true)
    }
  }
}

/** A piece of a view as a class, with no scope of its own. */
export abstract class BasePanelClass<P> {
  initialized!: boolean

  abstract render(props: P, initial?: boolean): React.ReactNode

  static FC<P>(ctor: new (props: P) => BasePanelClass<P>) {
    return Panel

    function Panel(props: P) {
      // one instance for the component's whole life, made from its first props
      // eslint-disable-next-line react-hooks/exhaustive-deps
      const memo = React.useMemo(() => new ctor(props), ZERO_DEPS)

      if (memo.initialized) {
        return memo.render(props, false)
      }

      memo.initialized = true
      return memo.render(props, true)
    }
  }
}
