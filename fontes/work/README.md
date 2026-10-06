# work

Diretório de execução do backend em desenvolvimento. `./gradlew :backend:run` roda a partir de `fontes/`, e é aqui que o servidor lê a configuração e grava os seus dados.

| Pasta | Conteúdo | No git |
|---|---|---|
| `config/` | `application.example.toml` — todas as chaves, com os padrões | sim |
| `config/` | `application.toml` — a configuração local (pode ter segredo) | não |
| `data/` | banco H2 padrão | não |
| `deploy/` | frontends web publicados pelos `deploy.sh` (`compose/`, `native/`) | só o esqueleto |
| `log/`, `temp/` | logs e temporários | não |

Para começar: copie `config/application.example.toml` para `config/application.toml` e ajuste. Sem esse arquivo o backend sobe com os padrões — banco H2 em `data/` e **sem segurança**.

O diretório-base pode ser outro: chave `app.basedir` na configuração. O arquivo de configuração também: `-Dshopping.config.file=/caminho/application.toml`.
