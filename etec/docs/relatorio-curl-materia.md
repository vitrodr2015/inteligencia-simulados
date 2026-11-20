# Relatório: chamadas reais às rotas de Matéria

Executado em 2026-11-20 18:11:36 UTC, a partir da `develop` (`84cf59f`). **39 de 39 chamadas conformes** com o esperado.

## Ambiente

- Aplicação: `java -jar` na porta **8085**, perfil `local` (a coleção usa `{{baseUrl}}`, por padrão `http://localhost:8080`).
- Banco: MySQL 8.4 real, contêiner `mysql-etec` (porta 3308), migrations V1 e V2 aplicadas; tabela `materia` vazia no início e no fim.
- Cliente: `curl 8.21.0` (as respostas abaixo são as devolvidas de verdade; nada foi simulado).
- Sem autenticação (401/403 ficam para os Módulos 10 e 11) e sem Swagger (desligado por padrão).

## Como usar no Postman

**Opção 1 (recomendada): importar a coleção.** `Import` → arquivo `postman/materia.postman_collection.json` (pasta `postman/` na raiz do projeto). As variáveis `materiaId`, `outraMateriaId`, `materiaIdC` e `materiaIdD` são preenchidas sozinhas pelos `POST` (script de teste). Rode a coleção **na ordem** (Collection Runner ou uma a uma): ela cria 4 matérias, exercita cada rota e apaga tudo no fim. Ajuste `baseUrl` se a aplicação não estiver na 8080.

**Opção 2: colar os `curl`.** `Import` → `Raw text` → cole **um** comando por vez. Os comandos usam `{{baseUrl}}` e `{{materiaId}}`: crie essas variáveis no ambiente do Postman e, depois do primeiro `POST`, copie o `id` da resposta para `materiaId`.

## Resumo

| # | Grupo | Chamada | Esperado | Obtido | Resultado |
|---|---|---|---|---|---|
| 1 | POST — criar | Criar matéria (caso feliz) | 201 | 201 | ✅ |
| 2 | POST — criar | Criar com espaços nas pontas do nome | 201 | 201 | ✅ |
| 3 | POST — criar | Criar ignorando id, ativo e datas enviados no corpo (mass assignment) | 201 | 201 | ✅ |
| 4 | POST — criar | Criar sem descrição | 201 | 201 | ✅ |
| 5 | POST — criar | Nome duplicado (igual ao existente) | 409 | 409 | ✅ |
| 6 | POST — criar | Nome duplicado sem acento e em minúscula (collation do MySQL) | 409 | 409 | ✅ |
| 7 | POST — criar | Nome só com espaços | 400 | 400 | ✅ |
| 8 | POST — criar | Nome com 1 caractere | 400 | 400 | ✅ |
| 9 | POST — criar | Sem o campo nome | 400 | 400 | ✅ |
| 10 | POST — criar | Nome com 101 caracteres | 400 | 400 | ✅ |
| 11 | POST — criar | Descrição com 256 caracteres | 400 | 400 | ✅ |
| 12 | POST — criar | Corpo ilegível (JSON quebrado) | 400 | 400 | ✅ |
| 13 | POST — criar | Content-Type não suportado (text/plain) | 415 | 415 | ✅ |
| 14 | GET — buscar | Buscar por id | 200 | 200 | ✅ |
| 15 | GET — buscar | Id inexistente | 404 | 404 | ✅ |
| 16 | GET — buscar | Id malformado (não é UUID) | 400 | 400 | ✅ |
| 17 | GET — buscar | Listagem (ainda não implementada: Módulo 8) | 405 | 405 | ✅ |
| 18 | PUT — atualizar | Trocar nome e descrição (ignora o ativo enviado) | 200 | 200 | ✅ |
| 19 | PUT — atualizar | Descrição omitida: PUT é substituição, vira nula (D61) | 200 | 200 | ✅ |
| 20 | PUT — atualizar | Nome que pertence a outra matéria | 409 | 409 | ✅ |
| 21 | PUT — atualizar | Id inexistente | 404 | 404 | ✅ |
| 22 | PUT — atualizar | Nome inválido (só espaços) | 400 | 400 | ✅ |
| 23 | PUT — atualizar | Id malformado | 400 | 400 | ✅ |
| 24 | PATCH — desativar e ativar | Desativar | 200 | 200 | ✅ |
| 25 | PATCH — desativar e ativar | Desativar de novo (idempotente) | 200 | 200 | ✅ |
| 26 | PATCH — desativar e ativar | Ativar | 200 | 200 | ✅ |
| 27 | PATCH — desativar e ativar | Ativar de novo (idempotente) | 200 | 200 | ✅ |
| 28 | PATCH — desativar e ativar | Desativar id inexistente | 404 | 404 | ✅ |
| 29 | PATCH — desativar e ativar | Ativar id inexistente | 404 | 404 | ✅ |
| 30 | PATCH — desativar e ativar | Id malformado | 400 | 400 | ✅ |
| 31 | DELETE — apagar | Apagar | 204 | 204 | ✅ |
| 32 | DELETE — apagar | Buscar depois de apagar | 404 | 404 | ✅ |
| 33 | DELETE — apagar | Apagar de novo | 404 | 404 | ✅ |
| 34 | DELETE — apagar | Id malformado | 400 | 400 | ✅ |
| 35 | Outras | Rota inexistente | 404 | 404 | ✅ |
| 36 | Outras | Rota inexistente em método com corpo (POST) | 404 | 404 | ✅ |
| 37 | Limpeza | Apagar a matéria Física | 204 | 204 | ✅ |
| 38 | Limpeza | Apagar a matéria Química | 204 | 204 | ✅ |
| 39 | Limpeza | Apagar a matéria História | 204 | 204 | ✅ |

