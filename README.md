# Simulados ETEC — Backend

API REST para simulados online da ETEC. Guia de trabalho em `CLAUDE.md`; estado do projeto em `memoria.md`.

## Banco local

MySQL 8.4 em contêiner Docker. Defina a senha de root na sua sessão (ela nunca vai para o repositório):

```powershell
$env:SENHA_DEV = Read-Host "Senha de dev do MySQL"
```

Crie o contêiner uma vez:

```powershell
docker run -d --name mysql-etec `
  -p 3308:3306 `
  -e MYSQL_DATABASE=etec `
  -e MYSQL_ROOT_PASSWORD=$env:SENHA_DEV `
  -v mysql-etec-data:/var/lib/mysql `
  mysql:8.4 `
  --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
```

Depois disso, só `docker start mysql-etec` / `docker stop mysql-etec`. Se o contêiner já existe, não o recrie: o `MYSQL_ROOT_PASSWORD` só vale na primeira criação do volume, e a tabela `usuario` define a collation `utf8mb4_unicode_ci` na própria migration.

**Porta:** o contêiner publica a **3308** no host. A 3306 costuma estar ocupada pelo serviço MySQL do Windows, e a 3307 é disputada por contêineres de outros projetos. O `application.yml` aponta por padrão para `localhost:3307`, então defina `DB_URL` com a porta que você usou (veja abaixo). Escolha outra porta se a 3308 também estiver ocupada.

**Rodando a aplicação:** ela lê `DB_PASSWORD` (a mesma senha do `MYSQL_ROOT_PASSWORD`), `JWT_SECRET` (mínimo de 32 caracteres) e `DB_URL`. Copie `etec/config/application-local.yml.example` para `etec/config/application-local.yml` (ignorado pelo Git), preencha e acrescente a linha `DB_URL: jdbc:mysql://localhost:3308/etec?createDatabaseIfNotExist=true`. Rode, a partir de `etec/`, com `SPRING_PROFILES_ACTIVE=local`. As migrations do Flyway são aplicadas na subida.

**Atenção à precedência:** uma variável de ambiente do sistema tem precedência sobre o arquivo local. Se `DB_PASSWORD`, `JWT_SECRET` ou `DB_URL` já existirem no seu Windows (talvez de outro projeto), elas vencem o `application-local.yml` e a aplicação usará os valores do sistema, com erro de acesso ou de configuração. Não apague variáveis do sistema para resolver: confira com `echo $env:DB_PASSWORD` (sem divulgar o valor) e, se precisar, remova a variável só da sessão atual (`Remove-Item Env:DB_PASSWORD`) antes de subir a aplicação.

## Como rodar os testes

Rode a partir de `etec/` (no PowerShell/cmd use `mvnw.cmd` em vez de `./mvnw`).

| O que | Comando | Docker | Tempo aproximado |
|---|---|---|---|
| Tudo (o que o CI roda) | `./mvnw verify` | **precisa estar aberto** | ~1 min 20 s |
| Só os rápidos (H2) | `./mvnw verify "-Dtest=!*IntegracaoTest,!MateriaConcorrenciaTest"` | não precisa | ~30 s |
| Só os de integração (MySQL real) | `./mvnw test "-Dtest=*IntegracaoTest,MateriaConcorrenciaTest"` | **precisa estar aberto** | ~50 s |

- **Rápidos (H2):** testes unitários, `@WebMvcTest`, `@DataJpaTest` e os `@SpringBootTest` com banco em memória. Não provam o que depende do MySQL (collation, `CHECK`, arredondamento de `DATETIME(3)`, concorrência com índice único).
- **Integração (MySQL real):** classes `*IntegracaoTest` e `MateriaConcorrenciaTest`, que estendem `AbstractIntegrationTest`. Sobem a aplicação inteira em porta aleatória, chamam por HTTP real e usam um contêiner `mysql:8.4` com as migrations de verdade (Flyway) e `ddl-auto: validate`.
- **Docker:** o Docker Desktop precisa estar aberto para os de integração. Sem ele, eles **falham** (não são ignorados). Na primeira vez o Docker baixa a imagem `mysql:8.4` (cerca de 1 GB).
- **Um contêiner só:** ele sobe uma vez por execução, é compartilhado por todas as classes de integração e é removido sozinho no fim. Os testes **não** usam o seu banco local (`mysql-etec`) nem as variáveis `DB_PASSWORD` e `JWT_SECRET` do seu ambiente.
- **Escrevendo um teste de integração novo:** estenda `AbstractIntegrationTest`, crie os próprios dados com nomes únicos (`nomeUnico(...)`), não dependa de ordem e não use `@DirtiesContext` (isso quebraria o reaproveitamento do contexto e deixaria o build lento).
