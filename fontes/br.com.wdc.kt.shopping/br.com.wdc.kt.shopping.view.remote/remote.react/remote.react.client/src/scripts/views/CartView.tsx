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
import { BaseViewClass, BasePanelClass } from '@root/utils/ViewUtils'
import * as NumberUtils from '@root/utils/NumberUtils'
import { Colors } from '@root/theme'

// :: Actions

const ON_BUY = 1
const ON_REMOVE_PRODUCT = 2
const ON_OPEN_PRODUCTS = 3
const ON_MODIFY_QUANTITY = 4

// :: Types

type ItemCarrinho = {
  id: number
  name: string
  price: number
  quantity: number
}

// :: View

export type CartViewState = {
  items: ItemCarrinho[]
  errorMessage?: string
}

class CartViewClass extends BaseViewClass<ViewProps, CartViewState> {
  override render({ className }: ViewProps) {
    const { vsid, state } = this
    const items = state.items ?? []
    let valorTotal = 0
    items.forEach((prod) => {
      valorTotal += prod.price * prod.quantity
    })

    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: '16px 10px 16px 16px' }}>
        <Box sx={{ maxWidth: 700, width: '100%', display: 'flex', flexDirection: 'column' }}>
          {/* Title */}
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1.5 }}>
            <ShoppingCartIcon sx={{ fontSize: 28 }} />
            <Typography variant="h5" sx={{ fontWeight: 500, fontSize: 28 }}>
              Carrinho de Compras
            </Typography>
          </Stack>

          <Divider />

          {/* Error message */}
          {state.errorMessage && (
            <Alert severity="error" sx={{ mt: 1.5, borderRadius: '8px' }}>
              {state.errorMessage}
            </Alert>
          )}

          {items.length === 0 ? (
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
                Seu carrinho está vazio
              </Typography>
              <Typography variant="body2" sx={{ mt: 1, color: Colors.OnSurfaceVariant, opacity: 0.7 }}>
                Adicione produtos para começar suas compras
              </Typography>
            </Box>
          ) : (
            /* Cart items */
            <Box sx={{ mt: 1.5, mr: -0.75 }}>
              <Stack direction="column" spacing={1}>
                {items.map((item) => (
                  <CartItemCard key={item.id} vsid={vsid} item={item} />
                ))}
              </Stack>
            </Box>
          )}

          {/* Total section */}
          {items.length > 0 && (
            <>
              <Divider sx={{ mt: 1, mb: 1 }} />
              <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end', alignItems: 'center' }}>
                <Typography variant="subtitle1" sx={{ color: Colors.OnSurfaceVariant }}>
                  Total:
                </Typography>
                <Box sx={{ bgcolor: Colors.PriceBackground, borderRadius: '10px', px: 1.75, py: 0.75 }}>
                  <Typography variant="h6" sx={{ fontWeight: 'bold', fontSize: 22, color: Colors.PriceColor }}>
                    R$ {NumberUtils.format(valorTotal)}
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
              Continuar Comprando
            </Button>
            {items.length > 0 && (
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
                Comprar
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

// :: CartItemCard Component

type CartItemCardProps = {
  vsid: string
  item: ItemCarrinho
}

class CartItemCardClass extends BasePanelClass<CartItemCardProps> {
  vsid!: string
  item!: ItemCarrinho

  override render({ vsid, item }: CartItemCardProps) {
    this.vsid = vsid
    this.item = item

    return (
      <Card elevation={0} sx={{ borderRadius: '8px', bgcolor: Colors.SurfaceVariant40 }}>
        <CardContent sx={{ p: 2 }}>
          <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
            {/* Product info */}
            <Box sx={{ flex: '1 1 0' }}>
              <Typography variant="body2" noWrap sx={{ fontWeight: 500, fontSize: 14 }}>
                {item.name}
              </Typography>
              <Typography variant="body2" sx={{ fontWeight: 500, color: Colors.PriceColor }}>
                R$ {NumberUtils.format(item.price)}
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
                {item.quantity}
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
              onClick={this.emitRemoveProduct}
              sx={{ fontWeight: 'normal', fontSize: 12, textTransform: 'none' }}
            >
              Remover
            </Button>
          </Stack>
        </CardContent>
      </Card>
    )
  }

  readonly emitRemoveProduct = () => {
    const { vsid, item } = this
    app.setFormField(vsid, 'p.productId', item.id)
    app.submit(vsid, ON_REMOVE_PRODUCT)
  }

  readonly emitIncrement = () => {
    const { vsid, item } = this
    app.setFormField(vsid, 'p.productId', item.id)
    app.setFormField(vsid, 'p.quantity', item.quantity + 1)
    app.submit(vsid, ON_MODIFY_QUANTITY)
  }

  readonly emitDecrement = () => {
    const { vsid, item } = this
    if (item.quantity > 1) {
      app.setFormField(vsid, 'p.productId', item.id)
      app.setFormField(vsid, 'p.quantity', item.quantity - 1)
      app.submit(vsid, ON_MODIFY_QUANTITY)
    }
  }
}

const CartItemCard = BasePanelClass.FC(CartItemCardClass)
