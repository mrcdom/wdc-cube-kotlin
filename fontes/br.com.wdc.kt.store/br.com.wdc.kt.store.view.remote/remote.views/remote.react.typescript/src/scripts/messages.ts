// Generated from br.com.wdc.kt.store.i18n/messages.<language>.json by ./gradlew :store-scripts:generateMessages: do not edit.

/** The languages of the catalogs; the first is the default. */
export const LANGUAGES = ['en-US', 'pt-BR'] as const
export type Language = (typeof LANGUAGES)[number]
export const DEFAULT_LANGUAGE: Language = 'en-US'

export type MessageKey =
  | 'browser.connectingInMinutes'
  | 'browser.connectingInSeconds'
  | 'browser.connectingNow'
  | 'browser.missingHomeContent'
  | 'browser.notConnected'
  | 'browser.tryNow'
  | 'browser.unexpectedError'
  | 'browser.uriNotReachable'
  | 'browser.warning'
  | 'cart.buy'
  | 'cart.empty'
  | 'cart.emptyHint'
  | 'cart.itemPriceTimesQuantity'
  | 'cart.remove'
  | 'cart.title'
  | 'common.appName'
  | 'common.continueShopping'
  | 'common.dateUnavailable'
  | 'common.decrease'
  | 'common.increase'
  | 'common.itemCountOne'
  | 'common.itemCountOther'
  | 'common.ok'
  | 'common.total'
  | 'error.cartNotAvailable'
  | 'error.connectionFailed'
  | 'error.databaseOffline'
  | 'error.databaseOfflineRetry'
  | 'error.emptyCartPurchase'
  | 'error.invalidQuantityFormat'
  | 'error.itemQuantityBelowOne'
  | 'error.malformedProductCode'
  | 'error.malformedQuantity'
  | 'error.productCodeNotFound'
  | 'error.productCodeRequired'
  | 'error.productNotInDatabase'
  | 'error.quantityAtLeastOne'
  | 'error.receiptCodeNotFound'
  | 'error.receiptCodeRequired'
  | 'error.sessionExpired'
  | 'error.tooManySignIns'
  | 'error.tooManySignInsSeconds'
  | 'error.unexpected'
  | 'error.unexpectedWithCause'
  | 'error.userOrPasswordNotRecognized'
  | 'format.date'
  | 'home.cart'
  | 'home.greeting'
  | 'home.signOut'
  | 'home.tabProducts'
  | 'home.tabPurchases'
  | 'login.password'
  | 'login.signIn'
  | 'login.subtitle'
  | 'login.username'
  | 'product.addToCart'
  | 'product.back'
  | 'product.loading'
  | 'product.quantity'
  | 'products.empty'
  | 'products.loading'
  | 'products.title'
  | 'purchases.empty'
  | 'purchases.pageOf'
  | 'purchases.title'
  | 'receipt.columnAmount'
  | 'receipt.columnItem'
  | 'receipt.columnQty'
  | 'receipt.purchaseCompleted'
  | 'receipt.quantityTimes'
  | 'receipt.title'

