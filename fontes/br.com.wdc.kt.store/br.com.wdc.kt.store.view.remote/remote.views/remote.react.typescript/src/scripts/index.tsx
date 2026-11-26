import React from 'react'
import * as ReactDOM from 'react-dom/client'
import CssBaseline from '@mui/material/CssBaseline'
import { ThemeProvider, createTheme } from '@mui/material/styles'

import bridge from 'wdc-cube-remote-bridge-react'

import { Colors, FontFamily } from './theme'
import { StoreTokens } from './tokens'
import { messages } from './i18n'

import BrowserView from './views/BrowserView'
import RootView from './views/RootView'
import LoginView from './views/LoginView'
import RestrictedView from './views/HomeView'
import CartView, { CartItemView } from './views/CartView'
import ReceiptView from './views/ReceiptView'
import ProductView from './views/ProductView'
import ProductsPanel from './views/home/ProductsPanel'
import PurchasesPanel from './views/home/PurchasesPanel'

// the bridge's own alert (a page older than the host) in the user's language too
bridge.setOutdatedMessage(messages.text('browser.outdated'))

bridge.registerComponents(
  BrowserView,
  RootView,
  LoginView,
  RestrictedView,
  CartView,
  CartItemView,
  ReceiptView,
  ProductView,
  ProductsPanel,
  PurchasesPanel,
)

const theme = createTheme({
  palette: {
    primary: { main: Colors.Primary, contrastText: Colors.OnPrimary },
    secondary: { main: Colors.Secondary, contrastText: Colors.OnSecondary },
    error: { main: Colors.Error },
    background: { default: Colors.Background, paper: Colors.Surface },
  },
  typography: {
    fontFamily: FontFamily,
  },
  components: {
    MuiButton: {
      styleOverrides: {
        root: { textTransform: 'none', borderRadius: StoreTokens.radius.md },
      },
    },
    MuiCard: {
      styleOverrides: {
        root: { borderRadius: StoreTokens.radius.sm },
      },
    },
  },
})

const domContainer = document.querySelector('#root')
if (domContainer) {
  const root = ReactDOM.createRoot(domContainer)
  root.render(
    <ThemeProvider theme={theme}>
      <CssBaseline />
      {bridge.createBrowserView()}
    </ThemeProvider>,
  )
}
