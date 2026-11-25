import React from 'react'

/** Makes the element of a view: its vsid and the props the parent passes. */
export type IViewFactory = (vsid: string, props: Record<string, unknown>) => React.ReactNode

export type ViewProps = {
  vsid: string
  className?: string
  style?: React.CSSProperties
}

export type ViewComponent = React.ComponentType<ViewProps> & { VIEW_ID: string }
