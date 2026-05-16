# framework-cube

Engine do padrão **Cube MVP** — gerenciamento de estado, navegação hierárquica com Places, e ciclo de vida de presenters.

**Plataformas:** JVM, Android, iOS, wasmJs

## Componentes

| Componente | Responsabilidade |
|------------|-----------------|
| `CubePlace` | Destino de navegação com ID, nome e factory de presenter |
| `CubePresenter` | Ciclo de vida: `applyParameters` → `publishParameters` → `commitComputedState` → `release` |
| `CubeIntent` | Container de parâmetros (serializáveis) e atributos (efêmeros, como `ViewSlot`) |
| `CubeViewSlot` | Mecanismo de composição pai-filho entre views |
| `CubeView` | Interface de renderização (Compose, React, etc.) |
| `CubeApplication` | Estado global — mapa de presenters ativos e ponto de entrada `navigate()` |
| `CubeNavigation` | Motor transacional — orquestra execute → commit/rollback com suporte a interrupções |

## Documentação

- [Arquitetura Cube — Mecanismo de Navegação](../../../docs/architecture-cube.md) — explicação detalhada do ciclo de navegação, interrupções, migração de presenters, commit, rollback e garantias do framework
- [Arquitetura Cube + Compose](../../../docs/architecture-cube-compose.md) — integração com Compose Multiplatform: ComposeCubeView, revision counter, safeCall, slots, view factories
- [Arquitetura Geral do Projeto](../../../docs/architecture.md) — visão geral da arquitetura, camadas, módulos e padrões
