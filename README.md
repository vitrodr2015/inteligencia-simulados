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

**Rodando a aplicação:** ela lê `DB_PASSWORD` (a mesma senha do `MYSQL_ROOT_PASSWORD`), `JWT_SECRET` (no mínimo 32 bytes; é tratado como texto UTF-8, não como Base64), `TERMOS_VERSAO` e `TERMOS_URL` (versão, com até 20 caracteres, e endereço dos termos de uso; sem valor padrão, e a aplicação não sobe sem eles) e `DB_URL`. Copie `etec/config/application-local.yml.example` para `etec/config/application-local.yml` (ignorado pelo Git), preencha e acrescente a linha `DB_URL: jdbc:mysql://localhost:3308/etec?createDatabaseIfNotExist=true`. Rode, a partir de `etec/`, com `SPRING_PROFILES_ACTIVE=local`. As migrations do Flyway são aplicadas na subida.

**Atenção à precedência:** uma variável de ambiente do sistema tem precedência sobre o arquivo local. Se `DB_PASSWORD`, `JWT_SECRET` ou `DB_URL` já existirem no seu Windows (talvez de outro projeto), elas vencem o `application-local.yml` e a aplicação usará os valores do sistema, com erro de acesso ou de configuração. Não apague variáveis do sistema para resolver: confira com `echo $env:DB_PASSWORD` (sem divulgar o valor) e, se precisar, remova a variável só da sessão atual (`Remove-Item Env:DB_PASSWORD`) antes de subir a aplicação.

## Autenticação e autorização (Módulos 10 e 11) e aviso de segurança

> **Rode só em `localhost` até o Módulo 17.** Os Módulos 13A e 13B fecharam o limite de tentativas (429), o refresh guardado e rotacionado, o logout e a troca de senha. Ainda NÃO existem HTTPS e HSTS, o proxy confiável atrás de um balanceador nem o deploy (Módulo 17). Não exponha esta versão na rede, em nuvem nem em nenhum ambiente compartilhado.

**Sessões e tokens (Módulo 13B, D133 a D139).** O access token vale 15 minutos e o refresh, 24 horas, e **cada refresh vale uma vez só**:

- `POST /api/v1/auth/refresh` troca o refresh por um par novo e revoga o usado (a tabela `refresh_token` guarda só o `jti`, nunca o token). **Reapresentar um refresh que já foi trocado é tratado como roubo**: todas as sessões do usuário são encerradas (todos os refresh revogados e os access tokens vivos derrubados) e a resposta é 401. Refresh vencido, desconhecido ou revogado por logout dá só 401.
- `POST /api/v1/auth/logout` (sem Bearer, com `{refreshToken}` no corpo) responde 204 para qualquer texto e revoga só aquele refresh. **O access token vivo continua valendo até vencer (no máximo 15 minutos).**
- `POST /api/v1/me/senha` (com Bearer, corpo `{senhaAtual, novaSenha}`) responde 204 sem tokens. Depois dela, **todos** os tokens antigos (access e refresh) deixam de valer: é preciso entrar de novo com a senha nova. ADMIN usa a política de 12 a 72 bytes (sem espaço nas pontas e sem texto de exemplo); STUDENT, de 8 a 72. Errar a senha atual 5 vezes em 15 minutos dá 429 (só para a troca daquele usuário; o login não é afetado).
- A versão do token (`usuario.versao_token`, claim `versao`) é o que derruba os tokens de uma vez. Tokens emitidos antes do Módulo 13B não têm esse claim e deixam de valer: todos entram de novo.
- Risco conhecido do PWA: se a resposta de um refresh se perde e o cliente repete o mesmo token, ou duas abas renovam ao mesmo tempo, o segundo pedido parece um reuso e desloga o usuário de todos os aparelhos. A rever na sprint do front.
- A limpeza dos refresh vencidos há mais de 1 dia roda de hora em hora (`app.sessoes.limpeza-ativa`). Para encerrar tudo à mão em desenvolvimento: `"UPDATE usuario SET versao_token = versao_token + 1 WHERE email = 'seu@email'; SELECT ROW_COUNT();" | docker exec -i mysql-etec sh -c 'mysql -uroot -p$MYSQL_ROOT_PASSWORD etec'`.

