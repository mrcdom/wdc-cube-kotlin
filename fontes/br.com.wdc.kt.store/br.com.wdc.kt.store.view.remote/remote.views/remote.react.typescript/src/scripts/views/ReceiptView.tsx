import React from 'react'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Divider from '@mui/material/Divider'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import ReceiptIcon from '@mui/icons-material/Receipt'
import bridge, { BaseViewClass, type ViewProps } from 'wdc-cube-remote-bridge-react'
import * as NumberUtils from '@root/utils/NumberUtils'
import * as DateUtils from '@root/utils/DateUtils'
import { Colors, toPx } from '@root/theme'
import { StoreTokens } from '@root/tokens'
import { messages } from '@root/i18n'

// :: Actions

const ON_OPEN_PRODUCTS = 1

// :: Types

type ReceiptItem = {
  id?: number
  description: string
  value: number
  quantity: number
}

type ReceiptForm = {
  date: number
  items: ReceiptItem[]
  total: number
}

// :: View

export type ReceiptScope = {
  receipt: ReceiptForm
  notifySuccess?: boolean
}

class ReceiptViewClass extends BaseViewClass<ViewProps, ReceiptScope> {
  override render() {
    const { scope } = this
    const receipt = scope.receipt

    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: '16px 10px 16px 16px' }}>
        <Box sx={{ maxWidth: 600, width: '100%' }}>
          <Stack direction="column" spacing={2}>
            {/* Success banner */}
            {scope.notifySuccess && (
              <Paper
                elevation={0}
                sx={{
                  p: 2.5,
                  borderRadius: toPx(StoreTokens.radius.md),
                  bgcolor: Colors.SuccessContainer,
                  textAlign: 'center',
                }}
              >
                <Stack direction="column" spacing={1} sx={{ alignItems: 'center' }}>
                  <CheckCircleIcon sx={{ fontSize: 32, color: Colors.SuccessColor }} />
                  <Typography
                    variant="subtitle1"
                    sx={{ fontWeight: StoreTokens.fontWeight.medium, color: Colors.SuccessColor }}
                  >
                    {messages.text('receipt.purchaseCompleted')}
                  </Typography>
                </Stack>
              </Paper>
            )}

            {/* Receipt header */}
            <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                <ReceiptIcon sx={{ fontSize: 28 }} />
                <Typography
                  variant="h5"
                  sx={{ fontWeight: StoreTokens.fontWeight.medium, fontSize: StoreTokens.fontSize.display }}
                >
                  {messages.text('receipt.title')}
                </Typography>
              </Stack>
              {receipt.date && (
                <Chip label={DateUtils.formatDate(receipt.date)} size="small" sx={{ bgcolor: Colors.SurfaceVariant }} />
              )}
            </Stack>

            <Divider />

            {/* Items header */}
            <Stack direction="row" sx={{ justifyContent: 'space-between', px: 0.5 }}>
              <Typography variant="caption" sx={{ flex: '1 1 0', color: Colors.OnSurfaceVariant }}>
                {messages.text('receipt.columnItem')}
              </Typography>
              <Typography variant="caption" sx={{ width: 50, textAlign: 'center', color: Colors.OnSurfaceVariant }}>
                {messages.text('receipt.columnQty')}
              </Typography>
              <Typography variant="caption" sx={{ width: 100, textAlign: 'right', color: Colors.OnSurfaceVariant }}>
                {messages.text('receipt.columnAmount')}
              </Typography>
            </Stack>

            {/* Items */}
            <Stack direction="column" spacing={1}>
              {receipt.items.map((item, idx) => (
                <Paper
                  key={item.id ?? idx}
                  elevation={0}
                  sx={{ p: 1.5, borderRadius: toPx(StoreTokens.radius.sm), bgcolor: Colors.SurfaceVariant40 }}
                >
                  <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
                    <Typography variant="body2" sx={{ flex: '1 1 0', fontWeight: StoreTokens.fontWeight.medium }}>
                      {item.description}
                    </Typography>
                    <Typography variant="body2" sx={{ width: 50, textAlign: 'center', color: Colors.OnSurfaceVariant }}>
                      {messages.text('receipt.quantityTimes', { quantity: item.quantity })}
                    </Typography>
                    <Typography
                      variant="body2"
                      sx={{
                        width: 100,
                        textAlign: 'right',
                        color: Colors.PriceColor,
                        fontWeight: StoreTokens.fontWeight.medium,
                      }}
                    >
                      ${NumberUtils.format(item.value)}
                    </Typography>
                  </Stack>
                </Paper>
              ))}
            </Stack>

            {/* Total */}
            <Divider />
            <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end', alignItems: 'center' }}>
              <Typography variant="subtitle1" sx={{ color: Colors.OnSurfaceVariant }}>
                {messages.text('common.total')}
              </Typography>
              <Box sx={{ bgcolor: Colors.PriceBackground, borderRadius: '10px', px: 1.75, py: 0.75 }}>
                <Typography variant="h6" sx={{ fontWeight: 'bold', fontSize: 22, color: Colors.PriceColor }}>
                  ${NumberUtils.format(receipt.total)}
                </Typography>
              </Box>
            </Stack>

            {/* Action button */}
            <Stack direction="row" sx={{ justifyContent: 'flex-end' }}>
              <Button
                variant="contained"
                onClick={this.emitOpenProducts}
                sx={{
                  borderRadius: toPx(StoreTokens.radius.md),
                  height: 48,
                  textTransform: 'none',
                  fontWeight: StoreTokens.fontWeight.medium,
                }}
                startIcon={<ArrowBackIcon />}
              >
                {messages.text('common.continueShopping')}
              </Button>
            </Stack>
          </Stack>
        </Box>
      </Box>
    )
  }

  // :: Emissors

  readonly emitOpenProducts = () => {
    const { vsid } = this
    bridge.submit(vsid, ON_OPEN_PRODUCTS)
  }
}

export default BaseViewClass.FC(ReceiptViewClass, 'e8d0bd8ae3bc')
