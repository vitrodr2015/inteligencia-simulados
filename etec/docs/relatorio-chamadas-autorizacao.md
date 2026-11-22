# Relatório: chamadas reais à autorização e ao CORS (Módulo 11)

Executado em 2026-11-22, a partir da branch `feature/11-autorizacao` (criada na `develop`, `d4f8749`, sem commit). **72 de 72 chamadas conformes** com o esperado, mais a execução das três coleções do Postman (65 + 95 + 123 requisições).

## Ambiente

- Aplicação: `java -jar target/etec-0.0.1-SNAPSHOT.jar` na porta **8080**, perfil `local`, com `CORS_ALLOWED_ORIGINS=https://app.exemplo.invalid,http://localhost:5173`. `DB_PASSWORD` e `JWT_SECRET` do Windows foram removidos só do processo (`env -u`); os valores do banco e do JWT vieram do `config/application-local.yml` (ignorado pelo Git), que **não foi lido** por quem fez esta rodada. `TERMOS_VERSAO` e `TERMOS_URL` foram passados como variáveis do processo.
- Banco: MySQL 8.4 real, contêiner `mysql-etec` (porta 3308), migrations V1 a V3 (nenhuma migration nova). As tabelas `usuario`, `materia` e `subtopico` estavam vazias no início e foram esvaziadas no fim.
- **ADMIN antes do Módulo 12:** ainda não existe criação de ADMIN por API nem bootstrap. O ADMIN (e o GUARDIAN) desta rodada foram cadastrados pela API como alunos e **promovidos por SQL** (`UPDATE usuario SET papel = 'ADMIN'`), executado dentro do contêiner, que lê a própria senha de root: a senha do banco nunca passou pelo script. O mesmo SQL trocou o papel de um usuário em andamento, para provar que vale na requisição seguinte com o token antigo.
- Cliente: Node v26.13.1 (`node:http`, que envia o caminho cru e o cabeçalho `Origin`). Nada foi simulado.
- Sem segredos neste relatório: tokens e senhas de teste não aparecem. O log da aplicação foi conferido: nenhuma ocorrência de senha de teste nem de token.

## Resumo

