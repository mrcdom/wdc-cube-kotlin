// Generated from br.com.wdc.kt.store.i18n/messages.<language>.json by ./gradlew :store-scripts:generateMessages: do not edit.
package br.com.wdc.store.i18n

/** The languages of the catalogs. [Default] is en-US. */
enum class Language(val tag: String) {
    EnUs("en-US"),
    PtBr("pt-BR"),
    ;

    companion object {
        val Default: Language = EnUs

        /** The language of a platform's tag (`pt-BR`, `pt_BR`, `pt`): the same tag, else the same language, else [Default]. */
        fun of(tag: String?): Language {
            val wanted = tag?.replace('_', '-')?.lowercase() ?: return Default
            return entries.firstOrNull { it.tag.lowercase() == wanted }
                ?: entries.firstOrNull { it.tag.substringBefore('-').lowercase() == wanted.substringBefore('-') }
                ?: Default
        }
    }
}

/** The keys of the catalogs. */
object MessageKeys {
    const val BrowserConnectingInMinutes = "browser.connectingInMinutes"
    const val BrowserConnectingInSeconds = "browser.connectingInSeconds"
    const val BrowserConnectingNow = "browser.connectingNow"
    const val BrowserMissingHomeContent = "browser.missingHomeContent"
    const val BrowserNotConnected = "browser.notConnected"
    const val BrowserOutdated = "browser.outdated"
    const val BrowserTryNow = "browser.tryNow"
    const val BrowserUnexpectedError = "browser.unexpectedError"
    const val BrowserUriNotReachable = "browser.uriNotReachable"
    const val BrowserWarning = "browser.warning"
    const val CartBuy = "cart.buy"
    const val CartEmpty = "cart.empty"
    const val CartEmptyHint = "cart.emptyHint"
    const val CartItemPriceTimesQuantity = "cart.itemPriceTimesQuantity"
    const val CartRemove = "cart.remove"
    const val CartTitle = "cart.title"
    const val CommonAppName = "common.appName"
    const val CommonContinueShopping = "common.continueShopping"
    const val CommonDateUnavailable = "common.dateUnavailable"
    const val CommonDecrease = "common.decrease"
    const val CommonIncrease = "common.increase"
    const val CommonItemCountOne = "common.itemCountOne"
    const val CommonItemCountOther = "common.itemCountOther"
    const val CommonOk = "common.ok"
    const val CommonTotal = "common.total"
    const val ErrorCartNotAvailable = "error.cartNotAvailable"
    const val ErrorConnectionFailed = "error.connectionFailed"
    const val ErrorDatabaseOffline = "error.databaseOffline"
    const val ErrorDatabaseOfflineRetry = "error.databaseOfflineRetry"
    const val ErrorEmptyCartPurchase = "error.emptyCartPurchase"
    const val ErrorInvalidQuantityFormat = "error.invalidQuantityFormat"
    const val ErrorItemQuantityBelowOne = "error.itemQuantityBelowOne"
    const val ErrorMalformedProductCode = "error.malformedProductCode"
    const val ErrorMalformedQuantity = "error.malformedQuantity"
    const val ErrorProductCodeNotFound = "error.productCodeNotFound"
    const val ErrorProductCodeRequired = "error.productCodeRequired"
    const val ErrorProductNotInDatabase = "error.productNotInDatabase"
    const val ErrorQuantityAtLeastOne = "error.quantityAtLeastOne"
    const val ErrorReceiptCodeNotFound = "error.receiptCodeNotFound"
    const val ErrorReceiptCodeRequired = "error.receiptCodeRequired"
    const val ErrorSessionExpired = "error.sessionExpired"
    const val ErrorTooManySignIns = "error.tooManySignIns"
    const val ErrorTooManySignInsSeconds = "error.tooManySignInsSeconds"
    const val ErrorUnexpected = "error.unexpected"
    const val ErrorUnexpectedWithCause = "error.unexpectedWithCause"
    const val ErrorUserOrPasswordNotRecognized = "error.userOrPasswordNotRecognized"
    const val FormatDate = "format.date"
    const val HomeCart = "home.cart"
    const val HomeGreeting = "home.greeting"
    const val HomeSignOut = "home.signOut"
    const val HomeTabProducts = "home.tabProducts"
    const val HomeTabPurchases = "home.tabPurchases"
    const val LoginPassword = "login.password"
    const val LoginSignIn = "login.signIn"
    const val LoginSubtitle = "login.subtitle"
    const val LoginUsername = "login.username"
    const val ProductAddToCart = "product.addToCart"
    const val ProductBack = "product.back"
    const val ProductLoading = "product.loading"
    const val ProductQuantity = "product.quantity"
    const val ProductsEmpty = "products.empty"
    const val ProductsLoading = "products.loading"
    const val ProductsTitle = "products.title"
    const val PurchasesEmpty = "purchases.empty"
    const val PurchasesPageOf = "purchases.pageOf"
    const val PurchasesTitle = "purchases.title"
    const val ReceiptColumnAmount = "receipt.columnAmount"
    const val ReceiptColumnItem = "receipt.columnItem"
    const val ReceiptColumnQty = "receipt.columnQty"
    const val ReceiptPurchaseCompleted = "receipt.purchaseCompleted"
    const val ReceiptQuantityTimes = "receipt.quantityTimes"
    const val ReceiptTitle = "receipt.title"
}