## Detalhe de cada chamada

Os `curl` usam as variáveis do Postman. Os ids nas respostas são os reais desta execução.

### POST — criar

#### 1. Criar matéria (caso feliz) ✅

```bash
curl --location '{{baseUrl}}/api/v1/materias' \
  --header 'Content-Type: application/json' \
  --data-raw '{"nome":"Matemática","descricao":"Álgebra e geometria"}'
```

Esperado **201**, obtido **201**.

```http
HTTP/1.1 201
Location: /api/v1/materias/8fa50f72-706a-43c3-8173-9a87fd036a60
Content-Type: application/json

{
  "id": "8fa50f72-706a-43c3-8173-9a87fd036a60",
  "nome": "Matemática",
  "descricao": "Álgebra e geometria",
  "ativo": true,
  "criadoEm": "2026-11-20T18:11:34.909Z",
  "atualizadoEm": "2026-11-20T18:11:34.909Z"
}
```

Verificações:

- ✅ Location é relativo e aponta para o novo id
- ✅ id é um UUID
- ✅ nasce ativa
- ✅ criadoEm com 3 casas de milissegundos
- ✅ não expõe o id numérico

#### 2. Criar com espaços nas pontas do nome ✅

```bash
curl --location '{{baseUrl}}/api/v1/materias' \
  --header 'Content-Type: application/json' \
  --data-raw '{"nome":"   Física   ","descricao":"Mecânica e óptica"}'
```

Esperado **201**, obtido **201**.

```http
HTTP/1.1 201
Location: /api/v1/materias/25b35b8b-d1ba-46e5-ba9a-ec6fbbcba4c2
Content-Type: application/json

{
  "id": "25b35b8b-d1ba-46e5-ba9a-ec6fbbcba4c2",
  "nome": "Física",
  "descricao": "Mecânica e óptica",
  "ativo": true,
  "criadoEm": "2026-11-20T18:11:34.942Z",
  "atualizadoEm": "2026-11-20T18:11:34.942Z"
}
```

Verificações:

- ✅ nome devolvido sem os espaços das pontas

#### 3. Criar ignorando id, ativo e datas enviados no corpo (mass assignment) ✅

```bash
curl --location '{{baseUrl}}/api/v1/materias' \
  --header 'Content-Type: application/json' \
  --data-raw '{"nome":"Química","id":"11111111-1111-1111-1111-111111111111","ativo":false,"criadoEm":"2000-01-01T00:00:00.000Z","atualizadoEm":"2000-01-01T00:00:00.000Z"}'
```

Esperado **201**, obtido **201**.