**Limite de tentativas (429, D128 a D131).** As rotas `POST /api/v1/auth/register`, `/signin`, `/refresh` e `/logout` respondem **429 `LIMITE_EXCEDIDO`** com o cabeçalho `Retry-After` (segundos) quando passam do limite. Há três contadores, configuráveis em `app.rate-limit.*` no `application.yml`: toda requisição POST dessas rotas por origem (120 por minuto), falhas de signin por e-mail e origem (5 por 15 minutos) e falhas de signin por origem (50 por 15 minutos). O bloqueio **expira sozinho** (nunca é permanente) e vale para e-mail existente e inexistente do mesmo jeito. Os contadores ficam na tabela `limite_tentativa` (só um hash, nunca o e-mail nem o IP), então valem para todas as instâncias. Em desenvolvimento, para destravar sem esperar: `"DELETE FROM limite_tentativa; SELECT ROW_COUNT() AS linhas_removidas;" | docker exec -i mysql-etec sh -c 'mysql -uroot -p$MYSQL_ROOT_PASSWORD etec'`.

**Atrás de um balanceador (Módulo 17).** A origem é o endereço do socket, e o `X-Forwarded-For` é **ignorado**. Sem configurar o proxy, todos os usuários atrás do balanceador dividem o mesmo limite (em produção a aplicação só registra um aviso). Para o IP real valer, defina as duas variáveis de ambiente, com a lista **explícita** dos proxies (nunca `.*`; em produção, lista vazia ou permissiva derruba a subida): `SERVER_TOMCAT_REMOTEIP_REMOTE_IP_HEADER=X-Forwarded-For` e `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES=<expressão regular dos IPs dos proxies>`.
>
> **O ADMIN nasce na subida, não por API.** O primeiro administrador é criado pelo bootstrap a partir de `ADMIN_EMAIL` e `ADMIN_SENHA` (passo abaixo, "Administrador inicial"). Sem ele, ninguém consegue criar matéria nem subtópico pela API.

| Rota | Quem pode |
|---|---|
| `POST /api/v1/auth/register`, `/signin`, `/refresh`; `GET` e `HEAD /api/v1/termos/vigente` | qualquer um |
| `GET` e `HEAD /api/v1/me` | STUDENT e ADMIN |
| `GET` e `HEAD` de `/api/v1/materias` e `/api/v1/subtopicos` (lista e por id) | STUDENT e ADMIN (o STUDENT só vê ativos) |
| `POST`, `PUT`, `PATCH` (disable e enable) e `DELETE` de matérias e subtópicos | só ADMIN |
| `GET` e `HEAD` de `/api/v1/provas` e `/api/v1/provas/{id}` | STUDENT e ADMIN (o STUDENT só vê as publicadas, num DTO próprio; rascunho dá 404; D189 a D191) |
| `POST /api/v1/admin/provas` | só ADMIN |
| `POST /api/v1/admin/provas/{id}/questoes`; `GET` e `HEAD` de `/api/v1/admin/questoes` e `/api/v1/admin/questoes/{id}` | só ADMIN |
| `POST /api/v1/admin/importacoes` (importação em lote) | só ADMIN |
| `PUT /api/v1/admin/provas/{id}`, `PUT /api/v1/admin/questoes/{id}` e `PUT /api/v1/admin/provas/{id}/gabarito` (edição do rascunho) | só ADMIN |
| `PATCH /api/v1/provas/{id}/publish` (publicar a prova e as questões) | só ADMIN |
| `POST /api/v1/admin/questoes/{id}/versoes` (nova versão) e `PATCH /api/v1/admin/questoes/{id}/publish` (publicar a questão) | só ADMIN |
| `DELETE /api/v1/admin/questoes/{id}` e `DELETE /api/v1/admin/provas/{id}` | só ADMIN |
| GUARDIAN | nenhuma permissão: 403 em tudo que exige papel, inclusive `/me` |
| todo o resto, inclusive rota ou método que não existe | negado: 401 sem token e 403 com token (até para o ADMIN) |

