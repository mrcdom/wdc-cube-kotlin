import React from 'react'
import * as ReactDOM from 'react-dom/client'
import CssBaseline from '@mui/material/CssBaseline'
import { ThemeProvider, createTheme } from '@mui/material/styles'

import bridge from '@wdc/remote-bridge-react'

import BrowserView from './views/BrowserView'
import RootView from './views/RootView'
import LoginView from './views/LoginView'
import RestrictedView from './views/HomeView'
import CartView, { CartItemView } from './views/CartView'
import ReceiptView from './views/ReceiptView'
import ProductView from './views/ProductView'
import ProductsPanel from './views/home/ProductsPanel'
import PurchasesPanel from './views/home/PurchasesPanel'

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
    primary: { main: '#1B5E7B', contrastText: '#FFFFFF' },
    secondary: { main: '#4A6572', contrastText: '#FFFFFF' },
    error: { main: '#BA1A1A' },
    background: { default: '#F5F7FA', paper: '#FFFFFF' },
  },
  typography: {
    fontFamily: "'Roboto', 'Helvetica', 'Arial', sans-serif",
  },
  components: {
    MuiButton: {
      styleOverrides: {
        root: { textTransform: 'none', borderRadius: 12 },
      },
    },
    MuiCard: {
      styleOverrides: {
        root: { borderRadius: 8 },
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
