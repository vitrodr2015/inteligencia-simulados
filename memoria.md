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
- Erros (Tarefa 04): formato único `ErrorResponse` (`dataHora`, `status`, `erro`, `codigo`, `mensagem`; `campos` e `detalhes` somem do JSON quando nulos), produzido só pelo `GlobalExceptionHandler` (estende `ResponseEntityExceptionHandler`, tudo em português e sempre em JSON, mesmo com `Accept: application/xml`). `ApiException` é a base das exceções de negócio (`ResourceNotFoundException` 404, `InvalidRequestException` 400, `ConflictException` 409). Os códigos de negócio (`MATERIA_NOME_DUPLICADO` etc.) entram no `CodigoErro` junto com cada recurso. O 500 é sempre genérico e a pilha vai só para o log. 401 e 403 nascem nos filtros de segurança e ficam para o Módulo 10.
- Clock: bean `Clock` em UTC em `shared.config` (`ClockConfig`), usado para o `dataHora` do erro e para a auditoria; os testes fixam o Clock.
- Auditoria do `Usuario`: `@CreatedDate`/`@LastModifiedDate` do Spring Data (`AuditoriaConfig`, com `@EnableJpaAuditing` e um `DateTimeProvider` sobre o `Clock`), no lugar de `@PrePersist`/`@PreUpdate`; o `public_id` é gerado no construtor. O `DateTimeProvider` trunca o instante para milissegundos (ver o achado do `DATETIME(3)` abaixo). **Todo `@DataJpaTest` que persista entidade com auditoria precisa de `@Import(AuditoriaConfig.class)` e de um Clock fixo** (`@TestConfiguration`), porque os testes de fatia (`@DataJpaTest`, `@WebMvcTest`) não carregam classes `@Configuration`.
- `spring.web.resources.add-mappings: false`: rota inexistente vira 404 no formato padrão em qualquer método. Sem isso, um POST em rota inexistente viraria 405, porque o tratador de arquivos estáticos só aceita GET e HEAD. O Swagger foi conferido na Tarefa 05 (springdoc 3.2.0): a interface responde 200 em `/swagger-ui/index.html` (e `/swagger-ui.html` redireciona), porque o springdoc registra os próprios mapeamentos; o `add-mappings: false` não atrapalha.
- Locale fixo: `spring.web.locale: pt-BR` com `locale-resolver: fixed`; as mensagens do Bean Validation saem sempre em português, ignorando o `Accept-Language`.
- Matéria (Tarefa 05) e padrão de entidade: `V2__create_table_materia.sql` (`id` BIGINT interno, `public_id BINARY(16)` único, `nome` com índice único e collation `utf8mb4_unicode_ci`, `descricao`, `ativo` BOOLEAN default TRUE, `criado_em`/`atualizado_em` DATETIME(3)); o Hibernate validou `binary(16)` e `tinyint(1)` com `ddl-auto: validate`. Padrão das entidades (`Usuario`, `Materia`): `public_id` gerado no construtor, `@EntityListeners(AuditingEntityListener.class)` com `@CreatedDate`/`@LastModifiedDate` (Clock truncado em milissegundos), `@Column` com nome explícito, sem setters (métodos de negócio). Service com `@Transactional(readOnly = true)` na classe e `@Transactional` nas escritas; mapper escrito à mão; o `DELETE` é atômico, numa instrução só (`@Modifying` por `public_id`, 0 linhas = 404), nunca ler e depois apagar; nome duplicado = checagem prévia e também a violação do índice único (corrida), ambos 409 `MATERIA_NOME_DUPLICADO`.
- Achado do `DATETIME(3)` no MySQL real: o `Instant.now()` tem precisão menor que 1 ms; a API trunca ao serializar, mas o MySQL ARREDONDA ao gravar em `DATETIME(3)`, então o POST devolvia `.850` e o GET seguinte `.851`. Corrigido truncando para milissegundos na `AuditoriaConfig` (com `AuditoriaConfigTest`). O H2 dos testes não pega isso porque o esquema dele é gerado pelo Hibernate (`create-drop`, timestamp com precisão maior), não pelo `DATETIME(3)` da migration; só o MySQL real arredonda. Regra: toda data gravada em `DATETIME(3)` nasce truncada em milissegundos.
- `JacksonConfig` (formato global de datas, sem anotar campo a campo): `Instant` sai SEMPRE em UTC com exatamente 3 casas de milissegundos, truncando, inclusive quando são zero (serializador próprio, só na saída; a leitura de `Instant` segue o padrão do Jackson); `LocalDate` é lida de forma estrita em `uuuu-MM-dd` (`withConfigOverride` com `lenient=false`): `2000-02-30`, `2001-02-29` e `2000-2-3` dão 400 (`CORPO_ILEGIVEL`), nunca corrigidas em silêncio. O padrão de datas do Java (resolução SMART) corrige `02-30` para `02-29`, por isso o `lenient=false`. Mecanismo: `JsonMapperBuilderCustomizer` (`org.springframework.boot.jackson.autoconfigure`) com `SimpleModule`; testado com o `JsonMapper` real, por MockMvc e por `curl` no banco real.
- Rotas: o caminho `/api/v1/...` fica explícito em cada controller (`@RequestMapping("/api/v1/materias")`). Um prefixo global por pacote quebraria os controllers falsos dos testes (que já têm `/api/v1`) e deixaria dúvida sobre o que fica fora (actuator, Swagger, `/error`).
- Swagger: `springdoc-openapi-starter-webmvc-ui` 3.2.0, com versão explícita no `pom.xml` (não é gerida pelo Boot); confirmada com o Boot 4.2.0 subindo a aplicação (`/v3/api-docs` 200 com a Matéria, UI 200). Anotações na interface `MateriaControllerDocs`, que o controller implementa (o controller fica só com a lógica). Documenta só o que o código devolve (200, 201, 204, 400, 404, 409; sem 401, 403 nem 500), verificado no documento gerado. Desligado por padrão (`SWAGGER_ENABLED`; sem ela o `/v3/api-docs` dá 404 no formato padrão). Sem `example` em parâmetros de filtro: o "Try it out" enviaria o valor do exemplo como filtro.
- Testes (Tarefa 05): o H2 de teste usa nome único por contexto (`jdbc:h2:mem:etec_teste_${random.uuid}`), porque vários `@SpringBootTest` com `create-drop` no mesmo banco em memória se atropelariam. Os testes web usam `@SpringBootTest` com `@AutoConfigureMockMvc` (`org.springframework.boot.webmvc.test.autoconfigure`) e não usam `@Transactional` (limpam com `deleteAll`), para o teste de concorrência (`@RepeatedTest(5)`, 12 DELETE simultâneos) enxergar dados confirmados. O `@WebMvcTest` do `GlobalExceptionHandlerTest` lista os controllers falsos (`controllers = {...}`); sem isso ele carrega o `MateriaController` real e falha por falta do `MateriaService`. O springdoc fica desligado no `application.yml` de teste; os testes do Swagger ligam por propriedade.
- Contrato da API da fase 1: `etec/docs/contrato-api-fase1.md` (rascunho v0.7, decisões D1 a D56).

