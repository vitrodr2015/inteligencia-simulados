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

> **Rode só em `localhost` até o Módulo 13B.** O Módulo 13A colocou o limite de tentativas (429) no cadastro, no signin, no refresh e no logout, com os contadores no banco. Ainda NÃO existem: o refresh token guardado e invalidado (um refresh roubado vale 24 horas, mesmo depois de usado), o logout e a troca de senha (Módulo 13B), nem HTTPS e HSTS (Módulo 17). Não exponha esta versão na rede, em nuvem nem em nenhum ambiente compartilhado.

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

**CORS.** A variável `CORS_ALLOWED_ORIGINS` (propriedade `app.cors.allowed-origins`) lista as origens de navegador permitidas, separadas por vírgula, no formato `http(s)://domínio[:porta]` (sem barra nem caminho). **Vazia (o padrão) = sem CORS.** A aplicação não sobe com `*` nem com origem fora do formato, e, com o perfil `prod`, só aceita `https`. Exemplo para um front local: `CORS_ALLOWED_ORIGINS=http://localhost:5173`. Origem, método ou cabeçalho não permitido recebe 403 `ACESSO_NEGADO`. Os relatórios das chamadas reais estão em `etec/docs/relatorio-chamadas-auth.md` e `etec/docs/relatorio-chamadas-autorizacao.md`.

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
