import React from 'react'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Card from '@mui/material/Card'
import CardContent from '@mui/material/CardContent'
import Divider from '@mui/material/Divider'
import IconButton from '@mui/material/IconButton'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import AddIcon from '@mui/icons-material/Add'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import RemoveIcon from '@mui/icons-material/Remove'
import ShoppingCartIcon from '@mui/icons-material/ShoppingCart'
import app, { type ViewProps } from '@root/App'
import { BaseViewClass } from '@root/utils/ViewUtils'
import * as NumberUtils from '@root/utils/NumberUtils'
import { Colors } from '@root/theme'

// :: Actions

const ON_BUY = 1
const ON_OPEN_PRODUCTS = 3

// row actions, sent by each row on its own view
const ON_REMOVE = 1
const ON_MODIFY_QUANTITY = 2

// :: View

export type CartScope = {
  /** one view per row, each with its own scope */
  rowsViewIds?: string[]
  /** computed on the server */
  total?: number
  errorMessage?: string
}

class CartViewClass extends BaseViewClass<ViewProps, CartScope> {
  override render({ className }: ViewProps) {
    const { scope } = this
    const rowsViewIds = scope.rowsViewIds ?? []

    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: '16px 10px 16px 16px' }}>
        <Box sx={{ maxWidth: 700, width: '100%', display: 'flex', flexDirection: 'column' }}>
          {/* Title */}
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1.5 }}>
            <ShoppingCartIcon sx={{ fontSize: 28 }} />
            <Typography variant="h5" sx={{ fontWeight: 500, fontSize: 28 }}>
              Shopping Cart
            </Typography>
          </Stack>

          <Divider />

          {/* Error message */}
          {scope.errorMessage && (
            <Alert severity="error" sx={{ mt: 1.5, borderRadius: '8px' }}>
              {scope.errorMessage}
            </Alert>
          )}

          {rowsViewIds.length === 0 ? (
            /* Empty state */
            <Box
              sx={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                flex: '1 1 0',
                p: 6,
              }}
            >
              <ShoppingCartIcon sx={{ fontSize: 48, color: Colors.OnSurfaceVariant }} />
              <Typography variant="h6" sx={{ mt: 2, color: Colors.OnSurfaceVariant }}>
                Your cart is empty
              </Typography>
              <Typography variant="body2" sx={{ mt: 1, color: Colors.OnSurfaceVariant, opacity: 0.7 }}>
                Add products to start shopping
              </Typography>
            </Box>
          ) : (
            /* Cart items */
            <Box sx={{ mt: 1.5, mr: -0.75 }}>
              <Stack direction="column" spacing={1}>
                {rowsViewIds.map((rowViewId) => (
                  <React.Fragment key={rowViewId}>{app.createView(rowViewId)}</React.Fragment>
                ))}
              </Stack>
            </Box>
          )}

          {/* Total section */}
          {rowsViewIds.length > 0 && (
            <>
              <Divider sx={{ mt: 1, mb: 1 }} />
              <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end', alignItems: 'center' }}>
                <Typography variant="subtitle1" sx={{ color: Colors.OnSurfaceVariant }}>
                  Total:
                </Typography>
                <Box sx={{ bgcolor: Colors.PriceBackground, borderRadius: '10px', px: 1.75, py: 0.75 }}>
                  <Typography variant="h6" sx={{ fontWeight: 'bold', fontSize: 22, color: Colors.PriceColor }}>
                    ${NumberUtils.format(scope.total ?? 0)}
                  </Typography>
                </Box>
              </Stack>
            </>
          )}

          {/* Action buttons */}
          <Stack direction="row" spacing={2} sx={{ justifyContent: 'flex-end', mt: 2 }}>
            <Button
              variant="outlined"
              onClick={this.emitClickVoltar}
              sx={{ borderRadius: '12px', height: 48, textTransform: 'none' }}
              startIcon={<ArrowBackIcon />}
            >
              Continue Shopping
            </Button>
            {rowsViewIds.length > 0 && (
              <Button
                variant="contained"
                onClick={this.emitClickFinalizar}
                sx={{
                  borderRadius: '12px',
                  height: 48,
                  textTransform: 'none',
                  fontWeight: 'bold',
                  fontSize: 16,
                  bgcolor: Colors.PriceColor,
                }}
              >
                Buy
              </Button>
            )}
          </Stack>
        </Box>
      </Box>
    )
  }

  // :: Emissors

  readonly emitClickFinalizar = () => {
    const { vsid } = this
    app.submit(vsid, ON_BUY)
  }

  readonly emitClickVoltar = () => {
    const { vsid } = this
    app.submit(vsid, ON_OPEN_PRODUCTS)
  }
}

export default BaseViewClass.FC(CartViewClass, '7eb485e5f843')

// :: CartItemView: one row of the cart, a view of its own

export type CartItemScope = {
  productId: number
  name?: string
  price: number
  quantity: number
}

class CartItemViewClass extends BaseViewClass<ViewProps, CartItemScope> {
  override render(_props: ViewProps) {
    const { scope } = this

    return (
      <Card elevation={0} sx={{ borderRadius: '8px', bgcolor: Colors.SurfaceVariant40 }}>
        <CardContent sx={{ p: 2 }}>
          <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
            {/* Product info */}
            <Box sx={{ flex: '1 1 0' }}>
              <Typography variant="body2" noWrap sx={{ fontWeight: 500, fontSize: 14 }}>
                {scope.name}
              </Typography>
              <Typography variant="body2" sx={{ fontWeight: 500, color: Colors.PriceColor }}>
                ${NumberUtils.format(scope.price)}
              </Typography>
            </Box>

            {/* Quantity controls */}
            <Paper
              elevation={0}
              sx={{
                borderRadius: '10px',
                bgcolor: Colors.SurfaceVariant,
                display: 'flex',
                alignItems: 'center',
                px: 0.5,
              }}
            >
              <IconButton
                size="small"
                onClick={this.emitDecrement}
                sx={{ bgcolor: Colors.SecondaryContainer, width: 32, height: 32 }}
              >
                <RemoveIcon sx={{ fontSize: 16 }} />
              </IconButton>
              <Typography variant="subtitle1" sx={{ fontWeight: 'bold', px: 1.75 }}>
                {scope.quantity}
              </Typography>
              <IconButton
                size="small"
                onClick={this.emitIncrement}
                sx={{ bgcolor: Colors.SecondaryContainer, width: 32, height: 32 }}
              >
                <AddIcon sx={{ fontSize: 16 }} />
              </IconButton>
            </Paper>

            {/* Remove button */}
            <Button
              variant="text"
              color="error"
              onClick={this.emitRemove}
              sx={{ fontWeight: 'normal', fontSize: 12, textTransform: 'none' }}
            >
              Remove
            </Button>
          </Stack>
        </CardContent>
      </Card>
    )
  }

  readonly emitRemove = () => {
    app.submit(this.vsid, ON_REMOVE)
  }

  readonly emitIncrement = () => {
    const { vsid, scope } = this
    app.setFormField(vsid, 'p.quantity', scope.quantity + 1)
    app.submit(vsid, ON_MODIFY_QUANTITY)
  }

  readonly emitDecrement = () => {
    const { vsid, scope } = this
    if (scope.quantity > 1) {
      app.setFormField(vsid, 'p.quantity', scope.quantity - 1)
      app.submit(vsid, ON_MODIFY_QUANTITY)
    }
  }
}

export const CartItemView = BaseViewClass.FC(CartItemViewClass, '5d9e3c7a1f42')
