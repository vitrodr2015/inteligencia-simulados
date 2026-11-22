# CLAUDE.md

Guia para o Claude Code trabalhar neste repositório.

## 1. Visão do projeto

**Simulados ETEC — Backend.** API REST para simulados online da ETEC: o aluno faz uma prova cronometrada, vê a nota e o gabarito.

- Arquitetura: monólito modular.
- Futuro (não antecipar): desempenho por subtópico, planos pagos, responsável, outros exames (ENEM, OAB).
- O processo segue o Playbook2 (módulos, "pronto quando", sabotagem). Parte de nuvem (AWS) adiada.

## 2. Stack

| Item | Situação |
|---|---|
| Java 25, Spring Boot 4.2.0, Maven (`mvnw`) | Atual (ver `etec/pom.xml`) |
| `spring-boot-starter-webmvc`, `devtools`, `webmvc-test` | Atual |
| MySQL 8.4, Flyway, JWT (java-jwt), BCrypt, Spring Security 7, Spring Data 4 | Planejado, ainda não adicionado |
| JUnit 5, Mockito, MockMvc, Testcontainers, REST Assured, springdoc-openapi | Planejado, ainda não adicionado |

- Boot 4 traz Spring 7, Spring Security 7, Spring Data 4 e Jackson 3.
- Dependência entra no módulo que a exige, com aviso e justificativa.

**ATENÇÃO Boot 4:** confirmar versões e pacotes reais antes de usar receitas antigas (não compilam). Exemplos:
- starters `spring-boot-starter-webmvc` e `spring-boot-starter-flyway` (+ `flyway-mysql`);
- Jackson 3 em `tools.jackson` (as anotações continuam em `com.fasterxml.jackson.annotation`);
- `@WebMvcTest` em `org.springframework.boot.webmvc.test.autoconfigure`;
- `PropertyReferenceException` em `org.springframework.data.core`.

## 3. Estrutura do repositório

```
/                         # raiz do repositório
  CLAUDE.md
  .github/                # CI (GitHub Actions) e Dependabot
  etec/                   # projeto Spring Boot
    pom.xml
    src/main/java/br/simulado/etec/     # EtecApplication é o ponto de entrada
    src/main/resources/application.properties
    src/test/java/br/simulado/etec/
```

### Pacotes (organizados por domínio)

Pacote base: `br.simulado.etec`

```
auth, usuarios, catalogo, tentativas, desempenho, assinatura, admin, shared
```

| Domínio | Responsabilidade |
|---|---|
| `auth` | login, refresh, cadastro |
| `usuarios` | perfil, consentimento, papéis |
| `catalogo` | programas, matérias, subtópicos, provas, questões |
| `tentativas` | tentativas, respostas, nota |
| `desempenho` | relatórios (fase 2) |
| `assinatura` | planos, entitlements, pagamento |
| `admin` | importação e revisão de questões |
| `shared` | transversal: `exception`, `config`, `security` |

Cada domínio contém: `controller`, `service`, `repository`, `model`, `dto`, `mapper`.

**Regras de camada**
- O controller só fala HTTP; o service guarda regras e transações.
- Dependência em um sentido só: `controller → service → repository/model`.
- Um domínio nunca acessa o repository de outro; usa o service desse domínio.

## 4. Comandos

Rodar de dentro de `etec/` (no PowerShell/cmd use `mvnw.cmd`):

```bash
./mvnw spring-boot:run     # sobe a aplicação
./mvnw verify              # compila e roda todos os testes (usar antes de concluir tarefa)
./mvnw clean package       # gera o jar em target/
```

## 5. Convenções de código

**API**
- Rotas sob `/api/v1`.
- Recursos expostos por `public_id` (UUID); `id` BIGINT é só interno.
- DTOs de entrada e de saída (records), separados das entidades. A entidade JPA nunca sai pela API.
- Validação com Bean Validation nos DTOs.
- Erros sempre no mesmo formato (`ErrorResponse`). 500 com mensagem genérica; pilha só no log.
- Ordem de validação: corpo (400), id (400), existência (404), conflito (409), alteração.
- Rota nova nasce fechada (negar por padrão).

