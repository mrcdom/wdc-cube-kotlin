# shopping-scripts

Scripts de criação, carga inicial e migração do banco de dados H2.

## Visão Geral

Este módulo é responsável por todo o ciclo de vida do schema do banco:

1. **Criação de tabelas** — verifica quais tabelas existem e cria as que faltam
2. **Carga de dados iniciais** — popula o banco com usuários, produtos e compras de exemplo
3. **Migrações incrementais** — aplica alterações de schema de forma ordenada e idempotente

## Componentes

| Classe | Responsabilidade |
|--------|------------------|
| `DBCreate` | Orquestra criação de tabelas e execução de migrações |
| `DBReset` | Limpa todas as tabelas e insere dados de exemplo (seed) |
| `MigrationRunner` | Executa scripts de migração, registrando steps já executados |
| `Migration_NNNN_*` | Scripts de migração individuais com steps numerados |
| `EnMigrationLog` | Tabela de controle que registra quais steps já foram executados |

## Como Funciona

### DBCreate

Ponto de entrada principal. Usa a API de metadados JDBC para verificar quais tabelas já existem no schema `PUBLIC`. Para cada tabela ausente, gera o DDL a partir da classe `EnXxx` correspondente (via `createTableSql()` e `createSequenceSql()`).

Se alguma tabela foi criada (ou se `withReset()` foi chamado), executa `DBReset` para popular os dados iniciais. Em seguida, executa todas as migrações pendentes via `MigrationRunner`.

```kotlin
// Na inicialização do backend (BusinessContext)
DBCreate()
    .withConnection(connection)
    .run()

// Nos testes (TestEnvironment) — força reset para garantir estado limpo
DBCreate()
    .withConnection(connection)
    .withReset()
    .run()
```

### DBReset (Seed)

Limpa todas as tabelas na ordem correta (respeitando foreign keys) e insere dados de exemplo:

- **Usuários**: `admin` (role ADMIN), `fulano` e `beotrano` (role CUSTOMER)
- **Produtos**: Cafeteira, Bola Wilson, Fita veda rosca, Pen Drive 2GB — com descrições HTML e imagens carregadas de `resources/META-INF/images/`
- **Compras**: 2 compras do admin com 3 itens no total

Os IDs gerados ficam disponíveis como campos estáticos (`DBReset.ADMIN_ID`, `DBReset.CAFETEIRA_ID`, etc.) para uso em testes.

As senhas são armazenadas como hash MD5 em base36.

### MigrationRunner

Sistema de migrações baseado em **reflexão**:

1. Recebe uma instância de um script de migração (ex: `Migration_0001_AddUserRoles`)
2. Descobre métodos públicos cujo nome começa com `step` via reflexão
3. Ordena por número do step (`step01_...`, `step02_...`)
4. Para cada step, verifica na tabela `EN_MIGRATION_LOG` se já foi executado
5. Se não, executa o método e registra o step com timestamp

Isso garante **idempotência** — rodar `DBCreate` múltiplas vezes é seguro. Steps já executados são ignorados.

### Criando uma Nova Migração

1. Crie uma classe `Migration_NNNN_NomeDescritivo` no pacote `sgbd`:

```kotlin
class Migration_0003_AddProductCategory(private val connection: Connection) {

    fun step01_addCategoryColumn() {
        Jdbi.create(connection).open().use { handle ->
            handle.execute("ALTER TABLE EN_PRODUCT ADD COLUMN IF NOT EXISTS CATEGORY VARCHAR(100) DEFAULT 'GENERAL'")
        }
    }

    fun step02_setCategoryForExisting() {
        Jdbi.create(connection).open().use { handle ->
            handle.execute("UPDATE EN_PRODUCT SET CATEGORY = 'ELECTRONICS' WHERE NAME LIKE '%Pen Drive%'")
        }
    }
}
```

2. Registre no `DBCreate.run()`:

```kotlin
MigrationRunner(conn)
    .run(Migration_0001_AddUserRoles(conn))
    .run(Migration_0002_PurchaseBuyDateToTimestamp(conn))
    .run(Migration_0003_AddProductCategory(conn))  // nova
```

Regras para steps:
- Métodos devem ser **públicos** e **sem parâmetros**
- O nome deve começar com `step` seguido de um número (`step01_`, `step02_`)
- Steps são executados na ordem numérica
- Cada step deve ser **autocontido** — se falhar, os anteriores já estão registrados

## Migrações Existentes

| Migração | Steps | Descrição |
|----------|-------|-----------|
| `Migration_0001_AddUserRoles` | 2 | Adiciona coluna `ROLES` à tabela `EN_USER` e define role ADMIN para o usuário admin |
| `Migration_0002_PurchaseBuyDateToTimestamp` | 1 | Altera coluna `BUYDATE` de DATE para TIMESTAMP |

## Uso nos Módulos

- **backend** — `BusinessContext` chama `DBCreate().withConnection(conn).run()` na inicialização do servidor Javalin
- **shopping-tests** — `TestEnvironment` e `RestTestEnvironment` chamam `DBCreate().withConnection(conn).withReset().run()` para garantir um banco limpo antes de cada suíte de testes

## Dependências

- `shopping-persistence` — usa as classes `EnXxx` (schema), `InsertXxxRowCmd` (commands) e `SqlKeywords`/`SqlList` (SQL builder)
- `h2` — driver JDBC do banco H2
- `logback-classic` — logging
