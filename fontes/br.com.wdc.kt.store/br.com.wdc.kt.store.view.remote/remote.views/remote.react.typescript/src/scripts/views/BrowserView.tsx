import React, { ReactNode } from 'react'
import Alert from '@mui/material/Alert'
import AlertTitle from '@mui/material/AlertTitle'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import LinearProgress from '@mui/material/LinearProgress'
import Link from '@mui/material/Link'
import Typography from '@mui/material/Typography'
import bridge, { BaseViewClass, type ViewProps, BROWSER_VID, type BrowserScope } from 'wdc-cube-remote-bridge-react'

// :: Actions

const ON_ALERT_OK = 1

// :: View

class BrowserViewClass extends BaseViewClass<ViewProps, BrowserScope> {
  // :: Renderes

  override render({ className }: ViewProps) {
    const { scope } = this

    React.useEffect(() => {
      bridge.onStart()
      return () => {
        bridge.onStop()
      }
    }, [])

    let connectionAlert: ReactNode
    if (scope.error) {
      connectionAlert = <ConnectionAlert delay={scope.error.delay} onReconnectNow={this.onRecconectNow} />
    }

    let rootView: ReactNode
    if (scope.contentViewId) {
      rootView = bridge.createView(scope.contentViewId)
    } else {
      rootView = (
        <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh' }}>
          <CircularProgress />
        </Box>
      )
    }

    let alertView: ReactNode
    if (scope.alertMessage) {
      alertView = (
        <AppAlert code={scope.alertMessage.id} args={scope.alertMessage.args ?? []} onDismiss={this.emitAlertOk} />
      )
    }

    return (
      <Box className={className}>
        {scope.submitting && (
          <LinearProgress
            sx={{
              position: 'fixed',
              top: 0,
              left: 0,
              right: 0,
              zIndex: 9999,
            }}
          />
        )}
        {connectionAlert}
        {alertView}
        {rootView}
      </Box>
    )
  }

  // :: Emissores

  readonly emitAlertOk = () => {
    const { vsid } = this
    bridge.submit(vsid, ON_ALERT_OK)
  }

  readonly onRecconectNow = () => {
    bridge.connect()
  }
}

export default BaseViewClass.FC(BrowserViewClass, BROWSER_VID)

// :: Internal - AppAlert

type AppAlertProps = {
  code: number
  args: string[]
  onDismiss?: () => void
}

function AppAlert(props: AppAlertProps) {
  let msgNode: React.ReactNode | null = null
  let detailMessage: string | null = null
  switch (props.code) {
    case -1:
      msgNode = props.args[0]
      detailMessage = props.args[1]
      break
    case -2:
      msgNode = 'The URI ' + props.args[0] + ' is not reachable'
      detailMessage = props.args[1]
      break
    default:
      msgNode = props.args.length > 0 ? props.args[0] : 'An unexpected error occurred'
  }

  return (
    <Alert
      severity="warning"
      sx={{ m: 2 }}
      action={
        <Button color="inherit" size="small" onClick={props.onDismiss}>
          Ok
        </Button>
      }
    >
      <AlertTitle>Warning!</AlertTitle>
      {msgNode}
      {detailMessage && (
        <Typography variant="body2" sx={{ mt: 1 }}>
          {detailMessage}
        </Typography>
      )}
    </Alert>
  )
}

// :: Internal - ConnectionAlert

type ConnectionAlertProps = {
  delay: number
  onReconnectNow: () => void
}

function ConnectionAlert(props: ConnectionAlertProps) {
  let timeElm: React.ReactNode, retryElm: React.ReactNode
  if (props.delay > 0) {
    let seconds = Math.floor(props.delay / 1000)
    let minutes = 0
    if (seconds > 60) {
      minutes = Math.floor(seconds / 60)
      seconds = seconds - minutes * 60
    }

    timeElm = (
      <Typography component="span" variant="body2">
        {minutes > 0 ? `Connecting in ${minutes}m ${seconds}s...` : `Connecting in ${seconds}s...`}
      </Typography>
    )
    retryElm = (
      <Link
        component="button"
        variant="body2"
        underline="always"
        onClick={props.onReconnectNow}
        sx={{ cursor: 'pointer', ml: 1 }}
      >
        Try now
      </Link>
    )
  } else {
    timeElm = (
      <Typography component="span" variant="body2">
        Connecting now...
      </Typography>
    )
  }

  return (
    <Box sx={{ textAlign: 'center' }}>
      <Alert
        severity="warning"
        icon={false}
        sx={{
          display: 'inline-flex',
          borderRadius: '0 0 4px 4px',
          py: 0,
          px: 1.5,
        }}
      >
        <Typography component="span" variant="body2" sx={{ fontWeight: 'bold' }}>
          Not connected.
        </Typography>{' '}
        {timeElm} {retryElm}.
      </Alert>
    </Box>
  )
}
