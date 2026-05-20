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
import app, { type ViewProps } from '@root/App'
import { BaseViewClass } from '@root/utils/ViewUtils'
import { Colors } from '@root/theme'

// :: Actions

const ON_ENTER = 1

// :: View

export type LoginViewState = {
  userName?: string
  password?: string
  loading?: boolean
  errorMessage?: string
}

class LoginViewClass extends BaseViewClass<ViewProps, LoginViewState> {
  // :: Render

  override render({ className }: ViewProps): React.ReactNode {
    const { state } = this

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

                <Typography variant="h5" sx={{ fontWeight: 'bold' }}>
                  Shopping
                </Typography>

                <Typography
                  variant="body2"
                  sx={{ color: Colors.OnSurfaceVariant, textAlign: 'center' }}
                >
                  Entre com suas credenciais para continuar
                </Typography>

                {/* Username */}
                <TextField
                  inputRef={this.usrInputRef}
                  label="Usuário"
                  type="text"
                  name="login-usr"
                  autoComplete="off"
                  defaultValue={state.userName ?? ''}
                  fullWidth
                  disabled={!!state.loading}
                />

                {/* Password */}
                <TextField
                  inputRef={this.pwdInputRef}
                  label="Senha"
                  name="login-pwd"
                  autoComplete="off"
                  defaultValue={state.password ?? ''}
                  fullWidth
                  disabled={!!state.loading}
                  onKeyDown={this.onKeyDown}
                  slotProps={{
                    htmlInput: {
                      style: { WebkitTextSecurity: 'disc', textSecurity: 'disc' } as React.CSSProperties,
                    },
                  }}
                />

                {/* Error message */}
                {!!state.errorMessage && (
                  <Alert severity="error" sx={{ width: '100%', borderRadius: '8px' }}>
                    {state.errorMessage}
                  </Alert>
                )}

                {/* Login button */}
                <Button
                  type="button"
                  variant="contained"
                  fullWidth
                  onClick={this.emitOnEnter}
                  disabled={!!state.loading}
                  sx={{
                    height: 48,
                    borderRadius: '12px',
                    fontWeight: 'bold',
                    fontSize: 16,
                    textTransform: 'none',
                  }}
                >
                  {state.loading ? (
                    <CircularProgress size={24} sx={{ color: '#fff' }} />
                  ) : (
                    'Entrar'
                  )}
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
    app.setFormField(vsid, 'userName', userName)
    app.setFormField(vsid, 'password', await app.cipher(password))
    app.submit(vsid, ON_ENTER)
  }

  readonly onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      e.preventDefault()
      this.emitOnEnter()
    }
  }
}

export default BaseViewClass.FC(LoginViewClass, 'c677cda52d14')
