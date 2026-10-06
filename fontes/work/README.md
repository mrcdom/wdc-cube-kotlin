# work

O **diretório de trabalho** do backend em desenvolvimento.

O backend não sobe sem saber qual é o seu diretório de trabalho: ele é informado com o argumento `--workdir=<pasta>` ou com a variável de ambiente `SHOPPING_WORKDIR`. O `./gradlew :backend:run` informa esta pasta (outra: `-Pworkdir=/caminho`). Tudo o que o serviço lê e grava em disco sai dela:

| Pasta | Para quê | No git |
|---|---|---|
| `config/` | A configuração e os arquivos que a apoiam: `application.toml` (local), `application.example.toml` (o modelo, com todas as chaves) e `logback.xml` (o log) | os padrões, sim; o `application.toml`, não |
| `data/` | Dados locais reaproveitados entre reinícios — o banco H2 padrão | não |
| `log/` | O arquivo de log e as suas rotações, conforme `config/logback.xml` | não |
| `tmp/` | Arquivos temporários gerados durante a execução | não |
| `deployment/` | Cada subpasta é um contexto de recursos estáticos que o servidor publica (`compose/` → `/compose/`). Os `deploy.sh` dos frontends web copiam para cá | só o esqueleto |

Para começar: copie `config/application.example.toml` para `config/application.toml` e ajuste. Sem esse arquivo o backend sobe com os padrões — banco H2 em `data/` e **sem segurança**.

O diretório precisa existir; as subpastas que faltarem são criadas na subida.
