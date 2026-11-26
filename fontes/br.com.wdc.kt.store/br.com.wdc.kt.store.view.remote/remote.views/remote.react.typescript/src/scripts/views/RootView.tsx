import React from 'react'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import bridge, { BaseViewClass, type ViewProps } from 'wdc-cube-remote-bridge-react'
import { messages, translate, type ScopeMessage } from '@root/i18n'

export type RootScope = {
  contentViewId?: string
  errorMessage?: ScopeMessage
}

class RootViewClass extends BaseViewClass<ViewProps, RootScope> {
  // :: Renderes

  override render({ className }: ViewProps) {
    const { scope } = this

    if (scope.errorMessage) {
      return (
        <Box sx={{ m: 2 }}>
          <Alert severity="error">{translate(scope.errorMessage)}</Alert>
        </Box>
      )
    }

    if (scope.contentViewId) {
      return bridge.createView(scope.contentViewId)
    }

    return (
      <Box className={className} sx={{ m: 2 }}>
        <Alert severity="error">{messages.text('browser.missingHomeContent')}</Alert>
      </Box>
    )
  }
}

export default BaseViewClass.FC(RootViewClass, 'f2d345c4a610')
