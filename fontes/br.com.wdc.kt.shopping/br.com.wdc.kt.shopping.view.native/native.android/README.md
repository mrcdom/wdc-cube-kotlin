# native.android

Implementação da view usando **Android Views programáticas** — sem XML layouts, sem Compose, sem ViewBinding.

## Arquitetura

### Base Class: `AbstractViewAndroid<P>`

Toda view estende `AbstractViewAndroid<P>`, que implementa a interface `CubeView` do framework:

```kotlin
class ProductsPanelViewAndroid(presenter: ProductsPanelPresenter) 
    : AbstractViewAndroid<ProductsPanelPresenter>("products-panel", presenter) {

    override fun createView(): View = AndroidDom.build(ctx) {
        // DSL para construção programática da UI
    }

    override fun doUpdate() {
        // Atualização eficiente com guards (comparação com estado anterior)
    }
}
```

O ciclo de vida é:
1. `createView()` — constrói a árvore de views programaticamente via `AndroidDom`
2. `doUpdate()` — chamado pelo scheduler quando o presenter sinaliza mudança de estado
3. Guards (`lastX != newX`) evitam recálculos desnecessários

### DSL: `AndroidDom`

DSL Kotlin que substitui XML/Compose com chamadas programáticas:

```kotlin
AndroidDom.build(ctx) {
    vStack(spacing = 8.dp) {
        hStack { 
            label { text = "Título"; textSize = 17f }
            spacer()
        }
        scrollView {
            flowLayout(minChildWidth = 160.dp) {
                // cards de produto
            }
        }
    }
}
```

Componentes disponíveis: `vStack`, `hStack`, `frame`, `scrollView`, `flowLayout`, `spacer`, `label`/`textView`, `button`, `imageView`, `editText`.

### Atualização de Views: `ViewUpdateScheduler`

O scheduler opera via `Handler(Looper.getMainLooper())`:

1. Presenter chama `update()` → marca a view como dirty
2. No próximo VSYNC, o scheduler faz flush de todas as views dirty
3. Antes do flush, `app.commitComputedState()` garante consistência do estado
4. Cada view executa `doUpdate()` uma única vez por frame

### Padrões de Implementação

| Padrão | Descrição |
|--------|-----------|
| **ListSlot** | Reciclagem de views em listas — grows, shrinks e updates in-place |
| **FlowLayout** | ViewGroup customizado que distribui filhos em grid responsivo baseado na largura disponível |
| **StateListDrawable** | Feedback visual de toque (background muda de cor no estado `pressed`) |
| **Guards** | Variáveis `lastX` que evitam reprocessamento quando o estado não mudou |
| **Density-aware** | Todas as dimensões usam `* density` para independência de densidade |

### Navegação e Slots

Views-pai declaram `ViewSlot` (um `FrameLayout`) que recebe views-filhas por navegação:

```kotlin
// No HomeViewAndroid
val contentSlot = newViewSlot(contentContainer)

// Quando o presenter navega para "product-details"
// O slot automaticamente troca a view visível
```

## Como compilar e executar

```bash
cd fontes

# Compilar APK debug
./gradlew :view-native-android:assembleDebug

# Deploy no emulador
cd br.com.wdc.kt.shopping/br.com.wdc.kt.shopping.view.native/native.android
./deploy.sh emulator --emulator-name Pixel_10
```

## Estrutura de Arquivos

```
src/androidMain/kotlin/br/com/wdc/shopping/nativeui/android/
├── theme/
│   ├── Colors.kt          # Paleta Material Design
│   ├── Dimens.kt          # Dimensões padrão
│   └── Styles.kt          # Helpers de estilo (cards, buttons)
├── toolkit/
│   ├── AbstractViewAndroid.kt   # Base class + ListSlot
│   ├── AndroidDom.kt           # DSL de construção de views
│   ├── FlowLayout.kt           # ViewGroup grid responsivo
│   ├── ViewUpdateScheduler.kt  # Coalescing de updates
│   └── ViewUtils.kt            # Formatação, loading de imagens
└── views/
    ├── RootViewAndroid.kt       # Entry point + registro de factories
    ├── LoginViewAndroid.kt      # Tela de login
    ├── HomeViewAndroid.kt       # Layout responsivo com panels/tabs
    ├── ProductsPanelViewAndroid.kt  # Grid de produtos
    ├── PurchasesPanelViewAndroid.kt # Lista de compras paginada
    ├── ProductViewAndroid.kt    # Detalhe do produto
    ├── CartViewAndroid.kt       # Carrinho de compras
    └── ReceiptViewAndroid.kt    # Recibo de compra
```