/** Connecting in {minutes}m {seconds}s... */
fun Messages.browserConnectingInMinutes(minutes: Any?, seconds: Any?): String = text(MessageKeys.BrowserConnectingInMinutes, "minutes" to minutes, "seconds" to seconds)

/** Connecting in {seconds}s... */
fun Messages.browserConnectingInSeconds(seconds: Any?): String = text(MessageKeys.BrowserConnectingInSeconds, "seconds" to seconds)

/** Connecting now... */
fun Messages.browserConnectingNow(): String = text(MessageKeys.BrowserConnectingNow)

/** Missing content for the home page */
fun Messages.browserMissingHomeContent(): String = text(MessageKeys.BrowserMissingHomeContent)

/** Not connected. */
fun Messages.browserNotConnected(): String = text(MessageKeys.BrowserNotConnected)

/** This page is out of date. Reload it to continue. */
fun Messages.browserOutdated(): String = text(MessageKeys.BrowserOutdated)

/** Try now */
fun Messages.browserTryNow(): String = text(MessageKeys.BrowserTryNow)

/** An unexpected error occurred */
fun Messages.browserUnexpectedError(): String = text(MessageKeys.BrowserUnexpectedError)

/** The URI {uri} is not reachable */
fun Messages.browserUriNotReachable(uri: Any?): String = text(MessageKeys.BrowserUriNotReachable, "uri" to uri)

/** Warning! */
fun Messages.browserWarning(): String = text(MessageKeys.BrowserWarning)

/** Buy */
fun Messages.cartBuy(): String = text(MessageKeys.CartBuy)

/** Your cart is empty */
fun Messages.cartEmpty(): String = text(MessageKeys.CartEmpty)

/** Add products to start shopping */
fun Messages.cartEmptyHint(): String = text(MessageKeys.CartEmptyHint)

/** {price} × {quantity} */
fun Messages.cartItemPriceTimesQuantity(price: Any?, quantity: Any?): String = text(MessageKeys.CartItemPriceTimesQuantity, "price" to price, "quantity" to quantity)

/** Remove */
fun Messages.cartRemove(): String = text(MessageKeys.CartRemove)

/** Shopping Cart */
fun Messages.cartTitle(): String = text(MessageKeys.CartTitle)

/** WDC Store */
fun Messages.commonAppName(): String = text(MessageKeys.CommonAppName)

/** Continue Shopping */
fun Messages.commonContinueShopping(): String = text(MessageKeys.CommonContinueShopping)

/** Date unavailable */
fun Messages.commonDateUnavailable(): String = text(MessageKeys.CommonDateUnavailable)