const CATALOGS: Record<Language, Record<MessageKey, string>> = {
  'en-US': {
    'browser.connectingInMinutes': 'Connecting in {minutes}m {seconds}s...',
    'browser.connectingInSeconds': 'Connecting in {seconds}s...',
    'browser.connectingNow': 'Connecting now...',
    'browser.missingHomeContent': 'Missing content for the home page',
    'browser.notConnected': 'Not connected.',
    'browser.tryNow': 'Try now',
    'browser.unexpectedError': 'An unexpected error occurred',
    'browser.uriNotReachable': 'The URI {uri} is not reachable',
    'browser.warning': 'Warning!',
    'cart.buy': 'Buy',
    'cart.empty': 'Your cart is empty',
    'cart.emptyHint': 'Add products to start shopping',
    'cart.itemPriceTimesQuantity': '{price} × {quantity}',
    'cart.remove': 'Remove',
    'cart.title': 'Shopping Cart',
    'common.appName': 'WDC Store',
    'common.continueShopping': 'Continue Shopping',
    'common.dateUnavailable': 'Date unavailable',
    'common.decrease': 'Decrease',
    'common.increase': 'Increase',
    'common.itemCountOne': '{count} item',
    'common.itemCountOther': '{count} items',
    'common.ok': 'OK',
    'common.total': 'Total:',
    'error.cartNotAvailable': 'The cart is not available. Please wait a moment and try again.',
    'error.connectionFailed': 'Failed to communicate with the server. Check your connection.',
    'error.databaseOffline': 'The database is offline!',
    'error.databaseOfflineRetry': 'The database is currently offline. Please wait a moment and try again.',
    'error.emptyCartPurchase': 'There must be at least one item in the cart to complete a purchase.',
    'error.invalidQuantityFormat': 'Invalid quantity format.',
    'error.itemQuantityBelowOne': 'Some products have a quantity of less than one item. Unable to purchase.',
    'error.malformedProductCode': 'Malformed product code.',
    'error.malformedQuantity': 'Malformed quantity value.',
    'error.productCodeNotFound': 'Product code not found.',
    'error.productCodeRequired': 'Product code is a required argument.',
    'error.productNotInDatabase': 'Product not found in the database.',
    'error.quantityAtLeastOne': 'The quantity of items in the cart must be at least 1 (one).',
    'error.receiptCodeNotFound': 'Receipt code not found.',
    'error.receiptCodeRequired': 'Receipt code is a required argument.',
    'error.sessionExpired': 'Your session has expired. Please sign in again.',
    'error.tooManySignIns': 'Too many failed sign-ins. Try again in a few minutes.',
    'error.tooManySignInsSeconds': 'Too many failed sign-ins. Try again in {seconds} seconds.',
    'error.unexpected': 'Unexpected error',
    'error.unexpectedWithCause': 'Unexpected error: {cause}',
    'error.userOrPasswordNotRecognized': 'Username or password not recognized!',
    'format.date': '{month}/{day}/{year}',
    'home.cart': 'Cart',
    'home.greeting': 'Hello, {name}',
    'home.signOut': 'Sign out',
    'home.tabProducts': 'Products',
    'home.tabPurchases': 'Purchases',
    'login.password': 'Password',
    'login.signIn': 'Sign in',
    'login.subtitle': 'Sign in to continue',
    'login.username': 'Username',
    'product.addToCart': 'Add to Cart',
    'product.back': 'Back',
    'product.loading': 'Loading product...',
    'product.quantity': 'Qty:',
    'products.empty': 'No products available',
    'products.loading': 'Loading products...',
    'products.title': 'Products',
    'purchases.empty': 'No purchases yet',
    'purchases.pageOf': '{page} / {pages}',
    'purchases.title': 'Purchases',
    'receipt.columnAmount': 'Amount',
    'receipt.columnItem': 'Item',
    'receipt.columnQty': 'Qty',
    'receipt.purchaseCompleted': 'Purchase completed successfully!',
    'receipt.quantityTimes': '{quantity}x',
    'receipt.title': 'Receipt',
  },
  'pt-BR': {
    'browser.connectingInMinutes': 'Conectando em {minutes}min {seconds}s...',
    'browser.connectingInSeconds': 'Conectando em {seconds}s...',
    'browser.connectingNow': 'Conectando agora...',
    'browser.missingHomeContent': 'Falta o conteúdo da página inicial',
    'browser.notConnected': 'Sem conexão.',
    'browser.tryNow': 'Tentar agora',
    'browser.unexpectedError': 'Ocorreu um erro inesperado',
    'browser.uriNotReachable': 'O endereço {uri} não está acessível',
    'browser.warning': 'Atenção!',
    'cart.buy': 'Comprar',
    'cart.empty': 'Seu carrinho está vazio',
    'cart.emptyHint': 'Adicione produtos para começar a comprar',
    'cart.itemPriceTimesQuantity': '{price} × {quantity}',
    'cart.remove': 'Remover',
    'cart.title': 'Carrinho de compras',
    'common.appName': 'WDC Store',
    'common.continueShopping': 'Continuar comprando',
    'common.dateUnavailable': 'Data indisponível',
    'common.decrease': 'Diminuir',
    'common.increase': 'Aumentar',
    'common.itemCountOne': '{count} item',
    'common.itemCountOther': '{count} itens',
    'common.ok': 'OK',
    'common.total': 'Total:',
    'error.cartNotAvailable': 'O carrinho não está disponível. Aguarde um momento e tente de novo.',
    'error.connectionFailed': 'Não foi possível falar com o servidor. Verifique sua conexão.',
    'error.databaseOffline': 'O banco de dados está fora do ar!',
    'error.databaseOfflineRetry': 'O banco de dados está fora do ar no momento. Aguarde um momento e tente de novo.',
    'error.emptyCartPurchase': 'É preciso ter pelo menos um item no carrinho para concluir a compra.',
    'error.invalidQuantityFormat': 'Formato de quantidade inválido.',
    'error.itemQuantityBelowOne': 'Alguns produtos têm quantidade menor que um item. Não é possível comprar.',
    'error.malformedProductCode': 'Código de produto malformado.',
    'error.malformedQuantity': 'Valor de quantidade malformado.',
    'error.productCodeNotFound': 'Código de produto não encontrado.',
    'error.productCodeRequired': 'O código do produto é obrigatório.',
    'error.productNotInDatabase': 'Produto não encontrado no banco de dados.',
    'error.quantityAtLeastOne': 'A quantidade de itens no carrinho deve ser de pelo menos 1 (um).',
    'error.receiptCodeNotFound': 'Código de recibo não encontrado.',
    'error.receiptCodeRequired': 'O código do recibo é obrigatório.',
    'error.sessionExpired': 'Sua sessão expirou. Entre de novo.',
    'error.tooManySignIns': 'Muitas tentativas de entrada sem sucesso. Tente de novo em alguns minutos.',
    'error.tooManySignInsSeconds': 'Muitas tentativas de entrada sem sucesso. Tente de novo em {seconds} segundos.',
    'error.unexpected': 'Erro inesperado',
    'error.unexpectedWithCause': 'Erro inesperado: {cause}',
    'error.userOrPasswordNotRecognized': 'Usuário ou senha não reconhecidos!',
    'format.date': '{day}/{month}/{year}',
    'home.cart': 'Carrinho',
    'home.greeting': 'Olá, {name}',
    'home.signOut': 'Sair',
    'home.tabProducts': 'Produtos',
    'home.tabPurchases': 'Compras',
    'login.password': 'Senha',
    'login.signIn': 'Entrar',
    'login.subtitle': 'Entre para continuar',
    'login.username': 'Usuário',
    'product.addToCart': 'Adicionar ao carrinho',
    'product.back': 'Voltar',
    'product.loading': 'Carregando produto...',
    'product.quantity': 'Qtd.:',
    'products.empty': 'Nenhum produto disponível',
    'products.loading': 'Carregando produtos...',
    'products.title': 'Produtos',
    'purchases.empty': 'Nenhuma compra ainda',
    'purchases.pageOf': '{page} / {pages}',
    'purchases.title': 'Compras',
    'receipt.columnAmount': 'Valor',
    'receipt.columnItem': 'Item',
    'receipt.columnQty': 'Qtd.',
    'receipt.purchaseCompleted': 'Compra concluída com sucesso!',
    'receipt.quantityTimes': '{quantity}x',
    'receipt.title': 'Recibo',
  },
}