| # | Chamada | Esperado | Obtido | Resultado |
|---|---|---|---|---|
| 1 | ADMIN cria matéria ativa (201) | 201 | 201 | ✅ |
| 2 | ADMIN cria a matéria que ficará inativa (201) | 201 | 201 | ✅ |
| 3 | ADMIN cria matéria sem subtópicos (201) | 201 | 201 | ✅ |
| 4 | ADMIN cria subtópico ativo em matéria ativa (201) | 201 | 201 | ✅ |
| 5 | ADMIN cria subtópico que ficará inativo (201) | 201 | 201 | ✅ |
| 6 | ADMIN cria subtópico em matéria que ficará inativa (201) | 201 | 201 | ✅ |
| 7 | ADMIN desativa o subtópico (200) | 200 | 200 | ✅ |
| 8 | ADMIN desativa a matéria (200) | 200 | 200 | ✅ |
| 9 | ADMIN altera a matéria (200) | 200 | 200 | ✅ |
| 10 | ADMIN altera o subtópico (200) | 200 | 200 | ✅ |
| 11 | ADMIN busca matéria inexistente (404) | 404 | 404 | ✅ |
| 12 | ADMIN apaga a matéria sem subtópicos (204) | 204 | 204 | ✅ |
| 13 | ADMIN apaga matéria que não existe (404) | 404 | 404 | ✅ |
| 14 | STUDENT lista matérias (200, só ativas) | 200 | 200 | ✅ |
| 15 | ADMIN lista matérias (200, todas) | 200 | 200 | ✅ |
| 16 | STUDENT lista subtópicos (200, só ativos de matéria ativa) | 200 | 200 | ✅ |
| 17 | ADMIN lista subtópicos (200, todos) | 200 | 200 | ✅ |
| 18 | STUDENT envia ativo=false (400 PARAMETRO_INVALIDO) | 400 | 400 | ✅ |
| 19 | ADMIN filtra ativo=false (200) | 200 | 200 | ✅ |
| 20 | STUDENT busca matéria ativa por id (200) | 200 | 200 | ✅ |
| 21 | STUDENT busca matéria inativa por id (404 MATERIA_NAO_ENCONTRADA) | 404 | 404 | ✅ |
| 22 | ADMIN busca matéria inativa por id (200) | 200 | 200 | ✅ |
| 23 | STUDENT busca subtópico inativo (404 SUBTOPICO_NAO_ENCONTRADO) | 404 | 404 | ✅ |
| 24 | STUDENT busca subtópico de matéria inativa (404) | 404 | 404 | ✅ |
| 25 | ADMIN busca subtópico de matéria inativa (200) | 200 | 200 | ✅ |
| 26 | STUDENT filtra subtópicos por matéria inativa (200, lista vazia) | 200 | 200 | ✅ |
| 27 | ADMIN filtra subtópicos por matéria inativa (200, 1 item) | 200 | 200 | ✅ |
| 28 | STUDENT lê o próprio usuário (200) | 200 | 200 | ✅ |
| 29 | STUDENT tenta criar matéria (403) | 403 | 403 | ✅ |
| 30 | STUDENT tenta criar matéria com corpo inválido (403, antes do 400) | 403 | 403 | ✅ |
| 31 | STUDENT tenta alterar matéria que não existe (403, antes do 404) | 403 | 403 | ✅ |
| 32 | STUDENT tenta apagar com id malformado (403, antes do 400) | 403 | 403 | ✅ |
| 33 | STUDENT tenta desativar matéria (403) | 403 | 403 | ✅ |
| 34 | STUDENT tenta criar subtópico (403) | 403 | 403 | ✅ |
| 35 | STUDENT tenta apagar subtópico (403) | 403 | 403 | ✅ |
| 36 | ADMIN altera com id malformado (400) | 400 | 400 | ✅ |
| 37 | HEAD /materias como STUDENT (200) | 200 | 200 | ✅ |
| 38 | HEAD matéria inativa como STUDENT (404) | 404 | 404 | ✅ |
| 39 | HEAD matéria inativa como ADMIN (200) | 200 | 200 | ✅ |
| 40 | HEAD /me como STUDENT (200) | 200 | 200 | ✅ |
| 41 | HEAD /materias sem token (401) | 401 | 401 | ✅ |
| 42 | HEAD /materias como GUARDIAN (403) | 403 | 403 | ✅ |
| 43 | HEAD /termos/vigente sem token (200) | 200 | 200 | ✅ |
| 44 | GUARDIAN lê /me (403) | 403 | 403 | ✅ |
| 45 | GUARDIAN lista matérias (403) | 403 | 403 | ✅ |
| 46 | GUARDIAN cria matéria (403) | 403 | 403 | ✅ |
| 47 | GUARDIAN lê termos vigentes (200, rota pública) | 200 | 200 | ✅ |
| 48 | rota inexistente sem token (401) | 401 | 401 | ✅ |
| 49 | rota inexistente como STUDENT (403) | 403 | 403 | ✅ |
| 50 | rota inexistente como ADMIN (403) | 403 | 403 | ✅ |
| 51 | DELETE na coleção de matérias como ADMIN (403, sem regra) | 403 | 403 | ✅ |
| 52 | PATCH /me como STUDENT (403, sem regra até a tarefa nascer) | 403 | 403 | ✅ |
| 53 | /actuator/env como ADMIN (403) | 403 | 403 | ✅ |
| 54 | antes: STUDENT cria matéria (403) | 403 | 403 | ✅ |
| 55 | SQL promove a ADMIN; mesmo token antigo cria matéria (201) | 201 | 201 | ✅ |
| 56 | SQL rebaixa a STUDENT; mesmo token antigo é barrado (403) | 403 | 403 | ✅ |
| 57 | SQL vira GUARDIAN; mesmo token antigo lê /me (403) | 403 | 403 | ✅ |
| 58 | CORS: preflight de origem permitida (200 com cabeçalhos) | 200 | 200 | ✅ |
| 59 | CORS: preflight da outra origem permitida (200) | 200 | 200 | ✅ |
| 60 | CORS: preflight de origem NÃO permitida (403 ErrorResponse) | 403 | 403 | ✅ |
| 61 | CORS: preflight de cabeçalho fora da lista (403) | 403 | 403 | ✅ |
| 62 | CORS: GET real de origem permitida (200 com cabeçalhos e Location exposto) | 200 | 200 | ✅ |
| 63 | CORS: 401 de origem permitida leva os cabeçalhos | 401 | 401 | ✅ |
| 64 | CORS: 403 (STUDENT escreve) de origem permitida leva os cabeçalhos | 403 | 403 | ✅ |
| 65 | CORS: GET real de origem NÃO permitida (403, sem cabeçalhos) | 403 | 403 | ✅ |
| 66 | CORS: OPTIONS comum sem cabeçalhos de preflight, anônimo (401) | 401 | 401 | ✅ |
| 67 | CORS: sem Origin (cliente que não é navegador) segue normal (200, sem cabeçalhos) | 200 | 200 | ✅ |
| 68 | firewall: barra dupla (400 REQUISICAO_INVALIDA) | 400 | 400 | ✅ |
| 69 | firewall: ;jsessionid (400 REQUISICAO_INVALIDA) | 400 | 400 | ✅ |
| 70 | firewall: %2e%2e (400 REQUISICAO_INVALIDA) | 400 | 400 | ✅ |
| 71 | barra no fim sem regra: anônimo (401) | 401 | 401 | ✅ |
| 72 | barra no fim sem regra: STUDENT (403) | 403 | 403 | ✅ |