O token vai em `Authorization: Bearer <accessToken>`. O papel que vale é o do **banco** (o claim do token é só informativo): trocar o papel de alguém no banco vale na requisição seguinte, mesmo com o token antigo, e excluir o usuário (`excluido_em`) derruba o token. A autorização vem antes da existência: quem não pode recebe 403 até para um id que não existe. As regras ficam todas na `SecurityConfig`; cada recurso novo traz a sua regra e as suas linhas na `MatrizDeAutorizacao` (a varredura de rotas quebra o build se faltar).

**Administrador inicial (Módulo 12, D45 e D126).** A API não cria ADMIN por rota: o primeiro administrador nasce **na subida**, a partir de duas variáveis de ambiente, `ADMIN_EMAIL` e `ADMIN_SENHA`. As duas vêm juntas ou nenhuma. A senha tem de 12 a 72 bytes (UTF-8), sem espaço no começo ou no fim e sem quebra de linha. Quem digita a senha é **você**, no terminal, sem eco, sem arquivo e sem colar de lugar nenhum. No **PowerShell**, na pasta `etec/`, **antes** de subir a aplicação:

```powershell
$email = Read-Host "E-mail do administrador"
$seguro = Read-Host "Senha do administrador (12 a 72 caracteres)" -AsSecureString
$env:ADMIN_EMAIL = $email
$env:ADMIN_SENHA = [System.Net.NetworkCredential]::new("", $seguro).Password
```

Suba a aplicação na mesma janela (`./mvnw spring-boot:run`, com o perfil `local` como no começo deste arquivo). No log aparece **`Administrador inicial criado.`** (nunca o e-mail nem a senha). **Depois da primeira subida, tire a senha do ambiente:**

```powershell
Remove-Item Env:ADMIN_SENHA, Env:ADMIN_EMAIL
```

Regras que não mudam:

- O bootstrap **só cria** se não existir nenhum ADMIN ativo (`excluido_em` nulo). Se já existe, as variáveis são **ignoradas**, inclusive para trocar a senha: subir de novo com outra `ADMIN_SENHA` não muda nada.
- **Nunca promove** um usuário que já existe: se o e-mail de `ADMIN_EMAIL` for de um aluno cadastrado, a subida registra o erro (em produção, não sobe) e o aluno continua aluno. Use outro e-mail.
- Sem variáveis e sem admin: em desenvolvimento, só um aviso no log (ninguém consegue entrar como ADMIN); **em produção (perfil `prod`) a aplicação não sobe.** Variável inválida também derruba a subida em produção, com a lista do que está errado e nunca o valor.
- Nada de senha, hash ou e-mail do admin vai para o log.
- Limitação conhecida (revisitar no Módulo 17): duas subidas **simultâneas** com `ADMIN_EMAIL` **diferentes** podem criar dois administradores. Com o mesmo e-mail, só um nasce.

**Esqueci a senha do admin.** Não há rota nem variável que troque a senha (o bootstrap ignora as variáveis quando já existe admin). Em **ambiente controlado** (o seu banco local, ou o servidor com acesso restrito), remova o administrador por SQL e suba de novo com as variáveis do passo acima:

```powershell
"DELETE FROM usuario WHERE papel = 'ADMIN'; SELECT ROW_COUNT() AS linhas_removidas;" | docker exec -i mysql-etec sh -c 'mysql -uroot -p$MYSQL_ROOT_PASSWORD etec'
```

Deve aparecer `linhas_removidas` com **1**. O aviso `Using a password on the command line interface can be insecure` é do próprio MySQL e é normal (a senha do banco não está na sua linha de comando). Em produção, faça isso só com backup e com a janela de manutenção combinada.