/** Decrease */
fun Messages.commonDecrease(): String = text(MessageKeys.CommonDecrease)

/** Increase */
fun Messages.commonIncrease(): String = text(MessageKeys.CommonIncrease)

/** {count} item */
fun Messages.commonItemCountOne(count: Any?): String = text(MessageKeys.CommonItemCountOne, "count" to count)

/** {count} items */
fun Messages.commonItemCountOther(count: Any?): String = text(MessageKeys.CommonItemCountOther, "count" to count)

/** OK */
fun Messages.commonOk(): String = text(MessageKeys.CommonOk)

/** Total: */
fun Messages.commonTotal(): String = text(MessageKeys.CommonTotal)

/** The cart is not available. Please wait a moment and try again. */
fun Messages.errorCartNotAvailable(): String = text(MessageKeys.ErrorCartNotAvailable)

/** Failed to communicate with the server. Check your connection. */
fun Messages.errorConnectionFailed(): String = text(MessageKeys.ErrorConnectionFailed)

/** The database is offline! */
fun Messages.errorDatabaseOffline(): String = text(MessageKeys.ErrorDatabaseOffline)

/** The database is currently offline. Please wait a moment and try again. */
fun Messages.errorDatabaseOfflineRetry(): String = text(MessageKeys.ErrorDatabaseOfflineRetry)

/** There must be at least one item in the cart to complete a purchase. */
fun Messages.errorEmptyCartPurchase(): String = text(MessageKeys.ErrorEmptyCartPurchase)

/** Invalid quantity format. */
fun Messages.errorInvalidQuantityFormat(): String = text(MessageKeys.ErrorInvalidQuantityFormat)

/** Some products have a quantity of less than one item. Unable to purchase. */
fun Messages.errorItemQuantityBelowOne(): String = text(MessageKeys.ErrorItemQuantityBelowOne)

/** Malformed product code. */
fun Messages.errorMalformedProductCode(): String = text(MessageKeys.ErrorMalformedProductCode)

/** Malformed quantity value. */
fun Messages.errorMalformedQuantity(): String = text(MessageKeys.ErrorMalformedQuantity)

/** Product code not found. */
fun Messages.errorProductCodeNotFound(): String = text(MessageKeys.ErrorProductCodeNotFound)

/** Product code is a required argument. */
fun Messages.errorProductCodeRequired(): String = text(MessageKeys.ErrorProductCodeRequired)

/** Product not found in the database. */
fun Messages.errorProductNotInDatabase(): String = text(MessageKeys.ErrorProductNotInDatabase)

/** The quantity of items in the cart must be at least 1 (one). */
fun Messages.errorQuantityAtLeastOne(): String = text(MessageKeys.ErrorQuantityAtLeastOne)

/** Receipt code not found. */
fun Messages.errorReceiptCodeNotFound(): String = text(MessageKeys.ErrorReceiptCodeNotFound)

/** Receipt code is a required argument. */
fun Messages.errorReceiptCodeRequired(): String = text(MessageKeys.ErrorReceiptCodeRequired)

/** Your session has expired. Please sign in again. */
fun Messages.errorSessionExpired(): String = text(MessageKeys.ErrorSessionExpired)

/** Too many failed sign-ins. Try again in a few minutes. */
fun Messages.errorTooManySignIns(): String = text(MessageKeys.ErrorTooManySignIns)

/** Too many failed sign-ins. Try again in {seconds} seconds. */
fun Messages.errorTooManySignInsSeconds(seconds: Any?): String = text(MessageKeys.ErrorTooManySignInsSeconds, "seconds" to seconds)

/** Unexpected error */
fun Messages.errorUnexpected(): String = text(MessageKeys.ErrorUnexpected)

/** Unexpected error: {cause} */
fun Messages.errorUnexpectedWithCause(cause: Any?): String = text(MessageKeys.ErrorUnexpectedWithCause, "cause" to cause)

/** Username or password not recognized! */
fun Messages.errorUserOrPasswordNotRecognized(): String = text(MessageKeys.ErrorUserOrPasswordNotRecognized)