/** The language of a browser's tag (`pt-BR`, `pt`): the same tag, else the same language, else the default. */
export function languageOf(tag: string | null | undefined): Language {
  if (!tag) return DEFAULT_LANGUAGE
  const wanted = tag.replace('_', '-').toLowerCase()
  return (
    LANGUAGES.find((it) => it.toLowerCase() === wanted) ??
    LANGUAGES.find((it) => it.split('-')[0].toLowerCase() === wanted.split('-')[0]) ??
    DEFAULT_LANGUAGE
  )
}

/** The UI texts in one language: each key's text, with its `{placeholders}` filled. */
export class Messages {
  constructor(readonly language: Language) {}

  text(key: MessageKey | string, args: Record<string, unknown> = {}): string {
    const pattern = (CATALOGS[this.language] as Record<string, string>)[key] ?? (CATALOGS[DEFAULT_LANGUAGE] as Record<string, string>)[key] ?? key
    return pattern.replace(/\{([A-Za-z0-9_]+)\}/g, (match, name: string) => (name in args ? String(args[name]) : match))
  }

  /** A civil date in this language's format (`format.date`). */
  date(year: number, month: number, day: number): string {
    const two = (n: number) => String(n).padStart(2, '0')
    return this.text('format.date', { year, month: two(month), day: two(day) })
  }
}