**Só em desenvolvimento, alternativa por SQL.** Se preferir não usar as variáveis, cadastre um usuário pela API (`POST /api/v1/auth/register`) e promova o e-mail dele:

```powershell
"UPDATE usuario SET papel = 'ADMIN' WHERE email = 'admin.teste@exemplo.invalid'; SELECT ROW_COUNT() AS linhas_alteradas;" | docker exec -i mysql-etec sh -c 'mysql -uroot -p$MYSQL_ROOT_PASSWORD etec'
```

Deve aparecer `linhas_alteradas` com **1** (se for **0**, o e-mail não existe). O papel é lido do banco a cada requisição, então não precisa entrar de novo. Para conferir quem é ADMIN: `"SELECT email, papel FROM usuario;" | docker exec -i mysql-etec sh -c 'mysql -uroot -p$MYSQL_ROOT_PASSWORD etec'`. Nunca grave e-mail e senha de ADMIN em arquivo do repositório.

**Entitlements (Tarefa 12, D140 a D142).** Todo endpoint que será premium passa pelo `EntitlementsService` (pacote `assinatura`), que consulta uma política substituível escolhida por `app.entitlements.politica` (hoje só `stub`, sem valor padrão: ausente ou desconhecida derruba a subida). O stub libera `INICIAR_TENTATIVA` no programa ETEC para STUDENT e ADMIN e nega o GUARDIAN; o que não está na lista dele é negado. Nenhum endpoint premium existe ainda, e `GET /entitlements` não existe na fase 1. A negação é `403 ENTITLEMENT_NEGADO`. **Antes de cobrar, o stub tem de ser trocado por regras reais.**

**Programa de prova (Sprint 2, D143 e D144).** A migration V7 cria a tabela `programa` com uma linha, `ETEC`, e a matéria passa a ter `programa_id` (obrigatório): o nome da matéria é único **por programa**. O programa é interno: não aparece em rota, corpo nem resposta, e o código dele casa com `Programa.ETEC` (pacote `shared.programa`). Se você já tem um banco local com matérias, a V7 as coloca no ETEC sozinha ao subir.

**Leitura das provas pelo aluno (Sprint 3, fatia 1; D188 a D191).** O STUDENT lê `GET /api/v1/provas` e `GET /api/v1/provas/{id}`, mas só as provas **publicadas**, com os mesmos filtros (`ano`, `semestre`), ordenação e paginação do ADMIN; enviar `status` dá 400, e uma prova em rascunho dá o mesmo 404 de uma que não existe. Ele recebe um DTO próprio (`id`, `titulo`, `banca`, `ano`, `semestre`, `duracaoMinutos`, `quantidadeQuestoes`, `fonteUrl`, `publicadaEm`). O ADMIN não mudou e o GUARDIAN segue com 403. Ainda não há rota de questões para o aluno: elas virão com a tentativa.

**Prova (Sprint 2, fatia 2a; D149 a D151).** A migration V8 cria a tabela `prova`: `public_id` (UUID exposto), `programa_id` (obrigatório, chave estrangeira com `RESTRICT`; o programa é interno e não aparece na API), `titulo` (até 150), `banca` (até 80, opcional), `ano` (2000 a 2100, opcional), `semestre` (1 ou 2, opcional), `duracao_minutos` (1 a 600), `quantidade_questoes` (1 a 300, **declarada** na criação), `fonte_url` (https, opcional), `status` (`RASCUNHO` ou `PUBLICADA`; toda prova nasce em `RASCUNHO`) e `publicada_em` (nulo no rascunho; o banco exige `status = PUBLICADA` se e somente se `publicada_em` está preenchida). Os limites são `CHECK` no banco e validação na API. A unicidade por programa + banca + ano + semestre **só vale com os três preenchidos** (no MySQL, `NULL` não conflita num índice único); a banca compara ignorando caixa e acento. Rotas (a criação é só do ADMIN; a leitura do aluno está no parágrafo acima; o filtro `status` é só do ADMIN): `POST /api/v1/admin/provas`, `GET /api/v1/provas` (filtros `ano`, `semestre`, `status`; ordenação por `ano` ou `semestre`, padrão ano decrescente; as provas sem ano ficam no fim no decrescente e no início no crescente) e `GET /api/v1/provas/{id}`. Os campos inteiros do corpo não aceitam texto nem decimal (`"ano":"2024"` dá 400). A coleção do Postman é `postman/prova.postman_collection.json`.

