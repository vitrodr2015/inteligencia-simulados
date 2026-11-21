# Coleções do Postman

Uma coleção por recurso, atualizada **a cada endpoint novo** (parte do "pronto" de cada tarefa).

| Coleção | Recurso |
|---|---|
| `materia.postman_collection.json` | Matéria (`/api/v1/materias`): POST, GET (por id e listagem paginada, com ordenação e filtros), PUT, PATCH disable/enable, DELETE e os erros 400, 404, 405, 409 e 415 |

## Como usar
1. No Postman: `Import` → escolha o arquivo `.postman_collection.json`.
2. A variável `baseUrl` já vem como `http://localhost:8080`; ajuste se a aplicação estiver em outra porta.
3. Rode **na ordem** (Collection Runner ou uma a uma). Os `POST` guardam os ids nas variáveis da coleção (`materiaId` etc.) por script de teste, e cada coleção apaga o que criou no fim, para poder rodar de novo.
4. Para subir a aplicação no banco local: `SPRING_PROFILES_ACTIVE=local` (veja o `README.md` da raiz, seção "Banco local").

## Convenções
- Nome do arquivo: `<recurso>.postman_collection.json`.
- Requisições agrupadas em pastas por verbo, com a pasta "Limpeza" no fim.
- Toda requisição tem um teste de status (`pm.test`), então o Runner mostra o que passou.
- Sem segredos nas coleções. Quando entrar autenticação, o token vai numa variável (`{{accessToken}}`), nunca no arquivo.
- As coleções saem de chamadas reais à aplicação; o relatório com as respostas de cada rodada fica em `etec/docs/`.
