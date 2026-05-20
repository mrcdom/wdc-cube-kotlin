# native.ios

Implementação da view usando **UIKit** via Kotlin/Native — sem SwiftUI, sem Storyboards, programático puro.

## Arquitetura

### Base Class: `AbstractViewIos<P>`

Toda view estende `AbstractViewIos<P>`, que implementa a interface `CubeView` do framework:

```kotlin
class ProductsPanelViewIos(presenter: ProductsPanelPresenter) 
    : AbstractViewIos<ProductsPanelPresenter>("products-panel", presenter) {

    override fun createView(): UIView = UIKitDom.build {
        // DSL para construção de UIViews com Auto Layout
    }

    override fun doUpdate() {
        // Atualização eficiente com guards
    }
}
```

O ciclo de vida é:
1. `createView()` — constrói a hierarquia de `UIView`s programaticamente via `UIKitDom`
2. `doUpdate()` — chamado pelo scheduler quando o presenter sinaliza mudança de estado
3. Guards (`lastX != newX`) evitam manipulações desnecessárias do UIKit

### DSL: `UIKitDom`

DSL Kotlin que constrói UIViews com Auto Layout via `NSLayoutConstraint`:

```kotlin
UIKitDom.build {
    val root = parent()
    
    vStack(spacing = 8.0) {
        label { 
            text = "Título"
            font = UIFont.boldSystemFontOfSize(17.0) 
        }
    }
    
    scrollView(configure = {
        NSLayoutConstraint.activateConstraints(listOf(
            topAnchor.constraintEqualToAnchor(root.topAnchor),
            leadingAnchor.constraintEqualToAnchor(root.leadingAnchor),
            trailingAnchor.constraintEqualToAnchor(root.trailingAnchor),
            bottomAnchor.constraintEqualToAnchor(root.bottomAnchor)
        ))
    }) {
        flowLayout(minChildWidth = 160.0) { }
    }
}
```

Componentes disponíveis: `vStack`, `hStack`, `view`, `scrollView`, `flowLayout`, `label`, `button`, `textField`, `imageView`.

Todas as views usam `translatesAutoresizingMaskIntoConstraints = false` (definido automaticamente pelo DSL) e posicionamento via Auto Layout constraints.

### Atualização de Views: `ViewUpdateScheduler`

O scheduler opera via `dispatch_async(dispatch_get_main_queue())`:

1. Presenter chama `update()` → marca a view como dirty
2. Na próxima iteração do run loop, o scheduler faz flush
3. Antes do flush, `app.commitComputedState()` garante consistência
4. Cada view executa `doUpdate()` uma única vez por ciclo

### Padrões de Implementação

| Padrão | Descrição |
|--------|-----------|
| **ListSlot** | Reciclagem de views em listas — suporta `UIStackView` e `UIView` genérico |
| **FlowLayoutView** | View customizada baseada em constraints que distribui filhos em grid responsivo |
| **retainForGC()** | Evita que o GC do Kotlin/Native colete targets de gestos (UIKit usa weak refs) |
| **UILongPressGestureRecognizer** | Feedback visual de toque (minimumPressDuration=0 para detectar touch-down) |
| **pin()** | Helper que ancora uma view nas 4 bordas do parent via constraints |
| **center()** | Helper que centraliza uma view no parent |
| **Guards** | Variáveis `lastX` que evitam redesenho quando o estado não mudou |

### Detecção de Rotação e Layout Responsivo

A view `HomeViewIos` observa `UIDeviceOrientationDidChangeNotification` para re-detectar o layout quando o dispositivo é rotacionado:

```kotlin
UIDevice.currentDevice.beginGeneratingDeviceOrientationNotifications()
orientationObserver = NSNotificationCenter.defaultCenter.addObserverForName(
    name = UIDeviceOrientationDidChangeNotification,
    `object` = null, queue = null
) { _ ->
    dispatch_async(dispatch_get_main_queue()) {
        this@HomeViewIos.detectLayout()
    }
}
```

Breakpoints:
- `WIDE_BREAKPOINT = 700pt` — acima disso, layout side-by-side (produtos + compras)
- `MAX_CONTENT_WIDTH = 1200pt` — largura máxima do container principal
- `MAX_DETAIL_WIDTH = 560pt` — largura máxima das telas de detalhe

### Navegação e Slots

Views-pai declaram `ViewSlot` (uma `UIView`) que recebe views-filhas por navegação. O slot "pina" a view-filha com constraints nas 4 bordas:

```kotlin
// No HomeViewIos
val detailSlot = newViewSlot(detailContainer)

// Quando o presenter navega para "product-details"
// O slot automaticamente troca a view visível
```

### Interop Kotlin/Native ↔ Objective-C

A comunicação com UIKit usa as anotações de interop:
- `@ObjCAction` — marca métodos que serão chamados por seletores (gesture targets)
- `sel_registerName("onTap")` — registra o seletor para a action
- `@OptIn(ExperimentalForeignApi::class)` — necessário para APIs de interop

## Como compilar e executar

```bash
cd fontes

# Compilar framework para simulador
./gradlew :view-native-ios:linkDebugFrameworkIosSimulatorArm64

# Deploy no simulador iPhone
cd br.com.wdc.kt.shopping/br.com.wdc.kt.shopping.view.native/native.ios
./deploy.sh simulator --simulator-name "iPhone 17"

# Deploy no simulador iPad
./deploy.sh simulator --ipad
```

O projeto Xcode (`ShoppingNativeApp.xcodeproj`) é gerado via [XcodeGen](https://github.com/yonaskolb/XcodeGen) a partir do `project.yml`.

## Estrutura de Arquivos

```
src/iosMain/kotlin/br/com/wdc/shopping/nativeui/ios/
├── theme/
│   ├── Colors.kt          # Paleta Material Design adaptada para UIColor
│   ├── Icons.kt           # Ícones SVG path → UIBezierPath
│   └── UIK.kt             # Constantes UIKit (alinhamentos, content modes)
├── toolkit/
│   ├── AbstractViewIos.kt       # Base class + ListSlot + ViewSlot
│   ├── UIKitDom.kt              # DSL de construção de UIViews
│   ├── FlowLayoutView.kt        # Grid responsivo baseado em constraints
│   ├── ViewUpdateScheduler.kt   # Coalescing de updates via GCD
│   └── ViewUtils.kt             # Formatação, loading async de imagens
└── views/
    ├── RootViewIos.kt           # Entry point + registro de factories
    ├── LoginViewIos.kt          # Tela de login
    ├── HomeViewIos.kt           # Layout responsivo com panels/tabs/detail
    ├── ProductsPanelViewIos.kt  # Grid de produtos (FlowLayout)
    ├── PurchasesPanelViewIos.kt # Lista de compras paginada
    ├── ProductViewIos.kt        # Detalhe do produto
    ├── CartViewIos.kt           # Carrinho de compras
    └── ReceiptViewIos.kt        # Recibo de compra

app/
├── AppDelegate.swift        # Entry point Swift (bootstrap do framework Kotlin)
├── Info.plist               # Configuração do app (orientações, ATS)
└── Assets.xcassets/         # Ícone do app
```
