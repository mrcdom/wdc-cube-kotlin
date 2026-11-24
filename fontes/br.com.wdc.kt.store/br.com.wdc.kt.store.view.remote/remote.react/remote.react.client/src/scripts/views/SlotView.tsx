import React from 'react'
import app, { type ViewProps } from '@root/App'

export type SlotScope = {
  slot?: string
}

SlotView.VIEW_ID = '798574115fcd'

export default function SlotView({ vsid }: ViewProps) {
  const { scope } = app.bindView<SlotScope>(vsid)
  return scope.slot ? app.createView(scope.slot) : <></>
}
