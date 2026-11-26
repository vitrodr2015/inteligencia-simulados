# Coleções do Postman

Uma coleção por recurso, atualizada **a cada endpoint novo** (parte do "pronto" de cada tarefa).

| Coleção | Recurso |
|---|---|
| `auth.postman_collection.json` | Autenticação e autorização (`/api/v1/auth/*`, `/api/v1/me`, `/api/v1/termos/vigente`): cadastro com todos os 400, signin e as falhas com o mesmo 401, refresh, `GET /me`, token ruim, o negar por padrão e o que o STUDENT não pode (D113, D121, D122), o firewall (D124) e o CORS (D123; rode a API com `CORS_ALLOWED_ORIGINS=https://app.exemplo.invalid`). |
| `materia.postman_collection.json` | Matéria (`/api/v1/materias`): POST, GET (por id e listagem paginada, com ordenação e filtros), PUT, PATCH disable/enable, DELETE e os erros 400, 404, 405, 409 e 415; textos curtos (espaços das pontas, descrição em branco, emoji e pontos de código), sort único (D101) e o 409 do DELETE de matéria com subtópicos |
| `subtopico.postman_collection.json` | Subtópico (`/api/v1/subtopicos`): POST, GET (por id e listagem paginada, com filtros `materiaId`, `nome` e `ativo`), PUT (com `CAMPO_IMUTAVEL`), PATCH disable/enable (idempotência), DELETE, matéria desativada (`MATERIA_INATIVA`, D109), nome duplicado (caixa, acento e espaços), pontos de código e os erros 400, 404, 405, 409 e 415 |
| `prova.postman_collection.json` | Prova (`POST /api/v1/admin/provas`, `GET /api/v1/provas` e `GET /api/v1/provas/{id}`, só ADMIN, D149 a D151 e D154): criar (completa, só com o obrigatório, banca em branco, limites, mass assignment), todos os 400 de validação e de corpo ilegível (inclusive `"ano":"2024"` e decimal), 415, o 409 `PROVA_DUPLICADA` (caixa, acento e espaços; combinações parciais repetidas convivem), listagem (filtros `ano`, `semestre`, `status`, ordenação com os anos nulos, paginação), busca por id (404 `PROVA_NAO_ENCONTRADA`, 400) e a autorização (401, 403 do STUDENT e das rotas que ainda não existem) |

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
- As coleções da Matéria e do Subtópico usam **duas identidades**: o **ADMIN** faz tudo (é o `Authorization` da coleção, `{{adminToken}}`) e um **STUDENT** descartável faz a pasta "Como STUDENT" (`{{studentToken}}`). O STUDENT se cadastra sozinho por script (`postman.*@exemplo.invalid`); o ADMIN NÃO se cadastra por API: ele nasce na subida da aplicação (bootstrap do Módulo 12), então você informa `adminEmail` e `adminSenha` (variáveis da coleção, **vazias no arquivo**) com os valores usados no bootstrap (passo abaixo). Sem elas, a coleção para com uma mensagem dizendo o que fazer. Os usuários descartáveis ficam no banco local, porque ainda não há `DELETE /me`.
- As coleções saem de chamadas reais à aplicação; o relatório com as respostas de cada rodada fica em `etec/docs/`.

## Preparar o ADMIN (passo a passo, uma vez, no Windows)
O ADMIN não se cadastra pela API: ele nasce na **subida da aplicação**, pelo bootstrap (`ADMIN_EMAIL` e `ADMIN_SENHA`, D45 e D126). Quem digita a senha é você, no PowerShell, sem eco e sem arquivo. A senha do banco **não aparece e não é pedida**.

1. Na pasta `etec/`, no **PowerShell**, defina as duas variáveis **na janela em que vai subir a API** (a senha tem de 12 a 72 caracteres):

```powershell
$email = Read-Host "E-mail do administrador"
$seguro = Read-Host "Senha do administrador (12 a 72 caracteres)" -AsSecureString
$env:ADMIN_EMAIL = $email
$env:ADMIN_SENHA = [System.Net.NetworkCredential]::new("", $seguro).Password
```

2. Suba o banco e a API (veja o `README.md` da raiz). No log deve aparecer `Administrador inicial criado.`. Se já existia um admin, a subida o ignora e as variáveis não têm efeito (e **não trocam a senha**); nesse caso use a senha que você já tem, ou veja "Esqueci a senha do admin" no `README.md`.
3. **Depois da primeira subida, tire a senha do ambiente:** `Remove-Item Env:ADMIN_SENHA, Env:ADMIN_EMAIL`.
4. No Postman, abra a coleção **Matéria** (e depois a do **Subtópico**), clique no nome dela, aba **Variables**, e preencha na coluna **Current value** (não na *Initial value*, que é a que vai para o arquivo): `adminEmail` com o e-mail do passo 1 e `adminSenha` com a senha que você digitou. Clique em **Save**. **Nunca grave esses valores no arquivo da coleção nem no Git.**
5. Rode a coleção. O papel é lido do banco a cada requisição. Se aparecer "Defina as variáveis adminEmail e adminSenha", o passo 4 não foi salvo.

Alternativa **só em desenvolvimento**: cadastrar um usuário pela API e promovê-lo por SQL (`UPDATE usuario SET papel = 'ADMIN' WHERE email = '...'`, ver o `README.md`).
