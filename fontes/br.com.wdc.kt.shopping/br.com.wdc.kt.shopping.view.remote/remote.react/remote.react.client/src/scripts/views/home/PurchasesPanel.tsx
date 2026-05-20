import React from 'react'
import Box from '@mui/material/Box'
import Card from '@mui/material/Card'
import CardActionArea from '@mui/material/CardActionArea'
import CardContent from '@mui/material/CardContent'
import Chip from '@mui/material/Chip'
import Divider from '@mui/material/Divider'
import IconButton from '@mui/material/IconButton'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import Inventory2Icon from '@mui/icons-material/Inventory2'
import app, { type ViewProps } from '@root/App'
import { BaseViewClass, BasePanelClass } from '@root/utils/ViewUtils'
import * as NumberUtils from '@root/utils/NumberUtils'
import * as DateUtils from '@root/utils/DateUtils'
import { Colors } from '@root/theme'

const ITEM_HEIGHT_PX = 76

// :: Actions

const ON_OPEN_RECEIPT = 1
const ON_PAGE_CHANGE = 2
const ON_PAGE_SIZE_CHANGE = 3

// :: Types

export type Purchase = {
  id: number
  date: number
  total: number
  items: string[]
}

type PurchasesPanelState = {
  purchases?: Purchase[]
  page: number
  pageSize: number
  totalCount: number
}

// :: View

class PurchasesPanelClass extends BaseViewClass<ViewProps, PurchasesPanelState> {
  totalPages!: number
  private resizeHandler: (() => void) | null = null
  private resizeTimer: number | null = null
  private listRef = React.createRef<HTMLDivElement>()

  private computePageSize = () => {
    const el = this.listRef.current
    if (!el) return
    const containerHeight = el.clientHeight
    if (containerHeight <= 0) return
    let itemHeight = ITEM_HEIGHT_PX
    const firstItem = el.querySelector('[data-purchase-item]') as HTMLElement | null
    if (firstItem && firstItem.offsetHeight > 0) {
      const style = getComputedStyle(firstItem)
      itemHeight = firstItem.offsetHeight + parseFloat(style.marginTop) + parseFloat(style.marginBottom)
    }
    const capacity = Math.max(1, Math.floor(containerHeight / itemHeight))
    const { vsid } = this
    app.setFormField(vsid, 'p.capacity', capacity)
    app.submitSilent(vsid, ON_PAGE_SIZE_CHANGE)
  }

  private onResize = () => {
    if (this.resizeTimer != null) clearTimeout(this.resizeTimer)
    this.resizeTimer = window.setTimeout(this.computePageSize, 150)
  }

  override render({ className }: ViewProps, initial?: boolean): React.ReactNode {
    const { state } = this
    const pageSize = Math.max(1, state.pageSize)
    this.totalPages = Math.max(1, Math.ceil(state.totalCount / pageSize))

    if (initial && !this.resizeHandler) {
      this.resizeHandler = this.onResize
      requestAnimationFrame(this.computePageSize)
      window.addEventListener('resize', this.onResize)
    }

    return (
      <Box
        className={className}
        sx={{ p: 1.5, display: 'flex', flexDirection: 'column', height: '100%' }}
      >
        {/* Section header */}
        <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 1.5 }}>
          <Typography variant="h6" sx={{ fontWeight: 'bold' }}>
            Compras
          </Typography>
          {state.totalCount > 0 && (
            <Box sx={{ bgcolor: Colors.SecondaryContainer, borderRadius: '8px', px: 2, py: 0.75 }}>
              <Typography variant="caption" sx={{ color: Colors.OnPrimaryContainer }}>
                {state.totalCount} itens
              </Typography>
            </Box>
          )}
        </Stack>

        <Divider sx={{ mb: 1.5 }} />