/** {month}/{day}/{year} */
fun Messages.formatDate(month: Any?, day: Any?, year: Any?): String = text(MessageKeys.FormatDate, "month" to month, "day" to day, "year" to year)

/** Cart */
fun Messages.homeCart(): String = text(MessageKeys.HomeCart)

/** Hello, {name} */
fun Messages.homeGreeting(name: Any?): String = text(MessageKeys.HomeGreeting, "name" to name)

/** Sign out */
fun Messages.homeSignOut(): String = text(MessageKeys.HomeSignOut)

/** Products */
fun Messages.homeTabProducts(): String = text(MessageKeys.HomeTabProducts)

/** Purchases */
fun Messages.homeTabPurchases(): String = text(MessageKeys.HomeTabPurchases)

/** Password */
fun Messages.loginPassword(): String = text(MessageKeys.LoginPassword)

/** Sign in */
fun Messages.loginSignIn(): String = text(MessageKeys.LoginSignIn)

/** Sign in to continue */
fun Messages.loginSubtitle(): String = text(MessageKeys.LoginSubtitle)

/** Username */
fun Messages.loginUsername(): String = text(MessageKeys.LoginUsername)

/** Add to Cart */
fun Messages.productAddToCart(): String = text(MessageKeys.ProductAddToCart)

/** Back */
fun Messages.productBack(): String = text(MessageKeys.ProductBack)

/** Loading product... */
fun Messages.productLoading(): String = text(MessageKeys.ProductLoading)

/** Qty: */
fun Messages.productQuantity(): String = text(MessageKeys.ProductQuantity)

/** No products available */
fun Messages.productsEmpty(): String = text(MessageKeys.ProductsEmpty)

/** Loading products... */
fun Messages.productsLoading(): String = text(MessageKeys.ProductsLoading)

/** Products */
fun Messages.productsTitle(): String = text(MessageKeys.ProductsTitle)

/** No purchases yet */
fun Messages.purchasesEmpty(): String = text(MessageKeys.PurchasesEmpty)

/** {page} / {pages} */
fun Messages.purchasesPageOf(page: Any?, pages: Any?): String = text(MessageKeys.PurchasesPageOf, "page" to page, "pages" to pages)

/** Purchases */
fun Messages.purchasesTitle(): String = text(MessageKeys.PurchasesTitle)

/** Amount */
fun Messages.receiptColumnAmount(): String = text(MessageKeys.ReceiptColumnAmount)

/** Item */
fun Messages.receiptColumnItem(): String = text(MessageKeys.ReceiptColumnItem)

/** Qty */
fun Messages.receiptColumnQty(): String = text(MessageKeys.ReceiptColumnQty)

/** Purchase completed successfully! */
fun Messages.receiptPurchaseCompleted(): String = text(MessageKeys.ReceiptPurchaseCompleted)

/** {quantity}x */
fun Messages.receiptQuantityTimes(quantity: Any?): String = text(MessageKeys.ReceiptQuantityTimes, "quantity" to quantity)

/** Receipt */
fun Messages.receiptTitle(): String = text(MessageKeys.ReceiptTitle)

internal fun catalogOf(language: Language): Map<String, String> = when (language) {
    Language.EnUs -> EnUsCatalog
    Language.PtBr -> PtBrCatalog
}

