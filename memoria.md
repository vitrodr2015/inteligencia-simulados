# memoria.md

Memória do projeto: regras, decisões, estado e pendências. Reler "Estado" e "Pendências" a cada passo.
Nunca colocar valor sensível aqui; usar marcadores (`<senha>`, `<IP>`).

Última atualização: 2026-11-20

## Regras (resumo; a fonte é o `CLAUDE.md`)

- Um passo por vez; plano antes de codar.
- Prova antes de "pronto" (teste passando ou resposta real).
- Nada de segredo no repositório, em log ou em chat.
- Trabalho por branch e PR; nunca commitar na `main`.
- Ação irreversível (merge na `main`, deploy, infraestrutura) só com confirmação explícita, que não vale para a próxima.
- Commit/push só quando o usuário pedir (regra atual do `CLAUDE.md`).
- Sem dependência nova nem antecipar escopo sem avisar e justificar.

## Decisões

- Repositório **público** (necessário para as regras de proteção valerem na conta gratuita).
- Aprovação: o responsável único é o dono do projeto (projeto individual, 0 aprovações exigidas no PR).
- Arquitetura: monólito modular; pacote base `br.simulado.etec`; camadas por domínio: `controller`, `service`, `repository`, `model`, `dto`, `mapper`; `shared`: `config`, `exception`, `security`.
- CI: GitHub Actions (`.github/workflows/ci.yml`) roda `./mvnw -B -ntp verify` em PR e em push na `main`, Java 25.
- Dependabot semanal para Maven (`/etec`) e GitHub Actions.
- Proteção da `main` (ruleset `proteger-main`, ativo): PR obrigatório, check **Build e testes** obrigatório com a branch atualizada, sem force push, sem deleção. Confirmado por push direto recusado.
- Merge de PR: squash.
- CD: adiado; destino de deploy em aberto. Parte de nuvem (AWS) adiada.
- Um único `.gitignore`, na raiz, com padrões de segredo (`.env*`, `application-local.*`, `*.pem`, `*.key`...).
- Configuração: `application.yml` com segredos `${VAR}` sem padrão (`DB_PASSWORD`, `JWT_SECRET`). `ValidacaoDeAmbiente` (listener no `main`) recusa subir sem eles, com `JWT_SECRET` abaixo de 32 caracteres ou com espaço/quebra de linha nas pontas; a mensagem nunca mostra o valor. Teste de higiene lê o `application.yml` real e reprova segredo com valor padrão.
- Configuração local: copiar `etec/config/application-local.yml.example` para `etec/config/application-local.yml` (ignorado) e rodar com `SPRING_PROFILES_ACTIVE=local`.
- Banco: collation `utf8mb4_unicode_ci` (charset `utf8mb4`), declarada na própria migration de cada tabela. Motivo: o contrato 4.1 exige nome de matéria único ignorando maiúsculas e acentos, e o D29 define o filtro `nome` como "contém" também sem distinguir maiúsculas e acentos; uma collation `_ci` faz as duas coisas no próprio banco (índice único e `LIKE`). Consequência: o e-mail também é único sem distinguir maiúsculas. O H2 dos testes rápidos não se comporta igual; isso só é provado contra MySQL real (Módulo 7).
- Banco: nenhum seed em migration (dados de exemplo só no `data.sql` dos testes); tabelas no singular, em português, snake_case, sem acento; colunas de data `criado_em`/`atualizado_em`/`excluido_em` (o `CLAUDE.md` foi alinhado a isso).
- Banco: `public_id` como `BINARY(16)` (o Hibernate valida sem anotações extras e o índice único fica menor; para ver como UUID no SQL, `BIN_TO_UUID()`). Starter de teste de JPA no Boot 4: `spring-boot-starter-data-jpa-test`.
- Contrato da API da fase 1: `etec/docs/contrato-api-fase1.md` (rascunho v0.7, decisões D1 a D56).

## Estado