**Questão (Sprint 2, fatia 2b-i; D145, D146, D152 a D158).** Três tabelas. `questao` (migration V9) é a **identidade**: `public_id` (UUID exposto, estável entre versões), `prova_id` (FK `RESTRICT`), `numero` (1 a 300, único na prova), `tipo` (interno, só `MULTIPLA_ESCOLHA`, invisível na API) e `hash_conteudo` (SHA-256 do conteúdo, gerado no cadastro e nunca alterado; o programa vem da prova). `questao_versao` (V10) é o **conteúdo**: `versao`, `status` (`RASCUNHO`, `PUBLICADA` ou `ARQUIVADA`), `subtopico_id` (FK `RESTRICT`: o subtópico é da versão, então um subtópico com questão em qualquer estado não pode ser apagado, 409), `texto_apoio`, `enunciado` e `explicacao` (`TEXT`, até 10.000 caracteres por `CHECK`), `imagem_url`, `grupo_apoio`, `gabarito` (A a E ou nulo) e `dificuldade`. Duas colunas **geradas** (`rascunho_aberto` e `publicada_vigente`) com índice único garantem, no banco, no máximo um rascunho e uma publicada por questão (qualquer número de arquivadas). `alternativa` (V10): 5 por versão, letra A a E única, texto de até 2000 caracteres. O cadastro é `POST /api/v1/admin/provas/{id}/questoes` (201; **200** com a questão já gravada se o `numero` já tem a MESMA questão; 409 `QUESTAO_NUMERO_EM_CONFLITO` se o conteúdo for diferente); a prova precisa estar em rascunho e o `numero` vai de 1 até a `quantidadeQuestoes` dela. Os textos só aceitam `<b>`, `<u>`, `<i>`, `<sup>`, `<sub>` e `<br>` (e `&lt;`, `&gt;`, `&amp;`). `imagemUrl` só aceita https de um host de `IMAGENS_HOSTS_PERMITIDOS` (propriedade `app.imagens.hosts-permitidos`; padrão `localhost`, **defina em produção**; lista vazia não aceita imagem). UUID de rota só vale no formato 8-4-4-4-12 (id de grupo curto dá 400).

**Listagem de questões e contador (Sprint 2, fatia 2b-ii; D160 a D162).** `GET /api/v1/admin/questoes` (só ADMIN) traz, de cada questão, só a versão mais recente (o rascunho aberto, senão a última publicada), com filtros `provaId`, `subtopicoId` e `status` (`RASCUNHO` ou `PUBLICADA`) que valem para essa versão; um id de filtro que não existe devolve lista vazia. A ordem é sempre `numero` crescente (qualquer outro `sort` dá 400). São duas consultas fixas por página (os ids paginados e a carga com tudo), sem N+1. A prova passou a expor `questoesCadastradas`: quantas questões (não versões) já foram cadastradas nela, contadas por uma única consulta agrupada. Coleção do Postman: `postman/questao.postman_collection.json`.

**Importação em lote (Sprint 2, fatia 3; D163 a D169).** `POST /api/v1/admin/importacoes` (só ADMIN) recebe `{ "provaId", "questoes": [...] }`, com cada questão nos mesmos campos e regras do cadastro unitário. A prova precisa **já existir** e estar em rascunho: a importação não a cria nem a altera. É **tudo ou nada**, numa transação com a prova travada; de 1 a 100 questões e corpo de até **2 MiB** (acima disso, 413 `CORPO_MUITO_GRANDE`: divida o lote). Responde 200 com `questoesCriadas` e `questoesIgnoradas` (as que já existiam com o mesmo conteúdo não são gravadas de novo, mesmo que o subtópico tenha sido desativado depois). Os erros apontam o item (`questoes[i].campo`, ou `detalhes.indice` e `detalhes.numero`), e os conflitos de conteúdo no numero vêm todos juntos. Não há migration nem tabela de importação. **Configuração:** `server.tomcat.max-swallow-size: 20MB` é necessária para o cliente receber o 413 em vez de ver a conexão cair. Coleção do Postman: `postman/importacao.postman_collection.json`.