## Respostas escolhidas

**STUDENT busca matéria inativa por id (404 MATERIA_NAO_ENCONTRADA)** — `GET /api/v1/materias/<uuid>` → **404** (`application/json`)

```json
{"dataHora":"2026-11-22T17:31:37.764Z","status":404,"erro":"Not Found","codigo":"MATERIA_NAO_ENCONTRADA","mensagem":"Matéria não encontrada."}
```

**STUDENT tenta criar matéria (403)** — `POST /api/v1/materias` → **403** (`application/json`)

```json
{"dataHora":"2026-11-22T17:31:37.875Z","status":403,"erro":"Forbidden","codigo":"ACESSO_NEGADO","mensagem":"Você não tem permissão para acessar este recurso."}
```

**rota inexistente como STUDENT (403)** — `GET /api/v1/nao-existe` → **403** (`application/json`)

```json
{"dataHora":"2026-11-22T17:31:38.059Z","status":403,"erro":"Forbidden","codigo":"ACESSO_NEGADO","mensagem":"Você não tem permissão para acessar este recurso."}
```

**CORS: preflight de origem permitida (200 com cabeçalhos)** — `OPTIONS /api/v1/materias` → **200**
Cabeçalhos de CORS: `{"access-control-allow-origin":"https://app.exemplo.invalid","access-control-allow-methods":"GET,POST,PUT,PATCH,DELETE,OPTIONS","access-control-allow-headers":"authorization, content-type","access-control-max-age":"3600","access-control-expose-headers":"Location"}`

(sem corpo)

**CORS: preflight de origem NÃO permitida (403 ErrorResponse)** — `OPTIONS /api/v1/materias` → **403** (`application/json`)

```json
{"dataHora":"2026-11-22T17:31:38.456Z","status":403,"erro":"Forbidden","codigo":"ACESSO_NEGADO","mensagem":"Você não tem permissão para acessar este recurso."}
```

