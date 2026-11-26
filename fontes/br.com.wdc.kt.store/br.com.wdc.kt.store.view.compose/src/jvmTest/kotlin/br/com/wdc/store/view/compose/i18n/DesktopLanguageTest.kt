package br.com.wdc.store.view.compose.i18n

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import br.com.wdc.framework.commons.storage.SessionStorage
import br.com.wdc.store.i18n.MessageKeys
import br.com.wdc.store.presentation.i18n.Message
import br.com.wdc.store.presentation.presenter.RootPresenter
import br.com.wdc.store.presentation.presenter.open.login.LoginScope
import br.com.wdc.store.presentation.presenter.restricted.cart.CartScope
import br.com.wdc.store.view.compose.bridge.ViewUpdateScheduler
import br.com.wdc.store.view.compose.theme.StoreTheme
import br.com.wdc.store.view.compose.views.CartView
import br.com.wdc.store.view.compose.views.LoginView
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * On the desktop, the UI texts follow the JVM's default locale (`Locale.current` reads it): the views are drawn the
 * way the desktop app draws them — inside [StoreTheme] — under pt-BR and en-US, without a window.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopLanguageTest {

    private class TestApp : RootPresenter() {
        override fun createSessionStorage(): SessionStorage = error("not used")
    }

    private val savedLocale: Locale = Locale.getDefault()

    init {
        TestApp().also { app -> ViewUpdateScheduler.initialize { app } }
    }

    @AfterTest
    fun restoreLocale() = Locale.setDefault(savedLocale)

    private fun loginWithError() = LoginScope().also {
        it.errorMessage = Message(MessageKeys.ErrorTooManySignInsSeconds, mapOf("seconds" to "30"))
    }

    private fun emptyCartWithError() = CartScope().also {
        it.errorMessage = Message(MessageKeys.ErrorEmptyCartPurchase)
    }

    @Test
    fun brazilianPortuguese_login() = runComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("pt-BR"))
        setContent { StoreTheme { LoginView(loginWithError()) } }
        onNodeWithText("Entre para continuar").assertExists()
        onNodeWithText("Usuário").assertExists()
        onNodeWithText("Senha").assertExists()
        onNodeWithText("Entrar").assertExists()
        // a presenter's message, with its argument, in the view's language
        onNodeWithText("Muitas tentativas de entrada sem sucesso. Tente de novo em 30 segundos.").assertExists()
    }

    @Test
    fun brazilianPortuguese_cart() = runComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("pt-BR"))
        setContent { StoreTheme { CartView(emptyCartWithError()) } }
        onNodeWithText("Carrinho de compras").assertExists()
        onNodeWithText("Seu carrinho está vazio").assertExists()
        onNodeWithText("É preciso ter pelo menos um item no carrinho para concluir a compra.").assertExists()
    }

    @Test
    fun usEnglish_login() = runComposeUiTest {
        Locale.setDefault(Locale.US)
        setContent { StoreTheme { LoginView(loginWithError()) } }
        onNodeWithText("Sign in to continue").assertExists()
        onNodeWithText("Username").assertExists()
        onNodeWithText("Too many failed sign-ins. Try again in 30 seconds.").assertExists()
    }

    @Test
    fun aLanguageWithoutCatalog_fallsBackToUsEnglish() = runComposeUiTest {
        Locale.setDefault(Locale.GERMANY)
        setContent { StoreTheme { CartView(emptyCartWithError()) } }
        onNodeWithText("Shopping Cart").assertExists()
        onNodeWithText("Your cart is empty").assertExists()
    }
}
