import React from 'react'
import AppBar from '@mui/material/AppBar'
import Avatar from '@mui/material/Avatar'
import Badge from '@mui/material/Badge'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import IconButton from '@mui/material/IconButton'
import Toolbar from '@mui/material/Toolbar'
import Typography from '@mui/material/Typography'
import LocalMallIcon from '@mui/icons-material/LocalMall'
import LogoutIcon from '@mui/icons-material/Logout'
import ShoppingCartIcon from '@mui/icons-material/ShoppingCart'
import app from '@root/App'
import { BasePanelClass } from '@root/utils/ViewUtils'
import { Colors } from '@root/theme'

// :: Actions

const ON_EXIT = 1
const ON_OPEN_CART = 2

// :: Panel

type HeaderPanelProps = {
  vsid: string
  nickName: string
  cartItemCount: number
  isCompact?: boolean
}

class HeaderPanelClass extends BasePanelClass<HeaderPanelProps> {
  vsid!: string

  override render({ vsid, nickName, cartItemCount, isCompact }: HeaderPanelProps) {
    this.vsid = vsid

    if (isCompact) {
      return (
        <AppBar position="static" sx={{ bgcolor: Colors.Primary }}>
          <Box sx={{ p: '10px 12px' }}>
            {/* Top row: logo + "Shopping" + logout */}
            <Box sx={{ display: 'flex', alignItems: 'center', width: '100%' }}>
              <Avatar
                sx={{
                  width: 32,
                  height: 32,
                  bgcolor: Colors.WhiteOverlay20,
                  borderRadius: '8px',
                  mr: 1,
                }}
              >
                <LocalMallIcon sx={{ fontSize: 20, color: '#fff' }} />
              </Avatar>
              <Box sx={{ position: 'relative' }}>
                <Typography variant="h6" sx={{ fontWeight: 'bold', color: '#fff' }}>
                  Shopping
                </Typography>
                <Typography variant="caption" sx={{ position: 'absolute', bottom: -10, right: 0, color: '#fff', opacity: 0.45, fontSize: 9 }}>
                  remote.react
                </Typography>
              </Box>
              <IconButton
                onClick={this.emitExit}
                sx={{ width: 28, height: 28, ml: 2, bgcolor: Colors.WhiteOverlay10 }}
              >
                <LogoutIcon sx={{ fontSize: 16, color: '#fff', opacity: 0.7 }} />
              </IconButton>
            </Box>

            {/* Spacer */}
            <Box sx={{ height: 8 }} />

            {/* Bottom row: greeting + cart */}
            <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', width: '100%' }}>
              <Typography variant="body2" sx={{ color: Colors.WhiteOverlay85 }}>
                Olá, {nickName}
              </Typography>
              <Badge badgeContent={cartItemCount} color="error" invisible={cartItemCount <= 0}>
                <Button
                  variant="contained"
                  onClick={this.emitOpenCart}
                  sx={{
                    bgcolor: Colors.WhiteOverlay20,
                    borderRadius: '10px',
                    textTransform: 'none',
                    py: 0.5,
                    px: 1.5,
                    fontSize: 13,
                  }}
                >
                  <ShoppingCartIcon sx={{ mr: 0.75, fontSize: 16 }} />
                  Carrinho
                </Button>
              </Badge>
            </Box>
          </Box>
        </AppBar>
      )
    }

    // Desktop header: single row
    return (
      <AppBar position="static" sx={{ bgcolor: Colors.Primary }}>
        <Toolbar sx={{ p: '12px 24px !important', minHeight: 'auto !important' }}>
          {/* Logo */}
          <Avatar
            sx={{
              width: 36,
              height: 36,
              bgcolor: Colors.WhiteOverlay20,
              borderRadius: '10px',
              mr: '10px',
            }}
          >
            <LocalMallIcon sx={{ fontSize: 22, color: '#fff' }} />
          </Avatar>

          <Box sx={{ position: 'relative' }}>
            <Typography variant="h6" sx={{ fontWeight: 'bold', color: '#fff' }}>
              Shopping
            </Typography>
            <Typography variant="caption" sx={{ position: 'absolute', bottom: -10, right: 0, color: '#fff', opacity: 0.45, fontSize: 10 }}>
              remote.react
            </Typography>
          </Box>

          {/* Logout icon */}
          <IconButton
            onClick={this.emitExit}
            sx={{ width: 32, height: 32, ml: '10px', bgcolor: Colors.WhiteOverlay10 }}
          >
            <LogoutIcon sx={{ fontSize: 18, color: '#fff', opacity: 0.7 }} />
          </IconButton>

          {/* Spacer */}
          <Box sx={{ flexGrow: 1 }} />

          {/* Greeting pill */}
          <Box
            sx={{
              bgcolor: Colors.WhiteOverlay15,
              borderRadius: '20px',
              px: '12px',
              py: '4px',
              mr: 2,
            }}
          >
            <Typography variant="body2" sx={{ color: '#fff' }}>
              Olá, {nickName}
            </Typography>
          </Box>

          {/* Cart button */}
          <Badge badgeContent={cartItemCount} color="error" invisible={cartItemCount <= 0}>
            <Button
              variant="contained"
              disableElevation
              onClick={this.emitOpenCart}
              sx={{
                bgcolor: Colors.WhiteOverlay20,
                borderRadius: '10px',
                textTransform: 'none',
                py: '5px',
                px: '12px',
              }}
            >
              <ShoppingCartIcon sx={{ mr: 0.75, fontSize: 18 }} />
              Carrinho
            </Button>
          </Badge>
        </Toolbar>
      </AppBar>
    )
  }

  // :: Emissors

  readonly emitOpenCart = () => {
    const { vsid } = this
    app.submit(vsid, ON_OPEN_CART)
  }

  readonly emitExit = () => {
    const { vsid } = this
    app.submit(vsid, ON_EXIT)
  }
}

export default BasePanelClass.FC(HeaderPanelClass)
