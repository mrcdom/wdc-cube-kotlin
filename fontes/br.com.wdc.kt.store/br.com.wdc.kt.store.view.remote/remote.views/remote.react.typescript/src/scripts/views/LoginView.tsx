import React, { ReactNode } from 'react'
import Alert from '@mui/material/Alert'
import Avatar from '@mui/material/Avatar'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Card from '@mui/material/Card'
import CardContent from '@mui/material/CardContent'
import CircularProgress from '@mui/material/CircularProgress'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import LocalMallIcon from '@mui/icons-material/LocalMall'
import bridge, { BaseViewClass, type ViewProps } from 'wdc-cube-remote-bridge-react'
import { Colors } from '@root/theme'

// :: Actions

const ON_ENTER = 1

// :: View

export type LoginScope = {
  userName?: string
  password?: string
  loading?: boolean
  errorMessage?: string
}

class LoginViewClass extends BaseViewClass<ViewProps, LoginScope> {
  // :: Render

  override render({ className }: ViewProps): React.ReactNode {
    const { scope } = this

    return (
      <Box
        className={className}
        sx={{
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: `linear-gradient(180deg, ${Colors.Primary} 0%, ${Colors.PrimaryContainer} 100%)`,
        }}
      >
        <Card
          sx={{
            width: 400,
            borderRadius: '12px',
            boxShadow: '0px 2px 8px rgba(0,0,0,0.12)',
          }}
        >
          <CardContent sx={{ p: 3 }}>
            <form autoComplete="off" onSubmit={(e) => e.preventDefault()}>
              <Stack direction="column" spacing={2} sx={{ alignItems: 'center' }}>
                {/* Logo */}
                <Avatar
                  sx={{
                    width: 72,
                    height: 72,
                    bgcolor: Colors.Primary,
                    borderRadius: '18px',
                  }}
                >
                  <LocalMallIcon sx={{ fontSize: 40 }} />
                </Avatar>

                <Box sx={{ position: 'relative' }}>
                  <Typography variant="h5" sx={{ fontWeight: 'bold' }}>
                    WDC Store
                  </Typography>
                  <Typography
                    variant="caption"
                    sx={{
                      position: 'absolute',
                      bottom: -16,
                      right: 0,
                      color: Colors.OnSurfaceVariant,
                      opacity: 0.5,
                      fontSize: 10,
                    }}
                  >
                    react
                  </Typography>
                </Box>

                <Typography variant="body2" sx={{ color: Colors.OnSurfaceVariant, textAlign: 'center' }}>
                  Sign in to continue
                </Typography>

                {/* Username */}
                <TextField
                  inputRef={this.usrInputRef}
                  label="Username"
                  type="text"
                  name="login-usr"
                  autoComplete="username"
                  defaultValue={scope.userName ?? ''}
                  fullWidth
                  disabled={!!scope.loading}
                />

                {/* Password: a real password field, so screen readers don't read it and password managers fill it */}
                <TextField
                  inputRef={this.pwdInputRef}
                  label="Password"
                  type="password"
                  name="login-pwd"
                  autoComplete="current-password"
                  defaultValue={scope.password ?? ''}
                  fullWidth
                  disabled={!!scope.loading}
                  onKeyDown={this.onKeyDown}
                />

                {/* Error message */}
                {!!scope.errorMessage && (
                  <Alert severity="error" sx={{ width: '100%', borderRadius: '8px' }}>
                    {scope.errorMessage}
                  </Alert>
                )}

                {/* Login button */}
                <Button
                  type="button"
                  variant="contained"
                  fullWidth
                  onClick={this.emitOnEnter}
                  disabled={!!scope.loading}
                  sx={{
                    height: 48,
                    borderRadius: '12px',
                    fontWeight: 'bold',
                    fontSize: 16,
                    textTransform: 'none',
                  }}
                >
                  {scope.loading ? <CircularProgress size={24} sx={{ color: '#fff' }} /> : 'Sign in'}
                </Button>
              </Stack>
            </form>
          </CardContent>
        </Card>
      </Box>
    )
  }

  // :: Element Refs

  usrInputRef: React.RefObject<HTMLInputElement | null> = {
    current: null,
  }

  pwdInputRef: React.RefObject<HTMLInputElement | null> = {
    current: null,
  }

  // :: Emissors

  readonly emitOnEnter = async () => {
    const { vsid } = this
    const userName = this.usrInputRef.current?.value ?? ''
    const password = this.pwdInputRef.current?.value ?? ''
    bridge.setFormField(vsid, 'userName', userName)
    bridge.setFormField(vsid, 'password', await bridge.cipher(password))
    bridge.submit(vsid, ON_ENTER)
  }

  readonly onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      e.preventDefault()
      this.emitOnEnter()
    }
  }
}

export default BaseViewClass.FC(LoginViewClass, 'c677cda52d14')
