# Relatório: chamadas reais à autenticação (Módulo 10)

Executado em 2026-11-22, a partir da branch `feature/10-autenticacao` (criada na `develop`, `2e622cc`, ainda sem commit). **44 de 44 chamadas conformes** com o esperado, mais a medição de tempo (30 chamadas por caso) e a execução das três coleções do Postman (47 + 75 + 97 requisições).

## Ambiente

- Aplicação: `java -jar target/etec-0.0.1-SNAPSHOT.jar` na porta **8080**, perfil `local`. `DB_PASSWORD` e `JWT_SECRET` do Windows foram removidos só do processo (`env -u`); a senha do banco e o segredo do JWT vieram do `config/application-local.yml` (ignorado pelo Git). `TERMOS_VERSAO=v1` e `TERMOS_URL` foram passados como variáveis do processo (não têm valor padrão).
- Banco: MySQL 8.4 real, contêiner `mysql-etec` (porta 3308), migrations V1 a V3 já aplicadas (nenhuma migration nova neste módulo). As tabelas `usuario`, `materia` e `subtopico` estavam vazias no início e foram esvaziadas no fim (os usuários e a matéria criados aqui foram apagados direto no banco).
- Cliente: Node v26.13.1 (`fetch`), que envia os acentos em UTF-8 sem converter. As respostas abaixo são as devolvidas de verdade; nada foi simulado.
- Sem segredos neste relatório: os tokens aparecem como `<jwt>` e a senha de teste como `<senha>`. O log da aplicação foi conferido: nenhuma ocorrência da senha de teste nem de um token.
- Sem Swagger (desligado por padrão), o que também é provado abaixo: `/v3/api-docs` dá 401 e não expõe a documentação.

## Resumo

| # | Chamada | Esperado | Obtido | Resultado |
|---|---|---|---|---|
| 1 | GET termos vigentes (público) | 200 | 200 | ✅ |
| 2 | POST register (201, e-mail em maiúsculas normalizado) | 201 | 201 | ✅ |
| 3 | POST register repetido (409 EMAIL_DUPLICADO) | 409 | 409 | ✅ |
| 4 | POST register com e-mail acentuado (400 campos[email]) | 400 | 400 | ✅ |
| 5 | POST register com data inexistente (400, mensagem da D60) | 400 | 400 | ✅ |
| 6 | POST register com senha de 73 bytes (400 campos[senha]) | 400 | 400 | ✅ |
| 7 | POST register com termos antigos (400 campos[aceiteTermosVersao]) | 400 | 400 | ✅ |
| 8 | POST signin (200, no-store) | 200 | 200 | ✅ |
| 9 | GET /me com access token (200) | 200 | 200 | ✅ |
| 10 | POST refresh (200, no-store, par novo) | 200 | 200 | ✅ |
| 11 | GET /me sem token (401 NAO_AUTENTICADO) | 401 | 401 | ✅ |
| 12 | GET /materias sem token (401) | 401 | 401 | ✅ |
| 13 | GET /materias com token (200) | 200 | 200 | ✅ |
| 14 | POST /materias com token (201) | 201 | 201 | ✅ |
| 15 | POST /materias sem token (401) | 401 | 401 | ✅ |
| 16 | GET /me com token adulterado (401) | 401 | 401 | ✅ |
| 17 | GET /me com refresh como Bearer (401) | 401 | 401 | ✅ |
| 18 | POST refresh com access token (401 TOKEN_INVALIDO) | 401 | 401 | ✅ |
| 19 | POST refresh com texto vazio (401) | 401 | 401 | ✅ |
| 20 | POST refresh ausente (400 campos) | 400 | 400 | ✅ |
| 21 | GET /me com alg=none (401) | 401 | 401 | ✅ |
| 22 | GET /me com papel editado sem reassinar (401) | 401 | 401 | ✅ |
| 23 | POST signin com Bearer lixo no cabeçalho (rota pública, 200) | 200 | 200 | ✅ |
| 24 | GET /api/v1/nao-existe sem token (401, D116) | 401 | 401 | ✅ |
| 25 | GET /api/v1/nao-existe com token (404, D116) | 404 | 404 | ✅ |
| 26 | GET /actuator/env sem token (401, D113/D120) | 401 | 401 | ✅ |
| 27 | GET /actuator/env com token (403, D113/D120) | 403 | 403 | ✅ |
| 28 | GET /error sem token (401) | 401 | 401 | ✅ |
| 29 | GET /api/v1/auth/signin (401: permitAll é por método) | 401 | 401 | ✅ |
| 30 | GET /me com Accept XML e sem token (401 em JSON) | 401 | 401 | ✅ |
| 31 | GET /v3/api-docs sem token (401: Swagger desligado cai no negar por padrão) | 401 | 401 | ✅ |
| 32 | POST signin sem corpo válido (400 CORPO_ILEGIVEL) | 400 | 400 | ✅ |
| 33 | POST signin com campo ausente (400 campos) | 400 | 400 | ✅ |
| 34 | POST signin com senha errada (401) | 401 | 401 | ✅ |
| 35 | POST signin com usuário inexistente (401) | 401 | 401 | ✅ |
| 36 | POST signin com e-mail de formato inválido (401) | 401 | 401 | ✅ |
| 37 | POST signin com senha vazia (401) | 401 | 401 | ✅ |
| 38 | POST signin com senha de 73 bytes (401) | 401 | 401 | ✅ |
| 39 | (preparo) POST register de uma conta a excluir | 201 | 201 | ✅ |
| 40 | (preparo) POST signin da conta a excluir | 200 | 200 | ✅ |
| 41 | GET /me antes de excluir (200) | 200 | 200 | ✅ |
| 42 | GET /me depois de excluir no banco (401, mesmo token) | 401 | 401 | ✅ |
| 43 | POST refresh depois de excluir (401) | 401 | 401 | ✅ |
| 44 | POST signin da conta excluída (401) | 401 | 401 | ✅ |