```http
HTTP/1.1 201
Location: /api/v1/materias/3297538b-eb93-4de0-8f3d-f2bb44838c59
Content-Type: application/json

{
  "id": "3297538b-eb93-4de0-8f3d-f2bb44838c59",
  "nome": "Química",
  "descricao": null,
  "ativo": true,
  "criadoEm": "2026-11-20T18:11:34.976Z",
  "atualizadoEm": "2026-11-20T18:11:34.976Z"
}
```

Verificações:

- ✅ id foi gerado pelo servidor
- ✅ ativo continua true
- ✅ criadoEm não é o enviado

#### 4. Criar sem descrição ✅

```bash
curl --location '{{baseUrl}}/api/v1/materias' \
  --header 'Content-Type: application/json' \
  --data-raw '{"nome":"História"}'
```

Esperado **201**, obtido **201**.

```http
HTTP/1.1 201
Location: /api/v1/materias/25e5f0b4-7d69-4e8b-b9ea-4e65e3f61903
Content-Type: application/json

{
  "id": "25e5f0b4-7d69-4e8b-b9ea-4e65e3f61903",
  "nome": "História",
  "descricao": null,
  "ativo": true,
  "criadoEm": "2026-11-20T18:11:35.009Z",
  "atualizadoEm": "2026-11-20T18:11:35.009Z"
}
```

Verificações:

- ✅ descricao é nula

#### 5. Nome duplicado (igual ao existente) ✅

```bash
curl --location '{{baseUrl}}/api/v1/materias' \
  --header 'Content-Type: application/json' \
  --data-raw '{"nome":"Matemática"}'
```

Esperado **409**, obtido **409**.

```http
HTTP/1.1 409
Content-Type: application/json

{
  "dataHora": "2026-11-20T18:11:35.042Z",
  "status": 409,
  "erro": "Conflict",
  "codigo": "MATERIA_NOME_DUPLICADO",
  "mensagem": "Já existe uma matéria com esse nome."
}
```

Verificações:

- ✅ codigo MATERIA_NOME_DUPLICADO
- ✅ erro é Conflict

#### 6. Nome duplicado sem acento e em minúscula (collation do MySQL) ✅

```bash
curl --location '{{baseUrl}}/api/v1/materias' \
  --header 'Content-Type: application/json' \
  --data-raw '{"nome":"matematica"}'
```

Esperado **409**, obtido **409**.

```http
HTTP/1.1 409
Content-Type: application/json

{
  "dataHora": "2026-11-20T18:11:35.066Z",
  "status": 409,
  "erro": "Conflict",
  "codigo": "MATERIA_NOME_DUPLICADO",
  "mensagem": "Já existe uma matéria com esse nome."
}
```

Verificações:

- ✅ codigo MATERIA_NOME_DUPLICADO

#### 7. Nome só com espaços ✅

```bash
curl --location '{{baseUrl}}/api/v1/materias' \
  --header 'Content-Type: application/json' \
  --data-raw '{"nome":"     "}'
```

Esperado **400**, obtido **400**.

```http
HTTP/1.1 400
Content-Type: application/json

{
  "dataHora": "2026-11-20T18:11:35.087Z",
  "status": 400,
  "erro": "Bad Request",
  "codigo": "VALIDACAO_FALHOU",
  "mensagem": "Há campos inválidos na requisição.",
  "campos": [
    {
      "campo": "nome",
      "mensagem": "O nome deve ter de 2 a 100 caracteres."
    }
  ]
}
```

Verificações:

- ✅ codigo VALIDACAO_FALHOU
- ✅ campos aponta o campo nome
- ✅ uma mensagem por campo

#### 8. Nome com 1 caractere ✅

```bash
curl --location '{{baseUrl}}/api/v1/materias' \
  --header 'Content-Type: application/json' \
  --data-raw '{"nome":"A"}'
```

Esperado **400**, obtido **400**.

```http
HTTP/1.1 400
Content-Type: application/json

{
  "dataHora": "2026-11-20T18:11:35.108Z",
  "status": 400,
  "erro": "Bad Request",
  "codigo": "VALIDACAO_FALHOU",
  "mensagem": "Há campos inválidos na requisição.",
  "campos": [
    {
      "campo": "nome",
      "mensagem": "O nome deve ter de 2 a 100 caracteres."
    }
  ]
}
```