## Estado

- Na `main`: esqueleto Spring Boot 4.2.0 / Java 25, CI, Dependabot, `.gitignore` único, configuração com validação de ambiente (PR #4), documentação e estrutura de pacotes (PR #5) e as Actions atualizadas (PRs #1 a #3 do Dependabot, já mergeados).
- Na `develop`: Tarefa 03 (banco e migrations, PR #6) e Tarefa 04 (camadas e erros padronizados, PR #7).
- Tarefa 05 (CRUD de Matéria): em PR para a `develop`, ainda sem merge.
- `./mvnw verify` passa (119 testes na branch da Tarefa 05), inclusive sem `DB_PASSWORD`/`JWT_SECRET` no ambiente, como no CI.
- Dependências: `webmvc`, `devtools`, `validation`, `data-jpa`, `flyway` (+ `flyway-mysql`), `mysql-connector-j`, `springdoc-openapi-starter-webmvc-ui` 3.2.0; de teste: `webmvc-test`, `data-jpa-test` e `h2`. Ainda sem segurança nem Testcontainers.
- Pacotes por domínio com `package-info.java`; as entidades são `Usuario` (migration V1) e `Materia` (migration V2); `shared.exception` tem o `GlobalExceptionHandler` e as exceções de negócio; o único recurso de negócio é a Matéria (`MateriaController`/`MateriaService`, CRUD sob `/api/v1/materias`, sem listagem); ainda não há autenticação.
- Existe a branch `develop` (criada a partir da `main`); o ruleset `proteger-main` cobre só a `main`, então a `develop` ainda não está protegida (Tarefa 01).
- Banco local do projeto: contêiner `mysql-etec` (mysql:8.4, volume `mysql-etec-data`, utf8mb4/`utf8mb4_unicode_ci`) na porta **3308**; o `DB_URL` do `config/application-local.yml` aponta para ela. O `mysql-dev` (porta 3307) é de outro uso e a senha de root dele não é a deste projeto; não mexer nele nem no volume `mysql-dev-data`. O `application.yml` ainda tem 3307 como URL padrão.
- Precedência de configuração: variável de ambiente do sistema vence o arquivo local. `DB_PASSWORD` e `JWT_SECRET` já existem no Windows desta máquina (possivelmente de outro projeto) e vencem o `application-local.yml`. Não alterar nem remover variáveis do Windows; ao rodar a aplicação ou os testes com o perfil local, removê-las só do processo (`env -u` no Bash, `Remove-Item Env:` no PowerShell).
- Dependabot: PRs #1 (checkout 4→7), #2 (setup-java 4→6) e #3 (upload-artifact 4→7) mergeados, com CI verde; o passo de upload só roda quando o build falha, então o CI não o exercita.

## Pendências

- Matéria, fora da Tarefa 05: a listagem `GET /materias` (Módulo 8), qualquer 401/403 e o Spring Security (Módulos 10 e 11) e o 409 do `DELETE` por subtópicos (a tabela `subtopico` ainda não existe; a chave estrangeira vai virar 409).
- Pontos que ficaram para o contrato (da Tarefa 05):
  - `Location` relativo (`/api/v1/materias/{id}`) e o corpo do 201 com a matéria completa;
  - nome: espaços das pontas removidos antes da validação; ausente = "O nome é obrigatório.", vazio, curto ou longo = "O nome deve ter de 2 a 100 caracteres."; decidir se `campos` traz um ou dois textos;
  - data inválida no corpo sai como `CORPO_ILEGIVEL` genérico, sem dizer qual campo; para o front mostrar o erro no campo, talvez convenha um código próprio;
  - `PUT` omitindo `descricao` zera a descrição (semântica de PUT);
  - `disable`/`enable` idempotentes: o segundo pedido não altera `atualizadoEm`;
  - o 409 do `DELETE` por subtópicos entra quando a tabela existir;
  - o arredondamento do `DATETIME(3)` e o truncamento em milissegundos na origem;
  - a unicidade sem distinguir maiúsculas e acentos foi provada só manualmente no MySQL real; a automação é do Módulo 7.
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
