# Coleções do Postman

Uma coleção por recurso, atualizada **a cada endpoint novo** (parte do "pronto" de cada tarefa).

| Coleção | Recurso |
|---|---|
| `auth.postman_collection.json` | Autenticação (`/api/v1/auth/*`, `/api/v1/me`, `/api/v1/termos/vigente`): cadastro com todos os 400, signin e as falhas com o mesmo 401, refresh, `GET /me`, token ruim e o negar por padrão (D113, D116, D120). |
| `materia.postman_collection.json` | Matéria (`/api/v1/materias`): POST, GET (por id e listagem paginada, com ordenação e filtros), PUT, PATCH disable/enable, DELETE e os erros 400, 404, 405, 409 e 415; textos curtos (espaços das pontas, descrição em branco, emoji e pontos de código), sort único (D101) e o 409 do DELETE de matéria com subtópicos |
| `subtopico.postman_collection.json` | Subtópico (`/api/v1/subtopicos`): POST, GET (por id e listagem paginada, com filtros `materiaId`, `nome` e `ativo`), PUT (com `CAMPO_IMUTAVEL`), PATCH disable/enable (idempotência), DELETE, matéria desativada (`MATERIA_INATIVA`, D109), nome duplicado (caixa, acento e espaços), pontos de código e os erros 400, 404, 405, 409 e 415 |

## Como usar
1. No Postman: `Import` → escolha o arquivo `.postman_collection.json`.
2. A variável `baseUrl` já vem como `http://localhost:8080`; ajuste se a aplicação estiver em outra porta.
3. Rode **na ordem** (Collection Runner ou uma a uma). Os `POST` guardam os ids nas variáveis da coleção (`materiaId` etc.) por script de teste, e cada coleção apaga o que criou no fim, para poder rodar de novo. Rode uma coleção por vez, com o banco sem matérias de mesmo nome (a do Subtópico cria Biologia, Inglês, Artes e Filosofia; a da Matéria cria Matemática, Física, Química, História, Geografia e outras).
4. Para subir a aplicação no banco local: `SPRING_PROFILES_ACTIVE=local` (veja o `README.md` da raiz, seção "Banco local").

## Convenções
- Nome do arquivo: `<recurso>.postman_collection.json`.
- Requisições agrupadas em pastas por verbo, com a pasta "Limpeza" no fim.
- Toda requisição tem um teste de status (`pm.test`), então o Runner mostra o que passou.
- Sem segredos nas coleções. O token vai numa variável (`{{accessToken}}`), nunca no arquivo; e-mail e senha de teste são gerados por script a cada rodada.
- As coleções da Matéria e do Subtópico exigem autenticação: um script de pré-requisição cadastra um usuário descartável (`postman.*@exemplo.invalid`), entra e guarda o token (renova perto de vencer). Esses usuários ficam no banco local, porque ainda não há `DELETE /me`; apague-os direto no banco se quiser.
- As coleções saem de chamadas reais à aplicação; o relatório com as respostas de cada rodada fica em `etec/docs/`.