Verificações:

- ✅ campos[0].mensagem: O nome deve ter de 2 a 100 caracteres.

#### 9. Sem o campo nome ✅

```bash
curl --location '{{baseUrl}}/api/v1/materias' \
  --header 'Content-Type: application/json' \
  --data-raw '{"descricao":"sem nome"}'
```

Esperado **400**, obtido **400**.

```http
HTTP/1.1 400
Content-Type: application/json

{
  "dataHora": "2026-11-20T18:11:35.128Z",
  "status": 400,
  "erro": "Bad Request",
  "codigo": "VALIDACAO_FALHOU",
  "mensagem": "Há campos inválidos na requisição.",
  "campos": [
    {
      "campo": "nome",
      "mensagem": "O nome é obrigatório."
    }
  ]
}
```

Verificações:

- ✅ mensagem: O nome é obrigatório.

#### 10. Nome com 101 caracteres ✅

```bash
curl --location '{{baseUrl}}/api/v1/materias' \
  --header 'Content-Type: application/json' \
  --data-raw '{"nome":"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"}'
```

Esperado **400**, obtido **400**.

```http
HTTP/1.1 400
Content-Type: application/json

{
  "dataHora": "2026-11-20T18:11:35.147Z",
  "status": 400,
  "erro": "Bad Request",
  "codigo": "VALIDACAO_FALHOU",
  "mensagem": "Há campos inválidos na requisição.",
  "campos": [
    {
      "campo": "nome",
      "mensagem": "O nome deve ter de 2 a 100 caracteres."
    }
  ]
}
```

Verificações:

- ✅ campos aponta o campo nome

#### 11. Descrição com 256 caracteres ✅

```bash
curl --location '{{baseUrl}}/api/v1/materias' \
  --header 'Content-Type: application/json' \
  --data-raw '{"nome":"Geografia","descricao":"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"}'
```

Esperado **400**, obtido **400**.

```http
HTTP/1.1 400
Content-Type: application/json

{
  "dataHora": "2026-11-20T18:11:35.170Z",
  "status": 400,
  "erro": "Bad Request",
  "codigo": "VALIDACAO_FALHOU",
  "mensagem": "Há campos inválidos na requisição.",
  "campos": [
    {
      "campo": "descricao",
      "mensagem": "A descrição deve ter no máximo 255 caracteres."
    }
  ]
}
```

Verificações:

- ✅ campos aponta o campo descricao
- ✅ mensagem: A descrição deve ter no máximo 255 caracteres.

#### 12. Corpo ilegível (JSON quebrado) ✅

```bash
curl --location '{{baseUrl}}/api/v1/materias' \
  --header 'Content-Type: application/json' \
  --data-raw '{"nome": '
```

Esperado **400**, obtido **400**.

```http
HTTP/1.1 400
Content-Type: application/json

{
  "dataHora": "2026-11-20T18:11:35.192Z",
  "status": 400,
  "erro": "Bad Request",
  "codigo": "CORPO_ILEGIVEL",
  "mensagem": "O corpo da requisição está ilegível ou em formato inválido."
}
```

Verificações:

- ✅ codigo CORPO_ILEGIVEL
- ✅ não vaza o trecho enviado nem detalhes do parser

#### 13. Content-Type não suportado (text/plain) ✅

```bash
curl --location '{{baseUrl}}/api/v1/materias' \
  --header 'Content-Type: text/plain' \
  --data-raw 'texto'
```

Esperado **415**, obtido **415**.

```http
HTTP/1.1 415
Content-Type: application/json

{
  "dataHora": "2026-11-20T18:11:35.220Z",
  "status": 415,
  "erro": "Unsupported Media Type",
  "codigo": "TIPO_NAO_SUPORTADO",
  "mensagem": "Tipo de conteúdo não suportado. Envie o corpo em application/json."
}
```

Verificações:

- ✅ codigo TIPO_NAO_SUPORTADO

### GET — buscar

#### 14. Buscar por id ✅

```bash
curl --location '{{baseUrl}}/api/v1/materias/{{materiaId}}'
```

