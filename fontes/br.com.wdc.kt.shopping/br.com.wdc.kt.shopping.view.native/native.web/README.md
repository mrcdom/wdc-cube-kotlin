# native.web

Implementação da view usando **React + Material UI** via Kotlin/JS — sem Compose Web, sem Wasm.

## Arquitetura

### Base Class: `ReactCubeView`

Toda view estende `ReactCubeView`, que integra o ciclo de vida Cube com o mecanismo de renderização do React:

```kotlin
class ProductsPanelView(presenter: ProductsPanelPresenter) 
    : ReactCubeView<ProductsPanelPresenter>("products-panel", presenter) {

    override fun ChildrenScope.render() {
        // JSX via Kotlin/JS wrappers
        Typography { +"Produtos" }
        Box { /* ... */ }
    }
}
```

O ciclo de vida é:
1. `render()` — função declarativa que produz a UI via React/MUI
2. `ViewUpdateScheduler` incrementa uma `revision` → React detecta mudança de estado → re-render
3. Como React já é reativo, não há necessidade de guards manuais (o virtual DOM otimiza automaticamente)

### Stack Tecnológica

| Camada | Tecnologia |
|--------|-----------|
| **Linguagem** | Kotlin/JS (compilado para JavaScript) |
| **UI Library** | React 18 (via kotlin-wrappers) |
| **Component Library** | Material UI 5 (MUI) |
| **Bundler** | Webpack (via Gradle plugin) |
| **Routing** | Hash-based (`#home`, `#product?id=1`) via Cube navigation |

### Integração Cube ↔ React

A ponte entre o framework Cube (imperativo) e React (declarativo) é feita pelo `ViewUpdateScheduler`:

1. Presenter chama `update()` → marca a view como dirty
2. Na próxima microtask (`Promise.resolve().then {}`), o scheduler faz flush
3. O flush incrementa a `revision` (estado React) de cada view dirty
4. React re-renderiza apenas os componentes cujo estado mudou

### Padrões de Implementação

| Padrão | Descrição |
|--------|-----------|
| **RenderSlot** | Componente React que renderiza um `CubeView` filha (navegação) |
| **Responsive breakpoints** | `window.innerWidth` para alternar entre layouts mobile e desktop |
| **Hash routing** | `window.location.hash` sincronizado com a navegação Cube |
| **MUI theming** | Cores e tipografia via constantes (`ShoppingColors`, `ShoppingTheme`) |
| **useEffect + revision** | Cada view usa `useState(revision)` para trigger de re-render |

### Navegação e Slots

Views-pai usam o componente `RenderSlot` que renderiza a view-filha associada a um slot do presenter:

```kotlin
// No HomeView
RenderSlot { slot = detailSlot }

// Quando o presenter navega, o slot atualiza
// e o RenderSlot renderiza a nova view automaticamente
```

### Web Worker (opcional)

O projeto suporta execução dos presenters em um **Web Worker** separado, isolando a lógica de negócio da thread de renderização. Nesse modo:
- Presenters rodam no worker thread
- A view comunica via `postMessage` serializado
- O `bridge/` contém os adaptadores para essa comunicação

## Como compilar e executar

```bash
cd fontes

# Desenvolvimento com hot-reload
./gradlew :view-native-web:jsBrowserDevelopmentRun --continuous

# Build de produção
./gradlew :view-native-web:jsBrowserProductionWebpack

# Build do Web Worker (se necessário)
./gradlew :view-native-web-worker:jsBrowserDevelopmentWebpack
```

A aplicação estará disponível em `http://localhost:8080/native/index.html`.

## Estrutura de Arquivos

```
src/jsMain/kotlin/br/com/wdc/shopping/nativeui/web/
├── Main.kt                # Entry point — bootstrap e registro de factories
├── bridge/                # Adaptadores para Web Worker (comunicação serializada)
├── theme/
│   ├── Colors.kt          # Paleta Material Design
│   └── Theme.kt           # Configuração do tema MUI
├── util/
│   ├── ViewUpdateScheduler.kt  # Coalescing de updates via microtask
│   └── ViewUtils.kt            # Formatação, URLs de imagens
└── views/
    ├── RootView.kt              # Shell do app + hash router
    ├── LoginView.kt             # Tela de login
    ├── HomeView.kt              # Layout responsivo com panels/tabs
    ├── ProductsPanelView.kt     # Grid de produtos (CSS Grid/Flow)
    ├── PurchasesPanelView.kt    # Lista de compras paginada
    ├── ProductView.kt           # Detalhe do produto
    ├── CartView.kt              # Carrinho de compras
    └── ReceiptView.kt           # Recibo de compra
```