- Na `main`: esqueleto Spring Boot 4.2.0 / Java 25, CI, Dependabot, `.gitignore` único, configuração com validação de ambiente (PR #4), documentação e estrutura de pacotes (PR #5) e as Actions atualizadas (PRs #1 a #3 do Dependabot, já mergeados).
- Tarefa 03 (banco e migrations): em PR para a `develop`, ainda sem merge.
- `./mvnw verify` passa (23 testes na branch da Tarefa 03), inclusive sem `DB_PASSWORD`/`JWT_SECRET` no ambiente, como no CI.
- Dependências (Tarefa 03): `webmvc`, `devtools`, `data-jpa`, `flyway` (+ `flyway-mysql`), `mysql-connector-j`; de teste: `webmvc-test`, `data-jpa-test` e `h2`. Ainda sem segurança nem Testcontainers.
- Pacotes por domínio com `package-info.java`; a única entidade é `Usuario` (tabela `usuario`, migration V1) com `UsuarioRepository`; não há controllers nem services.
- Existe a branch `develop` (criada a partir da `main`); o ruleset `proteger-main` cobre só a `main`, então a `develop` ainda não está protegida (Tarefa 01).
- Banco local do projeto: contêiner `mysql-etec` (mysql:8.4, volume `mysql-etec-data`, utf8mb4/`utf8mb4_unicode_ci`) na porta **3308**; o `DB_URL` do `config/application-local.yml` aponta para ela. O `mysql-dev` (porta 3307) é de outro uso e a senha de root dele não é a deste projeto; não mexer nele nem no volume `mysql-dev-data`. O `application.yml` ainda tem 3307 como URL padrão.
- Precedência de configuração: variável de ambiente do sistema vence o arquivo local. `DB_PASSWORD` e `JWT_SECRET` já existem no Windows desta máquina (possivelmente de outro projeto) e vencem o `application-local.yml`. Não alterar nem remover variáveis do Windows; ao rodar a aplicação ou os testes com o perfil local, removê-las só do processo (`env -u` no Bash, `Remove-Item Env:` no PowerShell).
- Dependabot: PRs #1 (checkout 4→7), #2 (setup-java 4→6) e #3 (upload-artifact 4→7) mergeados, com CI verde; o passo de upload só roda quando o build falha, então o CI não o exercita.

## Pendências

- Tarefa 01: Git Flow (`develop`, `feature/*`, `release/X.Y.Z`); CI cobrindo PR para `develop` e `release/**`; limpar o `pom.xml` (name, description, blocos vazios `licenses`, `developers`, `scm`).
- Tarefa 00: sabotagem pendente (sessão nova do Claude Code, sem a conversa, pede para continuar; anotar as lacunas). `PassoAPasso.md` é citado no `CLAUDE.md` e na tarefa, mas foi decidido não criar por enquanto.
- Dúvida a decidir: a tarefa 00 sugere "commit em branch de feature, sim"; o `CLAUDE.md` exige pedido do usuário para commit.
- Contrato, externas: D22 (questão anulada) e D24 (menores/LGPD, idade mínima e consentimento do responsável, com apoio jurídico).
- Contrato, pontos em aberto da última revisão (sem decisão ainda):
  - reuso de refresh revogado por logout conta como "já invalidado" e derrubaria todas as sessões;
  - gabarito diferente numa reimportação é descartado em silêncio (o hash não o inclui);
  - hash imutável: reimportar o conteúdo corrigido dá 409 para sempre, e o original conta como ignorado;
  - `numero` errado em questão já publicada não tem correção;
  - valor de `finalizadaEm` numa tentativa EXPIRADA;
  - `email` em `CAMPO_IMUTAVEL` deve ser comparado após a normalização em minúsculas;
  - o 413 precisa sair no formato `ErrorResponse`;
  - `CAMPO_IMUTAVEL` exige carregar o recurso, então é avaliado depois do 404.
- Contrato: nova rodada de sabotagem (sessão nova lê só o contrato e lista dúvidas) e aprovação, antes de qualquer endpoint.
- `ValidacaoDeAmbiente`: a regra de produção do admin (`ADMIN_EMAIL`/`ADMIN_SENHA`) entra na tarefa do admin inicial; "CORS nunca `*`" ainda não é validado em código; a falha de subida sai como stack trace (sem `FailureAnalyzer`).
- Variáveis `DB_PASSWORD` e `JWT_SECRET` já existem no ambiente do Windows desta máquina; ao testar "máquina limpa", removê-las só do processo filho.
- Com o devtools, `spring-boot:run` pode manter o processo vivo depois de falha de inicialização; para ver o código de saída, rodar o jar.
- Aviso do Mockito (agent dinâmico) no JDK 25: só aviso por enquanto.
- `duvida-CICD.md` fica para a fase de nuvem.