Esperado **200**, obtido **200**.

```http
HTTP/1.1 200
Content-Type: application/json

{
  "id": "8fa50f72-706a-43c3-8173-9a87fd036a60",
  "nome": "Matemática",
  "descricao": "Álgebra e geometria",
  "ativo": true,
  "criadoEm": "2026-11-20T18:11:34.909Z",
  "atualizadoEm": "2026-11-20T18:11:34.909Z"
}
```

Verificações:

- ✅ id igual ao pedido
- ✅ nome Matemática

#### 15. Id inexistente ✅

```bash
curl --location '{{baseUrl}}/api/v1/materias/11111111-2222-3333-4444-555555555555'
```

Esperado **404**, obtido **404**.

```http
HTTP/1.1 404
Content-Type: application/json

{
  "dataHora": "2026-11-20T18:11:35.285Z",
  "status": 404,
  "erro": "Not Found",
  "codigo": "MATERIA_NAO_ENCONTRADA",
  "mensagem": "Matéria não encontrada."
}
```

Verificações:

- ✅ codigo MATERIA_NAO_ENCONTRADA

#### 16. Id malformado (não é UUID) ✅

```bash
curl --location '{{baseUrl}}/api/v1/materias/isto-nao-e-um-uuid'
```

Esperado **400**, obtido **400**.

```http
HTTP/1.1 400
Content-Type: application/json

{
  "dataHora": "2026-11-20T18:11:35.311Z",
  "status": 400,
  "erro": "Bad Request",
  "codigo": "PARAMETRO_INVALIDO",
  "mensagem": "O parâmetro 'id' tem um valor inválido."
}
```

Verificações:

- ✅ codigo PARAMETRO_INVALIDO
- ✅ não devolve o valor enviado

#### 17. Listagem (ainda não implementada: Módulo 8) ✅

```bash
curl --location '{{baseUrl}}/api/v1/materias'
```

Esperado **405**, obtido **405**.

```http
HTTP/1.1 405
Allow: POST
Content-Type: application/json

{
  "dataHora": "2026-11-20T18:11:35.335Z",
  "status": 405,
  "erro": "Method Not Allowed",
  "codigo": "METODO_NAO_PERMITIDO",
  "mensagem": "Método HTTP não permitido para este recurso."
}
```

Verificações:

- ✅ codigo METODO_NAO_PERMITIDO
- ✅ cabeçalho Allow presente

### PUT — atualizar

#### 18. Trocar nome e descrição (ignora o ativo enviado) ✅

```bash
curl --location --request PUT '{{baseUrl}}/api/v1/materias/{{materiaId}}' \
  --header 'Content-Type: application/json' \
  --data-raw '{"nome":"Matemática Aplicada","descricao":"Nova descrição","ativo":false}'
```

Esperado **200**, obtido **200**.

```http
HTTP/1.1 200
Content-Type: application/json

{
  "id": "8fa50f72-706a-43c3-8173-9a87fd036a60",
  "nome": "Matemática Aplicada",
  "descricao": "Nova descrição",
  "ativo": true,
  "criadoEm": "2026-11-20T18:11:34.909Z",
  "atualizadoEm": "2026-11-20T18:11:35.369Z"
}
```

Verificações:

- ✅ nome e descrição trocados
- ✅ ativo não mudou (continua true)

#### 19. Descrição omitida: PUT é substituição, vira nula (D61) ✅

```bash
curl --location --request PUT '{{baseUrl}}/api/v1/materias/{{materiaId}}' \
  --header 'Content-Type: application/json' \
  --data-raw '{"nome":"Matemática Aplicada"}'
```

Esperado **200**, obtido **200**.

```http
HTTP/1.1 200
Content-Type: application/json

{
  "id": "8fa50f72-706a-43c3-8173-9a87fd036a60",
  "nome": "Matemática Aplicada",
  "descricao": null,
  "ativo": true,
  "criadoEm": "2026-11-20T18:11:34.909Z",
  "atualizadoEm": "2026-11-20T18:11:35.410Z"
}
```

Verificações:

- ✅ o próprio nome é permitido
- ✅ descricao virou nula

#### 20. Nome que pertence a outra matéria ✅