private val EnUsCatalog: Map<String, String> = mapOf(
    "browser.connectingInMinutes" to "Connecting in {minutes}m {seconds}s...",
    "browser.connectingInSeconds" to "Connecting in {seconds}s...",
    "browser.connectingNow" to "Connecting now...",
    "browser.missingHomeContent" to "Missing content for the home page",
    "browser.notConnected" to "Not connected.",
    "browser.outdated" to "This page is out of date. Reload it to continue.",
    "browser.tryNow" to "Try now",
    "browser.unexpectedError" to "An unexpected error occurred",
    "browser.uriNotReachable" to "The URI {uri} is not reachable",
    "browser.warning" to "Warning!",
    "cart.buy" to "Buy",
    "cart.empty" to "Your cart is empty",
    "cart.emptyHint" to "Add products to start shopping",
    "cart.itemPriceTimesQuantity" to "{price} × {quantity}",
    "cart.remove" to "Remove",
    "cart.title" to "Shopping Cart",
    "common.appName" to "WDC Store",
    "common.continueShopping" to "Continue Shopping",
    "common.dateUnavailable" to "Date unavailable",
    "common.decrease" to "Decrease",
    "common.increase" to "Increase",
    "common.itemCountOne" to "{count} item",
    "common.itemCountOther" to "{count} items",
    "common.ok" to "OK",
    "common.total" to "Total:",
    "error.cartNotAvailable" to "The cart is not available. Please wait a moment and try again.",
    "error.connectionFailed" to "Failed to communicate with the server. Check your connection.",
    "error.databaseOffline" to "The database is offline!",
    "error.databaseOfflineRetry" to "The database is currently offline. Please wait a moment and try again.",
    "error.emptyCartPurchase" to "There must be at least one item in the cart to complete a purchase.",
    "error.invalidQuantityFormat" to "Invalid quantity format.",
    "error.itemQuantityBelowOne" to "Some products have a quantity of less than one item. Unable to purchase.",
    "error.malformedProductCode" to "Malformed product code.",
    "error.malformedQuantity" to "Malformed quantity value.",
    "error.productCodeNotFound" to "Product code not found.",
    "error.productCodeRequired" to "Product code is a required argument.",
    "error.productNotInDatabase" to "Product not found in the database.",
    "error.quantityAtLeastOne" to "The quantity of items in the cart must be at least 1 (one).",
    "error.receiptCodeNotFound" to "Receipt code not found.",
    "error.receiptCodeRequired" to "Receipt code is a required argument.",
    "error.sessionExpired" to "Your session has expired. Please sign in again.",
    "error.tooManySignIns" to "Too many failed sign-ins. Try again in a few minutes.",
    "error.tooManySignInsSeconds" to "Too many failed sign-ins. Try again in {seconds} seconds.",
    "error.unexpected" to "Unexpected error",
    "error.unexpectedWithCause" to "Unexpected error: {cause}",
    "error.userOrPasswordNotRecognized" to "Username or password not recognized!",
    "format.date" to "{month}/{day}/{year}",
    "home.cart" to "Cart",
    "home.greeting" to "Hello, {name}",
    "home.signOut" to "Sign out",
    "home.tabProducts" to "Products",
    "home.tabPurchases" to "Purchases",
    "login.password" to "Password",
    "login.signIn" to "Sign in",
    "login.subtitle" to "Sign in to continue",
    "login.username" to "Username",
    "product.addToCart" to "Add to Cart",
    "product.back" to "Back",
    "product.loading" to "Loading product...",
    "product.quantity" to "Qty:",
    "products.empty" to "No products available",
    "products.loading" to "Loading products...",
    "products.title" to "Products",
    "purchases.empty" to "No purchases yet",
    "purchases.pageOf" to "{page} / {pages}",
    "purchases.title" to "Purchases",
    "receipt.columnAmount" to "Amount",
    "receipt.columnItem" to "Item",
    "receipt.columnQty" to "Qty",
    "receipt.purchaseCompleted" to "Purchase completed successfully!",
    "receipt.quantityTimes" to "{quantity}x",
    "receipt.title" to "Receipt",
)