**Edição do rascunho e gabarito em lote (Sprint 2, fatia 4, passada 1; D170 a D177).** Três `PUT`, só do ADMIN. `PUT /api/v1/admin/provas/{id}` troca a prova inteira (o opcional omitido vira nulo), só em RASCUNHO, e não deixa a `quantidadeQuestoes` ficar abaixo do maior número cadastrado. `PUT /api/v1/admin/questoes/{id}` troca o conteúdo do **rascunho aberto** (sem rascunho, 409 `QUESTAO_SEM_RASCUNHO`); **omitir o gabarito o apaga**, `numero` e `provaId` não mudam, e o subtópico só precisa estar ativo quando é trocado. `PUT /api/v1/admin/provas/{id}/gabarito` recebe `{ "itens": [ {"numero": 1, "gabarito": "C"} ] }` (1 a 100 itens), é tudo ou nada e devolve `{ "atualizadas": n }`, contando só as que mudaram. O hash da questão não muda com o PUT. Toda escrita trava a linha da prova; se a espera pela trava estourar, ou houver deadlock, a resposta é **503 `RECURSO_OCUPADO`** com `Retry-After: 5` (vale para qualquer rota) e quem chamou pode repetir. Publicar, abrir versão nova e apagar estão descritos abaixo.

**Publicar a prova (Sprint 2, fatia 4, passada 2a; D178 a D180).** `PATCH /api/v1/provas/{id}/publish` (só ADMIN, sem corpo) publica a prova e **todas as questões** dela numa transação só: ou tudo, ou nada. A prova precisa estar em RASCUNHO (publicar de novo dá 409 `PROVA_NAO_EDITAVEL`) e ter as questões de 1 a `quantidadeQuestoes`, cada uma com gabarito, subtópico ativo (de matéria ativa) e imagem de um host permitido **hoje**. Se faltar algo, 409 `PROVA_COM_QUESTOES_PENDENTES` com **todas** as pendências em `detalhes.questoesPendentes` (`{ "numero": 3, "motivo": "SEM_GABARITO" }`). **Publicar não tem volta:** depois, o que se corrige é a questão, por uma versão nova. A migration V11 passa a exigir, no banco, que só versão publicada ou arquivada tenha data de publicação.

**Nova versão e publicação de questão (Sprint 2, fatia 4, passada 2b; D181 a D183).** Para corrigir uma questão de prova publicada, em três passos, só do ADMIN: (1) `POST /api/v1/admin/questoes/{id}/versoes` (sem corpo, 201) abre um rascunho com a cópia completa da versão publicada, que continua valendo; com rascunho já aberto, 409 `QUESTAO_COM_RASCUNHO`. (2) `PUT /api/v1/admin/questoes/{id}` corrige o rascunho. (3) `PATCH /api/v1/admin/questoes/{id}/publish` (sem corpo, 200) publica o rascunho e **arquiva** a versão anterior, na mesma transação. Só vale em prova publicada (senão 409 `PROVA_NAO_PUBLICADA`), exige rascunho aberto (409 `QUESTAO_SEM_RASCUNHO`) e aplica as mesmas regras da publicação da prova; com pendência, 409 `QUESTAO_COM_PENDENCIAS`, com o mesmo corpo. A versão arquivada nunca mais muda, não aparece na listagem e se lê por `GET /api/v1/admin/questoes/{id}?versao=n`. Um rascunho aberto por engano se descarta com o `DELETE` da questão.

