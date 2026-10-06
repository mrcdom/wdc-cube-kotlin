# shopping-scripts

Scripts de criação, carga inicial e migração do banco de dados (H2 e PostgreSQL), e o gerador das classes jOOQ.

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
| `GenerateJooqSchema` | Gera as classes jOOQ de `:shopping-persistence` a partir do DDL de `DBCreate` |

## Como Funciona

### DBCreate

Ponto de entrada, e **fonte de verdade do esquema**: o DDL está escrito aqui, com as variantes de tipo de cada banco. Consulta os metadados JDBC para saber quais tabelas existem e cria as que faltam, com as suas sequências e índices.

Em seguida roda as migrações pendentes (`MigrationRunner`) e, por último, se alguma tabela foi criada ou se `withReset()` foi chamado, carrega os dados de demonstração (`DBReset`). A carga vem depois das migrações porque já grava no formato atual.

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

As senhas são armazenadas como resumo MD5 em base 36, sem sinal — o mesmo que `PasswordUtil.hashPassword` calcula. As datas são gravadas em UTC.

### MigrationRunner

Sistema de migrações baseado em **reflexão**:

1. Recebe uma instância de um script de migração (ex: `Migration_0001_AddUserRoles`)
2. Descobre métodos públicos cujo nome começa com `step` via reflexão
3. Ordena por número do step (`step01_...`, `step02_...`)
4. Para cada step, verifica na tabela de controle `EN_MIGRATION_LOG` se já foi executado
5. Se não, executa o método e registra o step com timestamp

Isso garante **idempotência** — rodar `DBCreate` múltiplas vezes é seguro. Steps já executados são ignorados.

### Criando uma Nova Migração

1. Crie uma classe `Migration_NNNN_NomeDescritivo` no pacote `sgbd`:

```kotlin
class Migration_0008_AddProductCategory(private val connection: Connection) {

    fun step01_addCategoryColumn() {
        connection.createStatement().use { stmt ->
            stmt.execute("ALTER TABLE EN_PRODUCT ADD COLUMN IF NOT EXISTS CATEGORY VARCHAR(100) DEFAULT 'GENERAL'")
        }
    }

    fun step02_setCategoryForExisting() {
        connection.createStatement().use { stmt ->
            stmt.execute("UPDATE EN_PRODUCT SET CATEGORY = 'ELECTRONICS' WHERE NAME LIKE '%Pen Drive%'")
        }
    }
}
```

2. Registre-a ao fim da lista em `DBCreate.run()`:

```kotlin
MigrationRunner(conn)
    // …as anteriores
    .run(Migration_0007_SessionExpiryToUtc(conn))
    .run(Migration_0008_AddProductCategory(conn))  // nova
```

3. Faça a mesma mudança no DDL de `DBCreate` — um banco novo já nasce no formato final — e regenere as classes jOOQ:

```bash
cd fontes && ./gradlew :shopping-scripts:generateJooqSchema
```

Regras para steps:
- Métodos devem ser **públicos** e **sem parâmetros**
- O nome deve começar com `step` seguido de um número (`step01_`, `step02_`)
- Steps são executados na ordem numérica
- Cada step deve ser **autocontido** — se falhar, os anteriores já estão registrados
- O SQL precisa valer em H2 e em PostgreSQL; quando a mudança só faz sentido num deles, confira o banco com `DBCreate.detectDialect(connection)`

## Migrações Existentes

| Migração | Steps | Descrição |
|----------|-------|-----------|
| `Migration_0001_AddUserRoles` | 2 | Adiciona coluna `ROLES` à tabela `EN_USER` e define role ADMIN para o usuário admin |
| `Migration_0002_PurchaseBuyDateToTimestamp` | 1 | Altera coluna `BUYDATE` de DATE para TIMESTAMP |
| `Migration_0003_CreateSecurityTables` | 2 | Cria tabelas `EN_USER_INTENT_SECRET` (segredos HMAC por usuário) e `EN_USER_SESSION` (sessões persistentes com RSA key pairs) |
| `Migration_0004_ImageVarbinaryAndOrderingIndexes` | 3 | Imagem do produto com tamanho variável (remove o preenchimento com zeros) e índices das ordenações |
| `Migration_0005_UnsignedPasswordDigest` | 1 | Regrava os resumos de senha que a carga antiga gravou com sinal |
| `Migration_0006_PurchaseBuyDateToUtc` | 1 | Passa a data das compras da hora local para UTC |
| `Migration_0007_SessionExpiryToUtc` | 1 | Descarta as sessões gravadas com a expiração em hora local |

## Uso nos Módulos

- **backend** — `BusinessContext` chama `DBCreate().withConnection(conn).run()` na inicialização do servidor Javalin
- **shopping-tests** — `TestEnvironment` e `RestTestEnvironment` chamam `DBCreate().withConnection(conn).withReset().run()` para garantir um banco limpo antes de cada teste

## Dependências

- `shopping-persistence` — destino das classes jOOQ geradas
- `framework-jooq` — dialeto do banco e gerador
- `h2` — banco usado pelo gerador de classes