private val PtBrCatalog: Map<String, String> = mapOf(
    "browser.connectingInMinutes" to "Conectando em {minutes}min {seconds}s...",
    "browser.connectingInSeconds" to "Conectando em {seconds}s...",
    "browser.connectingNow" to "Conectando agora...",
    "browser.missingHomeContent" to "Falta o conteúdo da página inicial",
    "browser.notConnected" to "Sem conexão.",
    "browser.outdated" to "Esta página está desatualizada. Recarregue-a para continuar.",
    "browser.tryNow" to "Tentar agora",
    "browser.unexpectedError" to "Ocorreu um erro inesperado",
    "browser.uriNotReachable" to "O endereço {uri} não está acessível",
    "browser.warning" to "Atenção!",
    "cart.buy" to "Comprar",
    "cart.empty" to "Seu carrinho está vazio",
    "cart.emptyHint" to "Adicione produtos para começar a comprar",
    "cart.itemPriceTimesQuantity" to "{price} × {quantity}",
    "cart.remove" to "Remover",
    "cart.title" to "Carrinho de compras",
    "common.appName" to "WDC Store",
    "common.continueShopping" to "Continuar comprando",
    "common.dateUnavailable" to "Data indisponível",
    "common.decrease" to "Diminuir",
    "common.increase" to "Aumentar",
    "common.itemCountOne" to "{count} item",
    "common.itemCountOther" to "{count} itens",
    "common.ok" to "OK",
    "common.total" to "Total:",
    "error.cartNotAvailable" to "O carrinho não está disponível. Aguarde um momento e tente de novo.",
    "error.connectionFailed" to "Não foi possível falar com o servidor. Verifique sua conexão.",
    "error.databaseOffline" to "O banco de dados está fora do ar!",
    "error.databaseOfflineRetry" to "O banco de dados está fora do ar no momento. Aguarde um momento e tente de novo.",
    "error.emptyCartPurchase" to "É preciso ter pelo menos um item no carrinho para concluir a compra.",
    "error.invalidQuantityFormat" to "Formato de quantidade inválido.",
    "error.itemQuantityBelowOne" to "Alguns produtos têm quantidade menor que um item. Não é possível comprar.",
    "error.malformedProductCode" to "Código de produto malformado.",
    "error.malformedQuantity" to "Valor de quantidade malformado.",
    "error.productCodeNotFound" to "Código de produto não encontrado.",
    "error.productCodeRequired" to "O código do produto é obrigatório.",
    "error.productNotInDatabase" to "Produto não encontrado no banco de dados.",
    "error.quantityAtLeastOne" to "A quantidade de itens no carrinho deve ser de pelo menos 1 (um).",
    "error.receiptCodeNotFound" to "Código de recibo não encontrado.",
    "error.receiptCodeRequired" to "O código do recibo é obrigatório.",
    "error.sessionExpired" to "Sua sessão expirou. Entre de novo.",
    "error.tooManySignIns" to "Muitas tentativas de entrada sem sucesso. Tente de novo em alguns minutos.",
    "error.tooManySignInsSeconds" to "Muitas tentativas de entrada sem sucesso. Tente de novo em {seconds} segundos.",
    "error.unexpected" to "Erro inesperado",
    "error.unexpectedWithCause" to "Erro inesperado: {cause}",
    "error.userOrPasswordNotRecognized" to "Usuário ou senha não reconhecidos!",
    "format.date" to "{day}/{month}/{year}",
    "home.cart" to "Carrinho",
    "home.greeting" to "Olá, {name}",
    "home.signOut" to "Sair",
    "home.tabProducts" to "Produtos",
    "home.tabPurchases" to "Compras",
    "login.password" to "Senha",
    "login.signIn" to "Entrar",
    "login.subtitle" to "Entre para continuar",
    "login.username" to "Usuário",
    "product.addToCart" to "Adicionar ao carrinho",
    "product.back" to "Voltar",
    "product.loading" to "Carregando produto...",
    "product.quantity" to "Qtd.:",
    "products.empty" to "Nenhum produto disponível",
    "products.loading" to "Carregando produtos...",
    "products.title" to "Produtos",
    "purchases.empty" to "Nenhuma compra ainda",
    "purchases.pageOf" to "{page} / {pages}",
    "purchases.title" to "Compras",
    "receipt.columnAmount" to "Valor",
    "receipt.columnItem" to "Item",
    "receipt.columnQty" to "Qtd.",
    "receipt.purchaseCompleted" to "Compra concluída com sucesso!",
    "receipt.quantityTimes" to "{quantity}x",
    "receipt.title" to "Recibo",
)