```bash
curl --location --request PUT '{{baseUrl}}/api/v1/materias/{{materiaId}}' \
  --header 'Content-Type: application/json' \
  --data-raw '{"nome":"Física"}'
```

Esperado **409**, obtido **409**.

```http
HTTP/1.1 409
Content-Type: application/json

{
  "dataHora": "2026-11-20T18:11:35.450Z",
  "status": 409,
  "erro": "Conflict",
  "codigo": "MATERIA_NOME_DUPLICADO",
  "mensagem": "Já existe uma matéria com esse nome."
}
```

Verificações:

- ✅ codigo MATERIA_NOME_DUPLICADO

#### 21. Id inexistente ✅

```bash
curl --location --request PUT '{{baseUrl}}/api/v1/materias/11111111-2222-3333-4444-555555555555' \
  --header 'Content-Type: application/json' \
  --data-raw '{"nome":"Qualquer"}'
```

Esperado **404**, obtido **404**.

```http
HTTP/1.1 404
Content-Type: application/json

{
  "dataHora": "2026-11-20T18:11:35.481Z",
  "status": 404,
  "erro": "Not Found",
  "codigo": "MATERIA_NAO_ENCONTRADA",
  "mensagem": "Matéria não encontrada."
}
```

Verificações:

- ✅ codigo MATERIA_NAO_ENCONTRADA

#### 22. Nome inválido (só espaços) ✅

```bash
curl --location --request PUT '{{baseUrl}}/api/v1/materias/{{materiaId}}' \
  --header 'Content-Type: application/json' \
  --data-raw '{"nome":" "}'
```

Esperado **400**, obtido **400**.

```http
HTTP/1.1 400
Content-Type: application/json

{
  "dataHora": "2026-11-20T18:11:35.520Z",
  "status": 400,
  "erro": "Bad Request",
  "codigo": "VALIDACAO_FALHOU",
  "mensagem": "Há campos inválidos na requisição.",
  "campos": [
    {
      "campo": "nome",
      "mensagem": "O nome deve ter de 2 a 100 caracteres."
    }
  ]
}
```

Verificações:

- ✅ codigo VALIDACAO_FALHOU
- ✅ campos aponta o campo nome

#### 23. Id malformado ✅

```bash
curl --location --request PUT '{{baseUrl}}/api/v1/materias/isto-nao-e-um-uuid' \
  --header 'Content-Type: application/json' \
  --data-raw '{"nome":"Qualquer"}'
```

Esperado **400**, obtido **400**.

```http
HTTP/1.1 400
Content-Type: application/json

{
  "dataHora": "2026-11-20T18:11:35.566Z",
  "status": 400,
  "erro": "Bad Request",
  "codigo": "PARAMETRO_INVALIDO",
  "mensagem": "O parâmetro 'id' tem um valor inválido."
}
```

Verificações:

- ✅ codigo PARAMETRO_INVALIDO

### PATCH — desativar e ativar

#### 24. Desativar ✅

```bash
curl --location --request PATCH '{{baseUrl}}/api/v1/materias/{{materiaId}}/disable'
```

Esperado **200**, obtido **200**.

```http
HTTP/1.1 200
Content-Type: application/json

{
  "id": "8fa50f72-706a-43c3-8173-9a87fd036a60",
  "nome": "Matemática Aplicada",
  "descricao": null,
  "ativo": false,
  "criadoEm": "2026-11-20T18:11:34.909Z",
  "atualizadoEm": "2026-11-20T18:11:35.621Z"
}
```

Verificações:

- ✅ ativo é false

#### 25. Desativar de novo (idempotente) ✅

```bash
curl --location --request PATCH '{{baseUrl}}/api/v1/materias/{{materiaId}}/disable'
```

Esperado **200**, obtido **200**.

```http
HTTP/1.1 200
Content-Type: application/json

{
  "id": "8fa50f72-706a-43c3-8173-9a87fd036a60",
  "nome": "Matemática Aplicada",
  "descricao": null,
  "ativo": false,
  "criadoEm": "2026-11-20T18:11:34.909Z",
  "atualizadoEm": "2026-11-20T18:11:35.621Z"
}
```

Verificações:

- ✅ mesmo resultado da chamada anterior
- ✅ atualizadoEm não mudou (D62)

#### 26. Ativar ✅

```bash
curl --location --request PATCH '{{baseUrl}}/api/v1/materias/{{materiaId}}/enable'
```

Esperado **200**, obtido **200**.

```http
HTTP/1.1 200
Content-Type: application/json

{
  "id": "8fa50f72-706a-43c3-8173-9a87fd036a60",
  "nome": "Matemática Aplicada",
  "descricao": null,
  "ativo": true,
  "criadoEm": "2026-11-20T18:11:34.909Z",
  "atualizadoEm": "2026-11-20T18:11:35.710Z"
}
```

Verificações:

- ✅ ativo é true

#### 27. Ativar de novo (idempotente) ✅

```bash
curl --location --request PATCH '{{baseUrl}}/api/v1/materias/{{materiaId}}/enable'
```

Esperado **200**, obtido **200**.

```http
HTTP/1.1 200
Content-Type: application/json

{
  "id": "8fa50f72-706a-43c3-8173-9a87fd036a60",
  "nome": "Matemática Aplicada",
  "descricao": null,
  "ativo": true,
  "criadoEm": "2026-11-20T18:11:34.909Z",
  "atualizadoEm": "2026-11-20T18:11:35.710Z"
}
```

Verificações:

- ✅ mesmo resultado da chamada anterior
- ✅ atualizadoEm não mudou (D62)

#### 28. Desativar id inexistente ✅

```bash
curl --location --request PATCH '{{baseUrl}}/api/v1/materias/11111111-2222-3333-4444-555555555555/disable'
```

Esperado **404**, obtido **404**.

```http
HTTP/1.1 404
Content-Type: application/json

{
  "dataHora": "2026-11-20T18:11:35.786Z",
  "status": 404,
  "erro": "Not Found",
  "codigo": "MATERIA_NAO_ENCONTRADA",
  "mensagem": "Matéria não encontrada."
}
```

Verificações:

- ✅ codigo MATERIA_NAO_ENCONTRADA

#### 29. Ativar id inexistente ✅

```bash
curl --location --request PATCH '{{baseUrl}}/api/v1/materias/11111111-2222-3333-4444-555555555555/enable'
```

Esperado **404**, obtido **404**.

```http
HTTP/1.1 404
Content-Type: application/json

{
  "dataHora": "2026-11-20T18:11:35.815Z",
  "status": 404,
  "erro": "Not Found",
  "codigo": "MATERIA_NAO_ENCONTRADA",
  "mensagem": "Matéria não encontrada."
}
```

Verificações:

- ✅ codigo MATERIA_NAO_ENCONTRADA

#### 30. Id malformado ✅

```bash
curl --location --request PATCH '{{baseUrl}}/api/v1/materias/isto-nao-e-um-uuid/disable'
```

Esperado **400**, obtido **400**.

```http
HTTP/1.1 400
Content-Type: application/json

{
  "dataHora": "2026-11-20T18:11:35.838Z",
  "status": 400,
  "erro": "Bad Request",
  "codigo": "PARAMETRO_INVALIDO",
  "mensagem": "O parâmetro 'id' tem um valor inválido."
}
```

Verificações:

- ✅ codigo PARAMETRO_INVALIDO

### DELETE — apagar

#### 31. Apagar ✅

```bash
curl --location --request DELETE '{{baseUrl}}/api/v1/materias/{{materiaId}}'
```

Esperado **204**, obtido **204**.

```http
HTTP/1.1 204

(sem corpo)
```

Verificações:

- ✅ corpo vazio

#### 32. Buscar depois de apagar ✅

```bash
curl --location '{{baseUrl}}/api/v1/materias/{{materiaId}}'
```

Esperado **404**, obtido **404**.

```http
HTTP/1.1 404
Content-Type: application/json

{
  "dataHora": "2026-11-20T18:11:35.901Z",
  "status": 404,
  "erro": "Not Found",
  "codigo": "MATERIA_NAO_ENCONTRADA",
  "mensagem": "Matéria não encontrada."
}
```

Verificações:

- ✅ codigo MATERIA_NAO_ENCONTRADA

#### 33. Apagar de novo ✅