**Dados**
- Datas em UTC (`Instant`). Relógio injetável (`Clock`) para testar tempo.
- Toda tabela com `criado_em` e `atualizado_em`.
- Nomes de tabela no singular e de coluna em snake_case, em português, sem acento (ex.: `data_nascimento`, `criado_em`).
- Migrations Flyway em `src/main/resources/db/migration`, nome `V{n}__descricao.sql`.
- **NUNCA** editar uma migration já aplicada; corrige-se com uma nova.
- `ddl-auto: validate`, `open-in-view: false`. Nunca `ddl-auto: update`.

**Segurança**
- Segredos só por variável de ambiente, SEM valor padrão; a aplicação não sobe sem eles.
- Nunca logar senha, token, segredo ou dado sensível.

**Geral**
- Mensagens para o usuário em português.

## 6. Testes

- Unitário de service (Mockito) para regras de negócio.
- Web (MockMvc) para contrato.
- Integração com Testcontainers (MySQL real) para fluxo e concorrência. Todo endpoint tem teste de integração.
- Nome do teste pelo cenário: `deve_<resultado>_quando_<condição>`.
- Comando: `./mvnw verify`.

## 7. Regras do produto que não podem quebrar

- Questão só publica com subtópico e **exatamente um** gabarito válido.
- Questão publicada é imutável; editar cria nova versão.
- O cronômetro da prova é do servidor (`started_at`, `deadline_at`); o cliente só exibe.
- Resposta salva é idempotente. Gravar: alternativa, acerto, tempo gasto, horário e versão da questão.
- Endpoints que serão premium passam pelo serviço de entitlements.
- Dados de menores de idade: LGPD (consentimento, exclusão de conta, sem dados sensíveis em logs).

## 8. Princípios do playbook (valem sempre)

1. Prova antes de "pronto": teste passando ou resposta real. Nunca "acho que funciona".
2. Sabotar para confirmar: quebrar de um jeito conhecido, ver o teste falhar, restaurar (arquivo idêntico).
3. Segredo nunca no repositório, nem em log, nem em chat.
4. Negar por padrão: rota nova nasce fechada.
5. A fonte da verdade é o banco, não o token nem o cache.
6. Falhar cedo e com mensagem clara, na subida.
7. Migration aplicada nunca se edita; corrige-se com uma nova.
8. Anotar enquanto faz (`PassoAPasso.md` e `memoria.md`).

## 9. Definição de pronto (todo módulo/tarefa)

1. A prova do "Pronto quando" foi mostrada.
2. A sabotagem foi feita e desfeita.
3. A suíte inteira passa com os segredos fora do ambiente (como no CI).
4. A documentação da API, se mudou, está atualizada.
5. Nada sensível entrou nos arquivos.
6. A tarefa foi anotada.

## 10. Fluxo de trabalho

1. Ler este `CLAUDE.md` e a tarefa.
2. Apresentar o plano antes de codar. Um passo por vez.
3. Implementar em passos pequenos, com testes.
4. Rodar `./mvnw verify`.
5. Conferir os critérios de aceite e a definição de pronto; listar o que ficou pendente.

## 11. O que NÃO fazer

- Sem microsserviços, Redis, filas ou dependências novas sem avisar e justificar.
- Não antecipar escopo de outras tarefas.
- Não alterar migrations antigas nem arquivos fora do escopo da tarefa.
- Não commitar `target/` nem `HELP.md` (já no `.gitignore`).
- Ação irreversível (merge na `main`, deploy, mexer em infraestrutura) só com confirmação explícita do responsável; a confirmação de uma ação não vale para a próxima.

## 12. Git e CI

- Remoto: `https://github.com/vitrodr2015/inteligencia-simulados.git`, branch principal `main`.
- Fluxo: `feature/*` → PR para a `develop`; a `main` só recebe PR de `release/X.Y.Z` (ou hotfix) vindo da `develop`.
- A `main` é protegida (ruleset `proteger-main`): só entra por pull request com o check **Build e testes** verde.
- Nunca commitar direto na `main`/`develop`; sempre por branch e PR.
- Só fazer commit/push quando o usuário pedir.
- CI: `.github/workflows/ci.yml` roda `./mvnw verify` em PRs e em push na `main`. Dependabot semanal (Maven e Actions).
- CD: ainda não definido (destino de deploy em aberto).