**CORS: 401 de origem permitida leva os cabeçalhos** — `GET /api/v1/materias` → **401** (`application/json`)
Cabeçalhos de CORS: `{"access-control-allow-origin":"https://app.exemplo.invalid","access-control-expose-headers":"Location"}`

```json
{"dataHora":"2026-11-22T17:31:38.463Z","status":401,"erro":"Unauthorized","codigo":"NAO_AUTENTICADO","mensagem":"É necessário estar autenticado para acessar este recurso."}
```

**CORS: 403 (STUDENT escreve) de origem permitida leva os cabeçalhos** — `POST /api/v1/materias` → **403** (`application/json`)
Cabeçalhos de CORS: `{"access-control-allow-origin":"https://app.exemplo.invalid","access-control-expose-headers":"Location"}`

```json
{"dataHora":"2026-11-22T17:31:38.470Z","status":403,"erro":"Forbidden","codigo":"ACESSO_NEGADO","mensagem":"Você não tem permissão para acessar este recurso."}
```

**CORS: GET real de origem NÃO permitida (403, sem cabeçalhos)** — `GET /api/v1/termos/vigente` → **403** (`application/json`)

```json
{"dataHora":"2026-11-22T17:31:38.471Z","status":403,"erro":"Forbidden","codigo":"ACESSO_NEGADO","mensagem":"Você não tem permissão para acessar este recurso."}
```

**firewall: barra dupla (400 REQUISICAO_INVALIDA)** — `GET /api/v1//materias` → **400** (`application/json`)

```json
{"dataHora":"2026-11-22T17:31:38.478Z","status":400,"erro":"Bad Request","codigo":"REQUISICAO_INVALIDA","mensagem":"A requisição é inválida."}
```

## O que a prova mostra

- **STUDENT lê e não escreve; ADMIN faz tudo.** Toda escrita do STUDENT dá 403 `ACESSO_NEGADO`, **antes** do 400 (corpo inválido, id malformado) e do 404 (id inexistente).
- **Visibilidade (D28, D47, D103).** O STUDENT só vê ativos (subtópico: só o ativo de matéria ativa); `ativo=false` dá 400 `PARAMETRO_INVALIDO`; o inativo por id dá o mesmo 404 do inexistente (`MATERIA_NAO_ENCONTRADA` ou `SUBTOPICO_NAO_ENCONTRADO`); o filtro por `materiaId` de matéria inativa dá lista vazia; o ADMIN vê tudo.
- **HEAD segue o GET** (200, 404, 401 e 403 iguais aos do GET).
- **GUARDIAN não tem nenhuma permissão (D122):** 403 em `/me` e em todo o catálogo; as rotas públicas funcionam.
- **Negar por padrão (D121):** rota inexistente e método sem regra dão 401 ao anônimo e 403 ao autenticado, inclusive ADMIN. O 404 só vem depois da autorização, para recurso inexistente.
- **Papel trocado no banco vale na requisição seguinte, com o token antigo, nos dois sentidos** (STUDENT promovido escreve; rebaixado, é barrado; GUARDIAN perde o `/me`).
- **CORS (D123):** o preflight da origem permitida passa sem autenticação, com os métodos, os cabeçalhos, `Max-Age: 3600` e `Location` exposto, sem credenciais e sem `*`; origem, método ou cabeçalho fora da lista dão 403 `ACESSO_NEGADO` em `ErrorResponse`; os 401 e 403 de origem permitida levam os cabeçalhos de CORS; um OPTIONS comum, sem os cabeçalhos de preflight, é negado.
- **Firewall (D124):** barra dupla, `;jsessionid` e `%2e%2e` dão 400 `REQUISICAO_INVALIDA` em `ErrorResponse`, sem detalhe técnico. A barra no fim não é recusada: não tem regra (401 e 403).
