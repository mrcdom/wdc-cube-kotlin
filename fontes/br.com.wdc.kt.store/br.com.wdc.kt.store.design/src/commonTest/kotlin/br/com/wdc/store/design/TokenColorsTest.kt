package br.com.wdc.store.design

import kotlin.test.Test
import kotlin.test.assertEquals

class TokenColorsTest {

    @Test
    fun anOpaqueColor_isHexInCss() {
        assertEquals("#1B5E7B", TokenColors.css(StoreTokens.Color.Primary))
        assertEquals(0x1B, TokenColors.red(StoreTokens.Color.Primary))
        assertEquals(255, TokenColors.alpha(StoreTokens.Color.Primary))
    }

    @Test
    fun aTranslucentColor_isRgbaInCss() {
        assertEquals("rgba(231, 235, 240, 0.4)", TokenColors.css(StoreTokens.Color.SurfaceVariant40))
        assertEquals("rgba(255, 255, 255, 0.85)", TokenColors.css(StoreTokens.Color.WhiteOverlay85))
        assertEquals(0x1A, TokenColors.alpha(StoreTokens.Color.PriceBackground))
    }

    @Test
    fun theFontFamily_isCss() {
        assertEquals("'Roboto', 'Helvetica', 'Arial', sans-serif", TokenColors.cssFamily(StoreTokens.FontFamily.Base))
    }
}
