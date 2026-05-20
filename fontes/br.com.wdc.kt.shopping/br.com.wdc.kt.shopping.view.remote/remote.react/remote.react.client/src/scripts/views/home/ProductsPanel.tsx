import React, { ReactNode } from 'react'
import Box from '@mui/material/Box'
import Card from '@mui/material/Card'
import CardActionArea from '@mui/material/CardActionArea'
import CardContent from '@mui/material/CardContent'
import Chip from '@mui/material/Chip'
import CircularProgress from '@mui/material/CircularProgress'
import Divider from '@mui/material/Divider'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import app, { type ViewProps } from '@root/App'
import { BaseViewClass, BasePanelClass } from '@root/utils/ViewUtils'
import * as NumberUtils from '@root/utils/NumberUtils'
import * as EndpointUtils from '@root/utils/EndpointUtils'
import { Colors } from '@root/theme'

// :: Actions

const ON_OPEN_PRODUCT = 1

// :: Types

export type Product = {
  id: number
  name: string
  description?: string
  price: number
}

type ProductsPanelState = {
  products?: Product[]
}

// :: View

class ProductPanelClass extends BaseViewClass<ViewProps, ProductsPanelState> {
  override render({ className }: ViewProps): React.ReactNode {
    const { vsid, state } = this
    const products = state.products

    return (
      <Box className={className} sx={{ p: '12px 6px 12px 12px' }}>
        {/* Section header */}
        <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 1.5 }}>
          <Typography variant="h6" sx={{ fontWeight: 'bold' }}>
            Produtos
          </Typography>
          {products && (
            <Box sx={{ bgcolor: Colors.SecondaryContainer, borderRadius: '8px', px: 2, py: 0.75 }}>
              <Typography variant="caption" sx={{ color: Colors.OnPrimaryContainer }}>
                {products.length} itens
              </Typography>
            </Box>
          )}
        </Stack>

        <Divider sx={{ mb: 1.5 }} />

        {products == null ? (
          /* Loading */
          <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', p: 6 }}>
            <CircularProgress />
            <Typography variant="body2" sx={{ mt: 1.5, color: Colors.OnSurfaceVariant }}>
              Carregando produtos...
            </Typography>
          </Box>
        ) : products.length === 0 ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', p: 6 }}>
            <Typography variant="body1" sx={{ color: Colors.OnSurfaceVariant }}>
              Nenhum produto disponível
            </Typography>
          </Box>
        ) : (
          /* Product grid */
          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))',
              gap: 1.5,
            }}
          >
            {products.map((product) => (
              <CardProduto key={product.id} vsid={vsid} product={product} />
            ))}
          </Box>
        )}
      </Box>
    )
  }
}
export default BaseViewClass.FC(ProductPanelClass, 'a1b2c3d4e5f6')

// :: Internal - CardProduto

type CardProdutoProps = {
  vsid: string
  product: Product
}

class CardProdutoClass extends BasePanelClass<CardProdutoProps> {
  vsid!: string
  product!: Product

  override render({ vsid, product }: CardProdutoProps): React.ReactNode {
    this.vsid = vsid
    this.product = product

    return (
      <Card
        elevation={0}
        sx={{
          borderRadius: '8px',
          '&:hover': { boxShadow: '0 2px 8px rgba(0,0,0,0.12)' },
        }}
      >
        <CardActionArea onClick={this.emitOpenProduct}>
          {/* Product image */}
          <Box
            component="img"
            src={EndpointUtils.productImagePath(product.id)}
            alt={product.name}
            sx={{
              width: '100%',
              height: 140,
              objectFit: 'contain',
              bgcolor: Colors.SurfaceVariant,
            }}
          />
          <CardContent>
            <Typography variant="subtitle1" sx={{ fontWeight: 'bold' }}>
              {product.name}
            </Typography>
            <Chip
              label={`R$ ${NumberUtils.format(product.price)}`}
              size="small"
              sx={{
                mt: 1.5,
                bgcolor: Colors.PriceBackground,
                color: Colors.PriceColor,
                fontWeight: 'bold',
                borderRadius: '8px',
              }}
            />
          </CardContent>
        </CardActionArea>
      </Card>
    )
  }

  // :: Emissors

  emitOpenProduct = () => {
    const { vsid, product } = this
    app.setFormField(vsid, 'p.productId', product.id)
    app.submit(vsid, ON_OPEN_PRODUCT)
  }
}

const CardProduto = BasePanelClass.FC(CardProdutoClass)
