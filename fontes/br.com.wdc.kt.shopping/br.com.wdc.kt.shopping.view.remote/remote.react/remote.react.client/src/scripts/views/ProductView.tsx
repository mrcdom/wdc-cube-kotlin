import React from 'react'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import Divider from '@mui/material/Divider'
import IconButton from '@mui/material/IconButton'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import AddIcon from '@mui/icons-material/Add'
import AddShoppingCartIcon from '@mui/icons-material/AddShoppingCart'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import RemoveIcon from '@mui/icons-material/Remove'
import app, { type ViewProps } from '@root/App'
import { BaseViewClass } from '@root/utils/ViewUtils'
import * as NumberUtils from '@root/utils/NumberUtils'
import * as EndpointUtils from '@root/utils/EndpointUtils'
import { Colors } from '@root/theme'

// :: Actions

const ON_OPEN_PRODUCTS = 1
const ON_ADD_TO_CART = 2

// :: Types

type Product = {
  id: number
  name: string
  description: string
  price: number
}

const DefaultProduct: Product = {
  id: -1,
  name: '',
  description: '',
  price: 0,
}

// :: View

export type ProductViewState = {
  product: Product
  quantity: number
  errorMessage?: string
}

function stripHtml(html: string): string {
  return html
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<li>/gi, '\n• ')
    .replace(/<[^>]+>/g, '')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&nbsp;/g, ' ')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

class ProductViewClass extends BaseViewClass<ViewProps, ProductViewState> {
  override render({ className }: ViewProps) {
    const { state } = this
    const quantity = (state.quantity = state.quantity ?? 1)
    const product = state.product ?? DefaultProduct

    if (product.id === -1) {
      return (
        <Stack direction="column" spacing={2} sx={{ alignItems: 'center', p: 6 }}>
          <CircularProgress />
          <Typography variant="body2" sx={{ color: Colors.OnSurfaceVariant }}>
            Carregando produto...
          </Typography>
        </Stack>
      )
    }

    const cleanDesc = product.description ? stripHtml(product.description) : ''

    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: '16px 10px 16px 16px' }}>
        <Box sx={{ maxWidth: 600, width: '100%' }}>
          <Stack direction="column" spacing={2}>
            {/* Error message */}
            {state.errorMessage && (
              <Alert severity="error" sx={{ borderRadius: '8px' }}>
                {state.errorMessage}
              </Alert>
            )}

            {/* Product name */}
            <Typography variant="h4" sx={{ fontWeight: 'bold' }}>
              {product.name}
            </Typography>

            <Divider />

            {/* Description */}
            {cleanDesc && (
              <Paper
                elevation={0}
                sx={{ p: 2, borderRadius: '8px', bgcolor: Colors.SurfaceVariant50 }}
              >
                <Typography
                  variant="body1"
                  sx={{ color: Colors.OnSurfaceVariant, lineHeight: '24px', whiteSpace: 'pre-wrap' }}
                >
                  {cleanDesc}
                </Typography>
              </Paper>
            )}

            {/* Price + quantity + image row */}
            <Stack direction="row" spacing={3} sx={{ alignItems: 'center' }}>
              {/* Left: price + quantity */}
              <Stack direction="column" spacing={2} sx={{ flex: '1 1 0' }}>
                {/* Price */}
                <Box
                  sx={{
                    bgcolor: Colors.PriceBackground,
                    borderRadius: '12px',
                    py: 1.5,
                    textAlign: 'center',
                  }}
                >
                  <Typography variant="body1" sx={{ color: Colors.PriceColor, fontWeight: 500, fontSize: 16 }}>
                    R$ {NumberUtils.format(product.price)}
                  </Typography>
                </Box>

                {/* Quantity selector */}
                <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                  <Typography variant="body1" sx={{ fontWeight: 'bold' }}>
                    Qtd:
                  </Typography>
                  <Paper
                    elevation={0}
                    sx={{
                      borderRadius: '12px',
                      bgcolor: Colors.SurfaceVariant,
                      display: 'flex',
                      alignItems: 'center',
                      px: 0.5,
                    }}
                  >
                    <IconButton size="small" disabled={quantity <= 1} onClick={this.emitDecrement}>
                      <RemoveIcon />
                    </IconButton>
                    <Typography variant="h6" sx={{ fontWeight: 'bold', px: 2.5 }}>
                      {quantity}
                    </Typography>
                    <IconButton size="small" onClick={this.emitIncrement}>
                      <AddIcon />
                    </IconButton>
                  </Paper>
                </Stack>
              </Stack>

              {/* Product image */}
              <Box
                sx={{
                  width: 120,
                  height: 120,
                  borderRadius: '12px',
                  overflow: 'hidden',
                  bgcolor: Colors.SurfaceVariant,
                }}
              >
                <Box
                  component="img"
                  src={EndpointUtils.productImagePath(product.id)}
                  alt={product.name}
                  sx={{ width: 120, height: 120, objectFit: 'contain' }}
                />
              </Box>
            </Stack>

            {/* Action buttons */}
            <Stack direction="row" spacing={2} sx={{ justifyContent: 'center' }}>
              <Button
                variant="outlined"
                onClick={this.emitGoHome}
                sx={{ borderRadius: '12px', height: 48, textTransform: 'none' }}
                startIcon={<ArrowBackIcon />}
              >
                Voltar
              </Button>
              <Button
                variant="contained"
                onClick={this.emitAddToCart}
                sx={{ borderRadius: '12px', height: 48, textTransform: 'none' }}
                startIcon={<AddShoppingCartIcon />}
              >
                Adicionar ao Carrinho
              </Button>
            </Stack>
          </Stack>
        </Box>
      </Box>
    )
  }

  // :: Emissors

  readonly emitDismissError = () => {
    this.state.errorMessage = undefined
    this.forceUpdate()
  }

  readonly emitAddToCart = () => {
    const { vsid, state } = this
    app.setFormField(vsid, 'p.quantity', state.quantity)
    app.submit(vsid, ON_ADD_TO_CART)
  }

  readonly emitGoHome = () => {
    const { vsid } = this
    app.submit(vsid, ON_OPEN_PRODUCTS)
  }

  readonly emitIncrement = () => {
    const { vsid, state } = this
    state.quantity = (state.quantity ?? 1) + 1
    app.setFormField(vsid, 'quantity', state.quantity)
    this.forceUpdate()
  }

  readonly emitDecrement = () => {
    const { vsid, state } = this
    const cur = state.quantity ?? 1
    if (cur > 1) {
      state.quantity = cur - 1
      app.setFormField(vsid, 'quantity', state.quantity)
      this.forceUpdate()
    }
  }
}

export default BaseViewClass.FC(ProductViewClass, '48b693f67410')
