import React from 'react'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Card from '@mui/material/Card'
import Tab from '@mui/material/Tab'
import Tabs from '@mui/material/Tabs'
import ShoppingBagIcon from '@mui/icons-material/ShoppingBag'
import Inventory2Icon from '@mui/icons-material/Inventory2'
import app, { type ViewProps } from '@root/App'
import { BaseViewClass } from '@root/utils/ViewUtils'
import { Colors, COMPACT_BREAKPOINT } from '@root/theme'
import HeaderPanel from './home/HeaderPanel'

// :: View

export type HomeViewState = {
  nickName: string
  cartItemCount: number
  productsPanelViewId?: string
  purchasesPanelViewId?: string
  contentViewId?: string
  errorMessage: string
}

class HomeViewClass extends BaseViewClass<ViewProps, HomeViewState> {
  private selectedTab = 0
  private isCompact = window.innerWidth < COMPACT_BREAKPOINT
  private resizeHandler: (() => void) | null = null

  override render({ vsid }: ViewProps, initial?: boolean) {
    const { state } = this

    if (initial && !this.resizeHandler) {
      this.resizeHandler = () => {
        const compact = window.innerWidth < COMPACT_BREAKPOINT
        if (compact !== this.isCompact) {
          this.isCompact = compact
          this.forceUpdate()
        }
      }
      window.addEventListener('resize', this.resizeHandler)
    }

    return (
      <Box sx={{ display: 'flex', flexDirection: 'column', height: '100vh', overflow: 'hidden' }}>
        <HeaderPanel
          vsid={vsid}
          nickName={state.nickName}
          cartItemCount={state.cartItemCount}
          isCompact={this.isCompact}
        />

        {/* Error */}
        {state.errorMessage && (
          <Alert severity="error" sx={{ mx: 2, mt: 1 }}>
            {state.errorMessage}
          </Alert>
        )}

        {/* Content area */}
        {state.contentViewId ? (
          <Box sx={{ flex: '1 1 0', overflowY: 'auto', overflowX: 'hidden' }}>
            <Box sx={{ maxWidth: 560, mx: 'auto' }}>
              {app.createView(state.contentViewId)}
            </Box>
          </Box>
        ) : this.isCompact ? (
          /* COMPACT: Tabs for Products / Purchases */
          <Box sx={{ flex: '1 1 0', p: 1, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
            <Tabs
              value={this.selectedTab}
              onChange={this.onTabChange}
              sx={{ mb: 0.5, minHeight: 36 }}
            >
              <Tab
                label="Produtos"
                icon={<ShoppingBagIcon />}
                iconPosition="start"
                sx={{ minHeight: 36, py: 0.5, fontSize: 13 }}
              />
              <Tab
                label="Compras"
                icon={<Inventory2Icon />}
                iconPosition="start"
                sx={{ minHeight: 36, py: 0.5, fontSize: 13 }}
              />
            </Tabs>
            <Card
              elevation={0}
              sx={{
                borderRadius: '8px',
                flex: '1 1 0',
                overflow: this.selectedTab === 0 ? 'auto' : 'hidden',
              }}
            >
              {this.selectedTab === 0
                ? app.createView(state.productsPanelViewId)
                : app.createView(state.purchasesPanelViewId)}
            </Card>
          </Box>
        ) : (
          /* DESKTOP: Side-by-side layout */
          <Box
            sx={{
              display: 'flex',
              flexDirection: 'row',
              flex: '1 1 0',
              p: 2,
              gap: 2,
              overflow: 'hidden',
              maxWidth: 1200,
              mx: 'auto',
              width: '100%',
            }}
          >
            {/* Products panel (flex: 3) */}
            <Card
              elevation={0}
              sx={{ flex: '3 1 0', borderRadius: '8px', overflowY: 'auto', overflowX: 'hidden' }}
            >
              {app.createView(state.productsPanelViewId)}
            </Card>

            {/* Purchases panel (flex: 2) */}
            <Card
              elevation={0}
              sx={{ flex: '2 1 0', borderRadius: '8px', overflow: 'hidden' }}
            >
              {app.createView(state.purchasesPanelViewId)}
            </Card>
          </Box>
        )}
      </Box>
    )
  }

  readonly onTabChange = (_: unknown, newValue: number) => {
    this.selectedTab = newValue
    this.forceUpdate()
  }
}

export default BaseViewClass.FC(HomeViewClass, '473dbdd7a36a')