        {/* Purchase list */}
        <Box ref={this.listRef} sx={{ flex: '1 1 auto', overflow: 'hidden' }}>
          {(state.purchases ?? []).length === 0 ? (
            state.totalCount === 0 && (
              <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', p: 6 }}>
                <Inventory2Icon sx={{ fontSize: 48, color: Colors.OnSurfaceVariant }} />
                <Typography variant="body1" sx={{ mt: 1, color: Colors.OnSurfaceVariant }}>
                  Nenhuma compra realizada
                </Typography>
              </Box>
            )
          ) : (
            <Stack direction="column" spacing={1}>
              {(state.purchases ?? []).map((purchase) => (
                <PurchaseItemRow key={purchase.id} vsid={this.vsid} purchase={purchase} />
              ))}
            </Stack>
          )}
        </Box>

        {/* Pagination */}
        {state.totalCount > state.pageSize && state.pageSize > 0 && (
          <Stack direction="row" spacing={2} sx={{ justifyContent: 'center', alignItems: 'center', mt: 1 }}>
            <IconButton disabled={state.page <= 0} onClick={this.emitPreviousPage}>
              <ChevronLeftIcon />
            </IconButton>
            <Typography variant="body2" sx={{ fontWeight: 'bold' }}>
              {state.page + 1} / {this.totalPages}
            </Typography>
            <IconButton disabled={state.page >= this.totalPages - 1} onClick={this.emitNextPage}>
              <ChevronRightIcon />
            </IconButton>
          </Stack>
        )}
      </Box>
    )
  }

  // :: Emissors

  readonly emitPageChange = (page: number) => {
    const { vsid } = this
    app.setFormField(vsid, 'p.page', page)
    app.submit(vsid, ON_PAGE_CHANGE)
  }

  readonly emitNextPage = () => {
    const { state } = this
    this.emitPageChange(state.page + 1)
  }

  readonly emitPreviousPage = () => {
    const { state } = this
    this.emitPageChange(state.page - 1)
  }
}

export default BaseViewClass.FC(PurchasesPanelClass, 'b3c4d5e6f7a8')

// :: Internal - PurchaseItemRow

type PurchaseItemRowProps = {
  vsid: string
  purchase: Purchase
}

class PurchaseItemRowClass extends BasePanelClass<PurchaseItemRowProps> {
  vsid!: string
  purchase!: Purchase

  override render({ vsid, purchase }: PurchaseItemRowProps): React.ReactNode {
    this.vsid = vsid
    this.purchase = purchase

    return (
      <Card
        data-purchase-item
        elevation={0}
        sx={{ borderRadius: '8px', bgcolor: Colors.SurfaceVariant50 }}
      >
        <CardActionArea onClick={this.emitOpenReceipt}>
          <CardContent sx={{ p: '14px !important' }}>
            <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
              <Box sx={{ flex: '1 1 0', minWidth: 0, overflow: 'hidden' }}>
                <Typography variant="caption" sx={{ color: Colors.OnSurfaceVariant }}>
                  {DateUtils.formatDate(purchase.date)}
                </Typography>
                <Typography
                  variant="body2"
                  noWrap
                  sx={{ fontWeight: 'normal' }}
                >
                  {purchase.items.join(', ')}
                </Typography>
              </Box>
              <Chip
                label={`R$ ${NumberUtils.format(purchase.total)}`}
                size="small"
                sx={{
                  bgcolor: Colors.PriceBackground,
                  color: Colors.PriceColor,
                  fontWeight: 'bold',
                  borderRadius: '8px',
                  ml: 1,
                }}
              />
            </Stack>
          </CardContent>
        </CardActionArea>
      </Card>
    )
  }

  readonly emitOpenReceipt = () => {
    const { vsid, purchase } = this
    app.setFormField(vsid, 'p.purchaseId', purchase.id)
    app.submit(vsid, ON_OPEN_RECEIPT)
  }
}

const PurchaseItemRow = BasePanelClass.FC(PurchaseItemRowClass)
