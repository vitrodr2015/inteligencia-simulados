# Coleções do Postman

Uma coleção por recurso, atualizada **a cada endpoint novo** (parte do "pronto" de cada tarefa).

| Coleção | Recurso |
|---|---|
| `auth.postman_collection.json` | Autenticação e autorização (`/api/v1/auth/*`, `/api/v1/me`, `/api/v1/termos/vigente`): cadastro com todos os 400, signin e as falhas com o mesmo 401, refresh, `GET /me`, token ruim, o negar por padrão e o que o STUDENT não pode (D113, D121, D122), o firewall (D124) e o CORS (D123; rode a API com `CORS_ALLOWED_ORIGINS=https://app.exemplo.invalid`). |
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
- As coleções da Matéria e do Subtópico usam **duas identidades**: o **ADMIN** faz tudo (é o `Authorization` da coleção, `{{adminToken}}`) e um **STUDENT** descartável faz a pasta "Como STUDENT" (`{{studentToken}}`). O STUDENT se cadastra sozinho por script (`postman.*@exemplo.invalid`); o ADMIN NÃO se cadastra por API (não há criação de ADMIN até o Módulo 12), então você informa `adminEmail` e `adminSenha` (variáveis da coleção, **vazias no arquivo**) depois de promover um usuário por SQL (passo abaixo). Sem elas, a coleção para com uma mensagem dizendo o que fazer. Os usuários descartáveis ficam no banco local, porque ainda não há `DELETE /me`.
- As coleções saem de chamadas reais à aplicação; o relatório com as respostas de cada rodada fica em `etec/docs/`.

## Preparar o ADMIN (passo a passo, uma vez, no Windows)
O ADMIN não se cadastra pela API (a criação de ADMIN só chega no Módulo 12), então você promove um usuário por SQL e diz à coleção quem ele é. A senha do banco **não aparece e não é pedida**: ela já está dentro do contêiner `mysql-etec`.

1. Suba a API e o banco (veja o `README.md` da raiz); confira que o contêiner está ligado: `docker start mysql-etec`.
2. Cadastre o usuário: no Postman, abra a coleção **Autenticação**, pasta **Cadastro**, e rode "Cadastrar aluno". Ele usa um e-mail e uma senha gerados na hora; para escolher os seus, troque `{{email}}` e `{{senha}}` no corpo da requisição por um e-mail seu (por exemplo `admin.teste@exemplo.invalid`) e uma senha de 8 a 72 caracteres que só você conheça. Anote os dois.
3. Promova esse e-mail a ADMIN, no **PowerShell** (troque o e-mail pelo seu):

```powershell
"UPDATE usuario SET papel = 'ADMIN' WHERE email = 'admin.teste@exemplo.invalid'; SELECT ROW_COUNT() AS linhas_alteradas;" | docker exec -i mysql-etec sh -c 'mysql -uroot -p$MYSQL_ROOT_PASSWORD etec'
```

   Deve aparecer `linhas_alteradas` com **1** (se for **0**, o e-mail não existe). O aviso `Using a password on the command line interface can be insecure` é normal. Para conferir: `"SELECT email, papel FROM usuario;" | docker exec -i mysql-etec sh -c 'mysql -uroot -p$MYSQL_ROOT_PASSWORD etec'`.
4. No Postman, abra a coleção **Matéria** (e depois a do **Subtópico**), clique no nome dela, aba **Variables**, e preencha na coluna **Current value** (não na *Initial value*, que é a que vai para o arquivo): `adminEmail` com o e-mail do passo 3 e `adminSenha` com a senha que você anotou. Clique em **Save**. **Nunca grave esses valores no arquivo da coleção nem no Git.**
5. Rode a coleção. O papel é lido do banco a cada requisição, então não precisa cadastrar de novo. Se aparecer "Defina as variáveis adminEmail e adminSenha", o passo 4 não foi salvo. Para desfazer, repita o passo 3 com `papel = 'STUDENT'`.