```bash
curl --location --request DELETE '{{baseUrl}}/api/v1/materias/{{materiaId}}'
```

Esperado **404**, obtido **404**.

```http
HTTP/1.1 404
Content-Type: application/json

{
  "dataHora": "2026-11-20T18:11:35.923Z",
  "status": 404,
  "erro": "Not Found",
  "codigo": "MATERIA_NAO_ENCONTRADA",
  "mensagem": "Matéria não encontrada."
}
```

Verificações:

- ✅ codigo MATERIA_NAO_ENCONTRADA

#### 34. Id malformado ✅

```bash
curl --location --request DELETE '{{baseUrl}}/api/v1/materias/isto-nao-e-um-uuid'
```

Esperado **400**, obtido **400**.

```http
HTTP/1.1 400
Content-Type: application/json

{
  "dataHora": "2026-11-20T18:11:35.945Z",
  "status": 400,
  "erro": "Bad Request",
  "codigo": "PARAMETRO_INVALIDO",
  "mensagem": "O parâmetro 'id' tem um valor inválido."
}
```

Verificações:

- ✅ codigo PARAMETRO_INVALIDO

### Outras

#### 35. Rota inexistente ✅

```bash
curl --location '{{baseUrl}}/api/v1/nao-existe'
```

Esperado **404**, obtido **404**.

```http
HTTP/1.1 404
Content-Type: application/json

{
  "dataHora": "2026-11-20T18:11:35.983Z",
  "status": 404,
  "erro": "Not Found",
  "codigo": "RECURSO_NAO_ENCONTRADO",
  "mensagem": "Recurso não encontrado."
}
```

Verificações:

- ✅ codigo RECURSO_NAO_ENCONTRADO
- ✅ resposta em JSON (nunca HTML)

#### 36. Rota inexistente em método com corpo (POST) ✅

```bash
curl --location '{{baseUrl}}/api/v1/nao-existe' \
  --header 'Content-Type: application/json' \
  --data-raw '{}'
```

Esperado **404**, obtido **404**.

```http
HTTP/1.1 404
Content-Type: application/json

{
  "dataHora": "2026-11-20T18:11:36.006Z",
  "status": 404,
  "erro": "Not Found",
  "codigo": "RECURSO_NAO_ENCONTRADO",
  "mensagem": "Recurso não encontrado."
}
```

Verificações:

- ✅ 404 e não 405

### Limpeza

#### 37. Apagar a matéria Física ✅

```bash
curl --location --request DELETE '{{baseUrl}}/api/v1/materias/{{outraMateriaId}}'
```

Esperado **204**, obtido **204**.

```http
HTTP/1.1 204

(sem corpo)
```

#### 38. Apagar a matéria Química ✅

```bash
curl --location --request DELETE '{{baseUrl}}/api/v1/materias/{{materiaIdC}}'
```

Esperado **204**, obtido **204**.

```http
HTTP/1.1 204

(sem corpo)
```

#### 39. Apagar a matéria História ✅

```bash
curl --location --request DELETE '{{baseUrl}}/api/v1/materias/{{materiaIdD}}'
```

Esperado **204**, obtido **204**.

```http
HTTP/1.1 204

(sem corpo)
```

## Conclusões

- Todas as chamadas devolveram o status esperado e passaram nas verificações.
- O formato de erro é o mesmo em todos os casos (`dataHora`, `status`, `erro`, `codigo`, `mensagem`, e `campos` só em 400 de validação).
- Confirmado no MySQL real: `matematica` (sem acento, minúscula) é recusado como duplicado de `Matemática` (collation `utf8mb4_unicode_ci`).
- Confirmado: `Location` relativo (D58), nome sem espaços nas pontas (D57), no máximo uma mensagem por campo nos casos testados (D59), `PUT` como substituição (D61) e `disable`/`enable` repetidos sem alterar a resposta (D62), datas com 3 casas de milissegundos (D63).
- `GET /api/v1/materias` (listagem) devolve **405** hoje, porque ela é do Módulo 8; `POST /api/v1/materias` é a única operação nessa rota.
- Não cobertos aqui, por não existirem ainda: listagem, 401/403 e o 409 do `DELETE` por subtópicos.