## Respostas escolhidas

**POST register (201, e-mail em maiúsculas normalizado)** — `POST /api/v1/auth/register` → **201** (Cache-Control: `no-cache, no-store, max-age=0, must-revalidate`)

```json
{"id":"5f009b99-ac35-4a26-8f55-d5f44376fde3","nome":"Ana Chamadas","email":"chamadas.mws0362t@exemplo.invalid","dataNascimento":"2008-05-10","papel":"STUDENT","menor":false,"criadoEm":"2026-11-22T15:37:26.040Z"}
```

**POST signin (200, no-store)** — `POST /api/v1/auth/signin` → **200** (Cache-Control: `no-store`)

```json
{"accessToken":"<jwt>","refreshToken":"<jwt>","tokenType":"Bearer","expiresIn":900}
```

**GET /me com access token (200)** — `GET /api/v1/me` → **200** (Cache-Control: `no-cache, no-store, max-age=0, must-revalidate`)

```json
{"id":"5f009b99-ac35-4a26-8f55-d5f44376fde3","nome":"Ana Chamadas","email":"chamadas.mws0362t@exemplo.invalid","dataNascimento":"2008-05-10","papel":"STUDENT","menor":false,"criadoEm":"2026-11-22T15:37:26.040Z"}
```

**POST refresh (200, no-store, par novo)** — `POST /api/v1/auth/refresh` → **200** (Cache-Control: `no-store`)

```json
{"accessToken":"<jwt>","refreshToken":"<jwt>","tokenType":"Bearer","expiresIn":900}
```

**GET /me sem token (401 NAO_AUTENTICADO)** — `GET /api/v1/me` → **401** (Cache-Control: `no-cache, no-store, max-age=0, must-revalidate`)

```json
{"dataHora":"2026-11-22T15:37:26.390Z","status":401,"erro":"Unauthorized","codigo":"NAO_AUTENTICADO","mensagem":"É necessário estar autenticado para acessar este recurso."}
```

**POST refresh com access token (401 TOKEN_INVALIDO)** — `POST /api/v1/auth/refresh` → **401** (Cache-Control: `no-cache, no-store, max-age=0, must-revalidate`)

```json
{"dataHora":"2026-11-22T15:37:26.527Z","status":401,"erro":"Unauthorized","codigo":"TOKEN_INVALIDO","mensagem":"Token inválido ou expirado."}
```

**GET /actuator/env com token (403, D113/D120)** — `GET /actuator/env` → **403** (Cache-Control: `no-cache, no-store, max-age=0, must-revalidate`)

```json
{"dataHora":"2026-11-22T15:37:26.663Z","status":403,"erro":"Forbidden","codigo":"ACESSO_NEGADO","mensagem":"Você não tem permissão para acessar este recurso."}
```

**GET /api/v1/nao-existe com token (404, D116)** — `GET /api/v1/nao-existe` → **404** (Cache-Control: `no-cache, no-store, max-age=0, must-revalidate`)

```json
{"dataHora":"2026-11-22T15:37:26.651Z","status":404,"erro":"Not Found","codigo":"RECURSO_NAO_ENCONTRADO","mensagem":"Recurso não encontrado."}
```