**Apagar (Sprint 2, fatia 4, passada 3; D184 a D187).** Duas rotas, só do ADMIN, sem corpo, com resposta 204. Só rascunho se apaga: versão publicada ou arquivada fica para sempre. `DELETE /api/v1/admin/questoes/{id}`: a questão **nunca publicada** (em prova em rascunho) some inteira e o número fica livre, que é como se corrige um conteúdo importado errado (apagar, corrigir e reimportar); da questão **já publicada** some só o rascunho aberto, e a publicada continua valendo (sem rascunho, 409 `QUESTAO_SEM_RASCUNHO`). `DELETE /api/v1/admin/provas/{id}`: só em rascunho (409 `PROVA_NAO_EDITAVEL`); apaga a prova e todas as questões dela, ou nada. Apagar não tem volta.

**CORS.** A variável `CORS_ALLOWED_ORIGINS` (propriedade `app.cors.allowed-origins`) lista as origens de navegador permitidas, separadas por vírgula, no formato `http(s)://domínio[:porta]` (sem barra nem caminho). **Vazia (o padrão) = sem CORS.** A aplicação não sobe com `*` nem com origem fora do formato, e, com o perfil `prod`, só aceita `https`. Exemplo para um front local: `CORS_ALLOWED_ORIGINS=http://localhost:5173`. Origem, método ou cabeçalho não permitido recebe 403 `ACESSO_NEGADO`. O front em outra origem consegue ler os cabeçalhos `Location` e `Retry-After` (D180). Os relatórios das chamadas reais estão em `etec/docs/relatorio-chamadas-auth.md` e `etec/docs/relatorio-chamadas-autorizacao.md`.

## Como rodar os testes

Rode a partir de `etec/` (no PowerShell/cmd use `mvnw.cmd` em vez de `./mvnw`).

| O que | Comando | Docker | Tempo aproximado |
|---|---|---|---|
| Tudo (o que o CI roda) | `./mvnw verify` | **precisa estar aberto** | ~1 min 40 s |
| Só os rápidos (H2) | `./mvnw verify "-Dtest=!*IntegracaoTest,!MateriaConcorrenciaTest"` | não precisa | ~30 s |
| Só os de integração (MySQL real) | `./mvnw test "-Dtest=*IntegracaoTest,MateriaConcorrenciaTest"` | **precisa estar aberto** | ~1 min 30 s |

- **Rápidos (H2):** testes unitários, `@WebMvcTest`, `@DataJpaTest` e os `@SpringBootTest` com banco em memória. Não provam o que depende do MySQL (collation, `CHECK`, arredondamento de `DATETIME(3)`, concorrência com índice único).
- **Integração (MySQL real):** classes `*IntegracaoTest` e `MateriaConcorrenciaTest`, que estendem `AbstractIntegrationTest`. Sobem a aplicação inteira em porta aleatória, chamam por HTTP real e usam um contêiner `mysql:8.4` com as migrations de verdade (Flyway) e `ddl-auto: validate`.
- **Docker:** o Docker Desktop precisa estar aberto para os de integração. Sem ele, eles **falham** (não são ignorados). Na primeira vez o Docker baixa a imagem `mysql:8.4` (cerca de 1 GB).
- **Um contêiner só:** ele sobe uma vez por execução, é compartilhado por todas as classes de integração e é removido sozinho no fim. Os testes **não** usam o seu banco local (`mysql-etec`) nem as variáveis `DB_PASSWORD` e `JWT_SECRET` do seu ambiente.
- **Nome da classe:** todo teste de integração termina em `IntegracaoTest` (inclusive os de concorrência, como `SubtopicoConcorrenciaIntegracaoTest`); é esse sufixo que o comando "só os rápidos" usa para deixá-los de fora.
- **Escrevendo um teste de integração novo:** estenda `AbstractIntegrationTest`, crie os próprios dados com nomes únicos (`nomeUnico(...)`), não dependa de ordem e não use `@DirtiesContext` (isso quebraria o reaproveitamento do contexto e deixaria o build lento).