**POST register com data inexistente (400, mensagem da D60)** — `POST /api/v1/auth/register` → **400** (Cache-Control: `no-cache, no-store, max-age=0, must-revalidate`)

```json
{"dataHora":"2026-11-22T15:37:26.152Z","status":400,"erro":"Bad Request","codigo":"VALIDACAO_FALHOU","mensagem":"Há campos inválidos na requisição.","campos":[{"campo":"dataNascimento","mensagem":"Data inválida. Use o formato aaaa-mm-dd."}]}
```

**POST register com e-mail acentuado (400 campos[email])** — `POST /api/v1/auth/register` → **400** (Cache-Control: `no-cache, no-store, max-age=0, must-revalidate`)

```json
{"dataHora":"2026-11-22T15:37:26.138Z","status":400,"erro":"Bad Request","codigo":"VALIDACAO_FALHOU","mensagem":"Há campos inválidos na requisição.","campos":[{"campo":"email","mensagem":"Informe um e-mail válido, só com caracteres ASCII."}]}
```

**POST signin com senha errada (401)** — `POST /api/v1/auth/signin` → **401** (Cache-Control: `no-cache, no-store, max-age=0, must-revalidate`)

```json
{"dataHora":"2026-11-22T15:37:26.759Z","status":401,"erro":"Unauthorized","codigo":"CREDENCIAIS_INVALIDAS","mensagem":"E-mail ou senha inválidos."}
```

Os cinco 401 de signin (senha errada, usuário inexistente, e-mail de formato inválido, senha vazia e senha de 73 bytes) e o da conta excluída têm **exatamente o mesmo corpo** (comparados sem o `dataHora`): **1 corpo distinto**. O hash gravado começa com `{bcrypt}$2a$` e tem 68 caracteres (prefixo `{bcrypt}` + 60).

## Medição de tempo do signin (30 chamadas por caso, intercaladas, depois de 5 de aquecimento)

| Caso | n | mín (ms) | p25 | **mediana** | p75 | máx | média | desvio padrão |
|---|---|---|---|---|---|---|---|---|
| usuário inexistente | 30 | 73.6 | 78.1 | **82.2** | 91.5 | 104.7 | 84.2 | 7.9 |
| senha errada | 30 | 77 | 79.4 | **81.5** | 86.9 | 95.6 | 83 | 4.8 |
| conta excluída | 30 | 75.2 | 78.8 | **81.6** | 85.4 | 104.3 | 82.9 | 6.5 |

A mediana dos três casos fica entre 81.5 e 82.2 ms, dentro da dispersão de cada um (desvio padrão de 4.8 a 7.9 ms). Usuário inexistente, senha errada e conta excluída passam todos por UMA comparação de BCrypt, com um hash fictício quando não há usuário válido, e por isso o tempo não revela qual foi a falha. A medição é por rede local, com um cliente só; serve de evidência, não de garantia estatística.

## Coleções do Postman

As três coleções foram executadas contra a aplicação por um executor mínimo que roda os scripts de pré-requisição e os testes (`pm.test`) de cada requisição na ordem da coleção (o Postman e o newman não estão instalados nesta máquina):

| Coleção | Requisições | Conformes |
|---|---|---|
| `auth.postman_collection.json` | 47 | 47 |
| `materia.postman_collection.json` | 75 | 75 |
| `subtopico.postman_collection.json` | 97 | 97 |

As coleções da Matéria e do Subtópico agora exigem autenticação: um script de pré-requisição da coleção cadastra um usuário descartável, entra e guarda o token em `{{accessToken}}` (renova perto de vencer). Os usuários `postman.*` e `auth.*` criados ficam no banco local (não há `DELETE /me` ainda) e foram apagados no fim.

## O que a prova mostra

- Cadastro, signin, `GET /me` e refresh funcionam de ponta a ponta no MySQL real, com o `no-store` exato no signin e no refresh.
- Sem token, token adulterado, refresh usado como Bearer, `alg=none` e papel editado sem reassinar dão 401 no `ErrorResponse`; a rota pública de signin ignora um Bearer ruim.
- Excluir o usuário no banco (`excluido_em`) derruba o mesmo token na requisição seguinte, o refresh e o signin.
- `/api/v1/nao-existe`: 401 sem token e 404 com token (D116). `/actuator/env` e `/error`: 401 sem token e 403 com token (D113, D120). `GET /api/v1/auth/signin` sem token dá 401 (o `permitAll` é por método).
- As rotas de Matéria dão 401 sem token e 200 ou 201 com token.
