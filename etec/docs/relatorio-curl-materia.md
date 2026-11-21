# Relatório: chamadas reais às rotas de Matéria

Executado em 2026-11-21 20:19:41 UTC, a partir da `develop` (`7c51928`). **53 de 53 chamadas conformes** com o esperado.

## Ambiente

- Aplicação: `java -jar` na porta **8085**, perfil `local` (a coleção usa `{{baseUrl}}`, por padrão `http://localhost:8080`).
- Banco: MySQL 8.4 real, contêiner `mysql-etec` (porta 3308), migrations V1 e V2 aplicadas; tabela `materia` vazia no início e no fim.
- Cliente: `curl 8.21.0` (as respostas abaixo são as devolvidas de verdade; nada foi simulado).
- Sem autenticação (401/403 ficam para os Módulos 10 e 11) e sem Swagger (desligado por padrão).

## Como usar no Postman

**Opção 1 (recomendada): importar a coleção.** `Import` → arquivo `materia.postman_collection.json`. As variáveis `materiaId`, `outraMateriaId`, `materiaIdC` e `materiaIdD` são preenchidas sozinhas pelos `POST` (script de teste). Rode a coleção **na ordem** (Collection Runner ou uma a uma): ela cria 4 matérias, exercita cada rota e apaga tudo no fim. Ajuste `baseUrl` se a aplicação não estiver na 8080.

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
| 14 | GET — listar | Listar todas (padrão: página 0, tamanho 20, nome crescente) | 200 | 200 | ✅ |
| 15 | GET — listar | Paginar: tamanho 2, página 0, ordenado por nome | 200 | 200 | ✅ |
| 16 | GET — listar | Segunda página | 200 | 200 | ✅ |
| 17 | GET — listar | Ordenar por criadoEm decrescente | 200 | 200 | ✅ |
| 18 | GET — listar | Filtrar por nome sem acento e em minúscula (collation do MySQL) | 200 | 200 | ✅ |
| 19 | GET — listar | Filtrar por nome com % (vale como texto, não como curinga) | 200 | 200 | ✅ |
| 20 | GET — listar | Filtrar por ativo=true | 200 | 200 | ✅ |
| 21 | GET — listar | Página além do fim: 200 com conteúdo vazio e totais corretos | 200 | 200 | ✅ |
| 22 | GET — listar | Tamanho gigante é reduzido ao máximo (50) | 200 | 200 | ✅ |
| 23 | GET — listar | Sort com injeção de SQL é recusado | 400 | 400 | ✅ |
| 24 | GET — listar | Sort por campo fora da lista (id) | 400 | 400 | ✅ |
| 25 | GET — listar | Página negativa | 400 | 400 | ✅ |
| 26 | GET — listar | Tamanho zero | 400 | 400 | ✅ |
| 27 | GET — listar | Filtro ativo inválido | 400 | 400 | ✅ |
| 28 | GET — listar | Método não permitido na coleção (DELETE em /materias) | 405 | 405 | ✅ |
| 29 | GET — buscar | Buscar por id | 200 | 200 | ✅ |
| 30 | GET — buscar | Id inexistente | 404 | 404 | ✅ |
| 31 | GET — buscar | Id malformado (não é UUID) | 400 | 400 | ✅ |
| 32 | PUT — atualizar | Trocar nome e descrição (ignora o ativo enviado) | 200 | 200 | ✅ |
| 33 | PUT — atualizar | Descrição omitida: PUT é substituição, vira nula (D61) | 200 | 200 | ✅ |
| 34 | PUT — atualizar | Nome que pertence a outra matéria | 409 | 409 | ✅ |
| 35 | PUT — atualizar | Id inexistente | 404 | 404 | ✅ |
| 36 | PUT — atualizar | Nome inválido (só espaços) | 400 | 400 | ✅ |
| 37 | PUT — atualizar | Id malformado | 400 | 400 | ✅ |
| 38 | PATCH — desativar e ativar | Desativar | 200 | 200 | ✅ |
| 39 | PATCH — desativar e ativar | Desativar de novo (idempotente) | 200 | 200 | ✅ |
| 40 | PATCH — desativar e ativar | Ativar | 200 | 200 | ✅ |
| 41 | PATCH — desativar e ativar | Ativar de novo (idempotente) | 200 | 200 | ✅ |
| 42 | PATCH — desativar e ativar | Desativar id inexistente | 404 | 404 | ✅ |
| 43 | PATCH — desativar e ativar | Ativar id inexistente | 404 | 404 | ✅ |
| 44 | PATCH — desativar e ativar | Id malformado | 400 | 400 | ✅ |
| 45 | DELETE — apagar | Apagar | 204 | 204 | ✅ |
| 46 | DELETE — apagar | Buscar depois de apagar | 404 | 404 | ✅ |
| 47 | DELETE — apagar | Apagar de novo | 404 | 404 | ✅ |
| 48 | DELETE — apagar | Id malformado | 400 | 400 | ✅ |
| 49 | Outras | Rota inexistente | 404 | 404 | ✅ |
| 50 | Outras | Rota inexistente em método com corpo (POST) | 404 | 404 | ✅ |
| 51 | Limpeza | Apagar a matéria Física | 204 | 204 | ✅ |
| 52 | Limpeza | Apagar a matéria Química | 204 | 204 | ✅ |
| 53 | Limpeza | Apagar a matéria História | 204 | 204 | ✅ |

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
Location: /api/v1/materias/bb6f863d-598f-4496-9234-7454a055052c
Content-Type: application/json

{
  "id": "bb6f863d-598f-4496-9234-7454a055052c",
  "nome": "Matemática",
  "descricao": "Álgebra e geometria",
  "ativo": true,
  "criadoEm": "2026-11-21T20:19:39.228Z",
  "atualizadoEm": "2026-11-21T20:19:39.228Z"
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
Location: /api/v1/materias/475d5da1-2ce3-419f-b795-67dcd57f1d84
Content-Type: application/json

{
  "id": "475d5da1-2ce3-419f-b795-67dcd57f1d84",
  "nome": "Física",
  "descricao": "Mecânica e óptica",
  "ativo": true,
  "criadoEm": "2026-11-21T20:19:39.348Z",
  "atualizadoEm": "2026-11-21T20:19:39.348Z"
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
Location: /api/v1/materias/3e11b613-8401-41cb-b131-0f72a99eb88e
Content-Type: application/json

{
  "id": "3e11b613-8401-41cb-b131-0f72a99eb88e",
  "nome": "Química",
  "descricao": null,
  "ativo": true,
  "criadoEm": "2026-11-21T20:19:39.391Z",
  "atualizadoEm": "2026-11-21T20:19:39.391Z"
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
Location: /api/v1/materias/e46cdec2-dcbc-4e01-b60e-edfe685e5016
Content-Type: application/json

{
  "id": "e46cdec2-dcbc-4e01-b60e-edfe685e5016",
  "nome": "História",
  "descricao": null,
  "ativo": true,
  "criadoEm": "2026-11-21T20:19:39.428Z",
  "atualizadoEm": "2026-11-21T20:19:39.428Z"
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
  "dataHora": "2026-11-21T20:19:39.476Z",
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
  "dataHora": "2026-11-21T20:19:39.514Z",
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
  "dataHora": "2026-11-21T20:19:39.542Z",
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
  "dataHora": "2026-11-21T20:19:39.566Z",
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
  "dataHora": "2026-11-21T20:19:39.612Z",
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
  "dataHora": "2026-11-21T20:19:39.659Z",
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
  "dataHora": "2026-11-21T20:19:39.693Z",
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
  "dataHora": "2026-11-21T20:19:39.739Z",
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
  "dataHora": "2026-11-21T20:19:39.799Z",
  "status": 415,
  "erro": "Unsupported Media Type",
  "codigo": "TIPO_NAO_SUPORTADO",
  "mensagem": "Tipo de conteúdo não suportado. Envie o corpo em application/json."
}
```

Verificações:

- ✅ codigo TIPO_NAO_SUPORTADO

### GET — listar

#### 14. Listar todas (padrão: página 0, tamanho 20, nome crescente) ✅

```bash
curl --location '{{baseUrl}}/api/v1/materias'
```

Esperado **200**, obtido **200**.

```http
HTTP/1.1 200
Content-Type: application/json

{
  "conteudo": [
    {
      "id": "475d5da1-2ce3-419f-b795-67dcd57f1d84",
      "nome": "Física",
      "descricao": "Mecânica e óptica",
      "ativo": true,
      "criadoEm": "2026-11-21T20:19:39.348Z",
      "atualizadoEm": "2026-11-21T20:19:39.348Z"
    },
    {
      "id": "e46cdec2-dcbc-4e01-b60e-edfe685e5016",
      "nome": "História",
      "descricao": null,
      "ativo": true,
      "criadoEm": "2026-11-21T20:19:39.428Z",
      "atualizadoEm": "2026-11-21T20:19:39.428Z"
    },
    {
      "id": "bb6f863d-598f-4496-9234-7454a055052c",
      "nome": "Matemática",
      "descricao": "Álgebra e geometria",
      "ativo": true,
      "criadoEm": "2026-11-21T20:19:39.228Z",
      "atualizadoEm": "2026-11-21T20:19:39.228Z"
    },
    {
      "id": "3e11b613-8401-41cb-b131-0f72a99eb88e",
      "nome": "Química",
      "descricao": null,
      "ativo": true,
      "criadoEm": "2026-11-21T20:19:39.391Z",
      "atualizadoEm": "2026-11-21T20:19:39.391Z"
    }
  ],
  "pagina": 0,
  "tamanho": 20,
  "totalElementos": 4,
  "totalPaginas": 1
}
```

Verificações:

- ✅ envelope com os campos na ordem do contrato
- ✅ ordenadas por nome crescente

#### 15. Paginar: tamanho 2, página 0, ordenado por nome ✅

```bash
curl --location '{{baseUrl}}/api/v1/materias?size=2&page=0&sort=nome,asc'
```

Esperado **200**, obtido **200**.

```http
HTTP/1.1 200
Content-Type: application/json

{
  "conteudo": [
    {
      "id": "475d5da1-2ce3-419f-b795-67dcd57f1d84",
      "nome": "Física",
      "descricao": "Mecânica e óptica",
      "ativo": true,
      "criadoEm": "2026-11-21T20:19:39.348Z",
      "atualizadoEm": "2026-11-21T20:19:39.348Z"
    },
    {
      "id": "e46cdec2-dcbc-4e01-b60e-edfe685e5016",
      "nome": "História",
      "descricao": null,
      "ativo": true,
      "criadoEm": "2026-11-21T20:19:39.428Z",
      "atualizadoEm": "2026-11-21T20:19:39.428Z"
    }
  ],
  "pagina": 0,
  "tamanho": 2,
  "totalElementos": 4,
  "totalPaginas": 2
}
```

Verificações:

- ✅ tamanho 2, 4 elementos, 2 páginas
- ✅ itens Física e História

#### 16. Segunda página ✅

```bash
curl --location '{{baseUrl}}/api/v1/materias?size=2&page=1&sort=nome,asc'
```

Esperado **200**, obtido **200**.

```http
HTTP/1.1 200
Content-Type: application/json

{
  "conteudo": [
    {
      "id": "bb6f863d-598f-4496-9234-7454a055052c",
      "nome": "Matemática",
      "descricao": "Álgebra e geometria",
      "ativo": true,
      "criadoEm": "2026-11-21T20:19:39.228Z",
      "atualizadoEm": "2026-11-21T20:19:39.228Z"
    },
    {
      "id": "3e11b613-8401-41cb-b131-0f72a99eb88e",
      "nome": "Química",
      "descricao": null,
      "ativo": true,
      "criadoEm": "2026-11-21T20:19:39.391Z",
      "atualizadoEm": "2026-11-21T20:19:39.391Z"
    }
  ],
  "pagina": 1,
  "tamanho": 2,
  "totalElementos": 4,
  "totalPaginas": 2
}
```

Verificações:

- ✅ pagina 1
- ✅ itens Matemática e Química

#### 17. Ordenar por criadoEm decrescente ✅

```bash
curl --location '{{baseUrl}}/api/v1/materias?sort=criadoEm,desc'
```

Esperado **200**, obtido **200**.

```http
HTTP/1.1 200
Content-Type: application/json

{
  "conteudo": [
    {
      "id": "e46cdec2-dcbc-4e01-b60e-edfe685e5016",
      "nome": "História",
      "descricao": null,
      "ativo": true,
      "criadoEm": "2026-11-21T20:19:39.428Z",
      "atualizadoEm": "2026-11-21T20:19:39.428Z"
    },
    {
      "id": "3e11b613-8401-41cb-b131-0f72a99eb88e",
      "nome": "Química",
      "descricao": null,
      "ativo": true,
      "criadoEm": "2026-11-21T20:19:39.391Z",
      "atualizadoEm": "2026-11-21T20:19:39.391Z"
    },
    {
      "id": "475d5da1-2ce3-419f-b795-67dcd57f1d84",
      "nome": "Física",
      "descricao": "Mecânica e óptica",
      "ativo": true,
      "criadoEm": "2026-11-21T20:19:39.348Z",
      "atualizadoEm": "2026-11-21T20:19:39.348Z"
    },
    {
      "id": "bb6f863d-598f-4496-9234-7454a055052c",
      "nome": "Matemática",
      "descricao": "Álgebra e geometria",
      "ativo": true,
      "criadoEm": "2026-11-21T20:19:39.228Z",
      "atualizadoEm": "2026-11-21T20:19:39.228Z"
    }
  ],
  "pagina": 0,
  "tamanho": 20,
  "totalElementos": 4,
  "totalPaginas": 1
}
```

Verificações:

- ✅ mais recente primeiro (História, Química, Física, Matemática)

#### 18. Filtrar por nome sem acento e em minúscula (collation do MySQL) ✅

```bash
curl --location '{{baseUrl}}/api/v1/materias?nome=matematica'
```

Esperado **200**, obtido **200**.

```http
HTTP/1.1 200
Content-Type: application/json

{
  "conteudo": [
    {
      "id": "bb6f863d-598f-4496-9234-7454a055052c",
      "nome": "Matemática",
      "descricao": "Álgebra e geometria",
      "ativo": true,
      "criadoEm": "2026-11-21T20:19:39.228Z",
      "atualizadoEm": "2026-11-21T20:19:39.228Z"
    }
  ],
  "pagina": 0,
  "tamanho": 20,
  "totalElementos": 1,
  "totalPaginas": 1
}
```

Verificações:

- ✅ encontra Matemática

#### 19. Filtrar por nome com % (vale como texto, não como curinga) ✅

```bash
curl --location '{{baseUrl}}/api/v1/materias?nome=%25'
```

Esperado **200**, obtido **200**.

```http
HTTP/1.1 200
Content-Type: application/json

{
  "conteudo": [],
  "pagina": 0,
  "tamanho": 20,
  "totalElementos": 0,
  "totalPaginas": 0
}
```

Verificações:

- ✅ não devolve tudo: nenhum nome contém %

#### 20. Filtrar por ativo=true ✅

```bash
curl --location '{{baseUrl}}/api/v1/materias?ativo=true'
```

Esperado **200**, obtido **200**.

```http
HTTP/1.1 200
Content-Type: application/json

{
  "conteudo": [
    {
      "id": "475d5da1-2ce3-419f-b795-67dcd57f1d84",
      "nome": "Física",
      "descricao": "Mecânica e óptica",
      "ativo": true,
      "criadoEm": "2026-11-21T20:19:39.348Z",
      "atualizadoEm": "2026-11-21T20:19:39.348Z"
    },
    {
      "id": "e46cdec2-dcbc-4e01-b60e-edfe685e5016",
      "nome": "História",
      "descricao": null,
      "ativo": true,
      "criadoEm": "2026-11-21T20:19:39.428Z",
      "atualizadoEm": "2026-11-21T20:19:39.428Z"
    },
    {
      "id": "bb6f863d-598f-4496-9234-7454a055052c",
      "nome": "Matemática",
      "descricao": "Álgebra e geometria",
      "ativo": true,
      "criadoEm": "2026-11-21T20:19:39.228Z",
      "atualizadoEm": "2026-11-21T20:19:39.228Z"
    },
    {
      "id": "3e11b613-8401-41cb-b131-0f72a99eb88e",
      "nome": "Química",
      "descricao": null,
      "ativo": true,
      "criadoEm": "2026-11-21T20:19:39.391Z",
      "atualizadoEm": "2026-11-21T20:19:39.391Z"
    }
  ],
  "pagina": 0,
  "tamanho": 20,
  "totalElementos": 4,
  "totalPaginas": 1
}
```

Verificações:

- ✅ as 4 estão ativas

#### 21. Página além do fim: 200 com conteúdo vazio e totais corretos ✅

```bash
curl --location '{{baseUrl}}/api/v1/materias?page=99'
```

Esperado **200**, obtido **200**.

```http
HTTP/1.1 200
Content-Type: application/json

{
  "conteudo": [],
  "pagina": 99,
  "tamanho": 20,
  "totalElementos": 4,
  "totalPaginas": 1
}
```

Verificações:

- ✅ conteúdo vazio
- ✅ totais corretos

#### 22. Tamanho gigante é reduzido ao máximo (50) ✅

```bash
curl --location '{{baseUrl}}/api/v1/materias?size=100000'
```

Esperado **200**, obtido **200**.

```http
HTTP/1.1 200
Content-Type: application/json

{
  "conteudo": [
    {
      "id": "475d5da1-2ce3-419f-b795-67dcd57f1d84",
      "nome": "Física",
      "descricao": "Mecânica e óptica",
      "ativo": true,
      "criadoEm": "2026-11-21T20:19:39.348Z",
      "atualizadoEm": "2026-11-21T20:19:39.348Z"
    },
    {
      "id": "e46cdec2-dcbc-4e01-b60e-edfe685e5016",
      "nome": "História",
      "descricao": null,
      "ativo": true,
      "criadoEm": "2026-11-21T20:19:39.428Z",
      "atualizadoEm": "2026-11-21T20:19:39.428Z"
    },
    {
      "id": "bb6f863d-598f-4496-9234-7454a055052c",
      "nome": "Matemática",
      "descricao": "Álgebra e geometria",
      "ativo": true,
      "criadoEm": "2026-11-21T20:19:39.228Z",
      "atualizadoEm": "2026-11-21T20:19:39.228Z"
    },
    {
      "id": "3e11b613-8401-41cb-b131-0f72a99eb88e",
      "nome": "Química",
      "descricao": null,
      "ativo": true,
      "criadoEm": "2026-11-21T20:19:39.391Z",
      "atualizadoEm": "2026-11-21T20:19:39.391Z"
    }
  ],
  "pagina": 0,
  "tamanho": 50,
  "totalElementos": 4,
  "totalPaginas": 1
}
```

Verificações:

- ✅ tamanho devolvido é 50

#### 23. Sort com injeção de SQL é recusado ✅

```bash
curl --location '{{baseUrl}}/api/v1/materias?sort=nome%3Bdrop%20table%20x'
```

Esperado **400**, obtido **400**.

```http
HTTP/1.1 400
Content-Type: application/json

{
  "dataHora": "2026-11-21T20:19:40.300Z",
  "status": 400,
  "erro": "Bad Request",
  "codigo": "ORDENACAO_INVALIDA",
  "mensagem": "O parâmetro 'sort' é inválido. Use campo,direção (asc ou desc) com um dos campos: criadoEm, nome."
}
```

Verificações:

- ✅ codigo ORDENACAO_INVALIDA
- ✅ não repete o valor enviado

#### 24. Sort por campo fora da lista (id) ✅

```bash
curl --location '{{baseUrl}}/api/v1/materias?sort=id,asc'
```

Esperado **400**, obtido **400**.

```http
HTTP/1.1 400
Content-Type: application/json

{
  "dataHora": "2026-11-21T20:19:40.328Z",
  "status": 400,
  "erro": "Bad Request",
  "codigo": "ORDENACAO_INVALIDA",
  "mensagem": "O parâmetro 'sort' é inválido. Use campo,direção (asc ou desc) com um dos campos: criadoEm, nome."
}
```

Verificações:

- ✅ codigo ORDENACAO_INVALIDA
- ✅ a mensagem lista os campos aceitos

#### 25. Página negativa ✅

```bash
curl --location '{{baseUrl}}/api/v1/materias?page=-1'
```

Esperado **400**, obtido **400**.

```http
HTTP/1.1 400
Content-Type: application/json

{
  "dataHora": "2026-11-21T20:19:40.358Z",
  "status": 400,
  "erro": "Bad Request",
  "codigo": "PARAMETRO_INVALIDO",
  "mensagem": "O parâmetro 'page' deve ser um número inteiro maior ou igual a 0."
}
```

Verificações:

- ✅ codigo PARAMETRO_INVALIDO
- ✅ a mensagem cita o parâmetro page

#### 26. Tamanho zero ✅

```bash
curl --location '{{baseUrl}}/api/v1/materias?size=0'
```

Esperado **400**, obtido **400**.

```http
HTTP/1.1 400
Content-Type: application/json

{
  "dataHora": "2026-11-21T20:19:40.386Z",
  "status": 400,
  "erro": "Bad Request",
  "codigo": "PARAMETRO_INVALIDO",
  "mensagem": "O parâmetro 'size' deve ser um inteiro maior que zero."
}
```

Verificações:

- ✅ codigo PARAMETRO_INVALIDO
- ✅ a mensagem cita o parâmetro size

#### 27. Filtro ativo inválido ✅

```bash
curl --location '{{baseUrl}}/api/v1/materias?ativo=talvez'
```

Esperado **400**, obtido **400**.

```http
HTTP/1.1 400
Content-Type: application/json

{
  "dataHora": "2026-11-21T20:19:40.416Z",
  "status": 400,
  "erro": "Bad Request",
  "codigo": "PARAMETRO_INVALIDO",
  "mensagem": "O parâmetro 'ativo' deve ser true ou false."
}
```

Verificações:

- ✅ codigo PARAMETRO_INVALIDO

#### 28. Método não permitido na coleção (DELETE em /materias) ✅

```bash
curl --location --request DELETE '{{baseUrl}}/api/v1/materias'
```

Esperado **405**, obtido **405**.

```http
HTTP/1.1 405
Allow: POST, GET
Content-Type: application/json

{
  "dataHora": "2026-11-21T20:19:40.443Z",
  "status": 405,
  "erro": "Method Not Allowed",
  "codigo": "METODO_NAO_PERMITIDO",
  "mensagem": "Método HTTP não permitido para este recurso."
}
```

Verificações:

- ✅ codigo METODO_NAO_PERMITIDO
- ✅ Allow lista GET e POST

### GET — buscar

#### 29. Buscar por id ✅

```bash
curl --location '{{baseUrl}}/api/v1/materias/{{materiaId}}'
```

Esperado **200**, obtido **200**.

```http
HTTP/1.1 200
Content-Type: application/json

{
  "id": "bb6f863d-598f-4496-9234-7454a055052c",
  "nome": "Matemática",
  "descricao": "Álgebra e geometria",
  "ativo": true,
  "criadoEm": "2026-11-21T20:19:39.228Z",
  "atualizadoEm": "2026-11-21T20:19:39.228Z"
}
```

Verificações:

- ✅ id igual ao pedido
- ✅ nome Matemática

#### 30. Id inexistente ✅

```bash
curl --location '{{baseUrl}}/api/v1/materias/11111111-2222-3333-4444-555555555555'
```

Esperado **404**, obtido **404**.

```http
HTTP/1.1 404
Content-Type: application/json

{
  "dataHora": "2026-11-21T20:19:40.546Z",
  "status": 404,
  "erro": "Not Found",
  "codigo": "MATERIA_NAO_ENCONTRADA",
  "mensagem": "Matéria não encontrada."
}
```

Verificações:

- ✅ codigo MATERIA_NAO_ENCONTRADA

#### 31. Id malformado (não é UUID) ✅

```bash
curl --location '{{baseUrl}}/api/v1/materias/isto-nao-e-um-uuid'
```

Esperado **400**, obtido **400**.

```http
HTTP/1.1 400
Content-Type: application/json

{
  "dataHora": "2026-11-21T20:19:40.573Z",
  "status": 400,
  "erro": "Bad Request",
  "codigo": "PARAMETRO_INVALIDO",
  "mensagem": "O parâmetro 'id' tem um valor inválido."
}
```

Verificações:

- ✅ codigo PARAMETRO_INVALIDO
- ✅ não devolve o valor enviado

### PUT — atualizar

#### 32. Trocar nome e descrição (ignora o ativo enviado) ✅

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
  "id": "bb6f863d-598f-4496-9234-7454a055052c",
  "nome": "Matemática Aplicada",
  "descricao": "Nova descrição",
  "ativo": true,
  "criadoEm": "2026-11-21T20:19:39.228Z",
  "atualizadoEm": "2026-11-21T20:19:40.634Z"
}
```

Verificações:

- ✅ nome e descrição trocados
- ✅ ativo não mudou (continua true)

#### 33. Descrição omitida: PUT é substituição, vira nula (D61) ✅

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
  "id": "bb6f863d-598f-4496-9234-7454a055052c",
  "nome": "Matemática Aplicada",
  "descricao": null,
  "ativo": true,
  "criadoEm": "2026-11-21T20:19:39.228Z",
  "atualizadoEm": "2026-11-21T20:19:40.704Z"
}
```

Verificações:

- ✅ o próprio nome é permitido
- ✅ descricao virou nula

#### 34. Nome que pertence a outra matéria ✅

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
  "dataHora": "2026-11-21T20:19:40.746Z",
  "status": 409,
  "erro": "Conflict",
  "codigo": "MATERIA_NOME_DUPLICADO",
  "mensagem": "Já existe uma matéria com esse nome."
}
```

Verificações:

- ✅ codigo MATERIA_NOME_DUPLICADO

#### 35. Id inexistente ✅

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
  "dataHora": "2026-11-21T20:19:40.781Z",
  "status": 404,
  "erro": "Not Found",
  "codigo": "MATERIA_NAO_ENCONTRADA",
  "mensagem": "Matéria não encontrada."
}
```

Verificações:

- ✅ codigo MATERIA_NAO_ENCONTRADA

#### 36. Nome inválido (só espaços) ✅

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
  "dataHora": "2026-11-21T20:19:40.820Z",
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

#### 37. Id malformado ✅

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
  "dataHora": "2026-11-21T20:19:40.880Z",
  "status": 400,
  "erro": "Bad Request",
  "codigo": "PARAMETRO_INVALIDO",
  "mensagem": "O parâmetro 'id' tem um valor inválido."
}
```

Verificações:

- ✅ codigo PARAMETRO_INVALIDO

### PATCH — desativar e ativar

#### 38. Desativar ✅

```bash
curl --location --request PATCH '{{baseUrl}}/api/v1/materias/{{materiaId}}/disable'
```

Esperado **200**, obtido **200**.

```http
HTTP/1.1 200
Content-Type: application/json

{
  "id": "bb6f863d-598f-4496-9234-7454a055052c",
  "nome": "Matemática Aplicada",
  "descricao": null,
  "ativo": false,
  "criadoEm": "2026-11-21T20:19:39.228Z",
  "atualizadoEm": "2026-11-21T20:19:40.924Z"
}
```

Verificações:

- ✅ ativo é false

#### 39. Desativar de novo (idempotente) ✅

```bash
curl --location --request PATCH '{{baseUrl}}/api/v1/materias/{{materiaId}}/disable'
```

Esperado **200**, obtido **200**.

```http
HTTP/1.1 200
Content-Type: application/json

{
  "id": "bb6f863d-598f-4496-9234-7454a055052c",
  "nome": "Matemática Aplicada",
  "descricao": null,
  "ativo": false,
  "criadoEm": "2026-11-21T20:19:39.228Z",
  "atualizadoEm": "2026-11-21T20:19:40.924Z"
}
```

Verificações:

- ✅ mesmo resultado da chamada anterior
- ✅ atualizadoEm não mudou (D62)

#### 40. Ativar ✅

```bash
curl --location --request PATCH '{{baseUrl}}/api/v1/materias/{{materiaId}}/enable'
```

Esperado **200**, obtido **200**.

```http
HTTP/1.1 200
Content-Type: application/json

{
  "id": "bb6f863d-598f-4496-9234-7454a055052c",
  "nome": "Matemática Aplicada",
  "descricao": null,
  "ativo": true,
  "criadoEm": "2026-11-21T20:19:39.228Z",
  "atualizadoEm": "2026-11-21T20:19:41.001Z"
}
```

Verificações:

- ✅ ativo é true

#### 41. Ativar de novo (idempotente) ✅

```bash
curl --location --request PATCH '{{baseUrl}}/api/v1/materias/{{materiaId}}/enable'
```

Esperado **200**, obtido **200**.

```http
HTTP/1.1 200
Content-Type: application/json

{
  "id": "bb6f863d-598f-4496-9234-7454a055052c",
  "nome": "Matemática Aplicada",
  "descricao": null,
  "ativo": true,
  "criadoEm": "2026-11-21T20:19:39.228Z",
  "atualizadoEm": "2026-11-21T20:19:41.001Z"
}
```

Verificações:

- ✅ mesmo resultado da chamada anterior
- ✅ atualizadoEm não mudou (D62)

#### 42. Desativar id inexistente ✅

```bash
curl --location --request PATCH '{{baseUrl}}/api/v1/materias/11111111-2222-3333-4444-555555555555/disable'
```

Esperado **404**, obtido **404**.

```http
HTTP/1.1 404
Content-Type: application/json

{
  "dataHora": "2026-11-21T20:19:41.071Z",
  "status": 404,
  "erro": "Not Found",
  "codigo": "MATERIA_NAO_ENCONTRADA",
  "mensagem": "Matéria não encontrada."
}
```

Verificações:

- ✅ codigo MATERIA_NAO_ENCONTRADA

#### 43. Ativar id inexistente ✅

```bash
curl --location --request PATCH '{{baseUrl}}/api/v1/materias/11111111-2222-3333-4444-555555555555/enable'
```

Esperado **404**, obtido **404**.

```http
HTTP/1.1 404
Content-Type: application/json

{
  "dataHora": "2026-11-21T20:19:41.121Z",
  "status": 404,
  "erro": "Not Found",
  "codigo": "MATERIA_NAO_ENCONTRADA",
  "mensagem": "Matéria não encontrada."
}
```

Verificações:

- ✅ codigo MATERIA_NAO_ENCONTRADA

#### 44. Id malformado ✅

```bash
curl --location --request PATCH '{{baseUrl}}/api/v1/materias/isto-nao-e-um-uuid/disable'
```

Esperado **400**, obtido **400**.

```http
HTTP/1.1 400
Content-Type: application/json

{
  "dataHora": "2026-11-21T20:19:41.149Z",
  "status": 400,
  "erro": "Bad Request",
  "codigo": "PARAMETRO_INVALIDO",
  "mensagem": "O parâmetro 'id' tem um valor inválido."
}
```

Verificações:

- ✅ codigo PARAMETRO_INVALIDO

### DELETE — apagar

#### 45. Apagar ✅

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

#### 46. Buscar depois de apagar ✅

```bash
curl --location '{{baseUrl}}/api/v1/materias/{{materiaId}}'
```

Esperado **404**, obtido **404**.

```http
HTTP/1.1 404
Content-Type: application/json

{
  "dataHora": "2026-11-21T20:19:41.246Z",
  "status": 404,
  "erro": "Not Found",
  "codigo": "MATERIA_NAO_ENCONTRADA",
  "mensagem": "Matéria não encontrada."
}
```

Verificações:

- ✅ codigo MATERIA_NAO_ENCONTRADA

#### 47. Apagar de novo ✅

```bash
curl --location --request DELETE '{{baseUrl}}/api/v1/materias/{{materiaId}}'
```

Esperado **404**, obtido **404**.

```http
HTTP/1.1 404
Content-Type: application/json

{
  "dataHora": "2026-11-21T20:19:41.272Z",
  "status": 404,
  "erro": "Not Found",
  "codigo": "MATERIA_NAO_ENCONTRADA",
  "mensagem": "Matéria não encontrada."
}
```

Verificações:

- ✅ codigo MATERIA_NAO_ENCONTRADA

#### 48. Id malformado ✅

```bash
curl --location --request DELETE '{{baseUrl}}/api/v1/materias/isto-nao-e-um-uuid'
```

Esperado **400**, obtido **400**.

```http
HTTP/1.1 400
Content-Type: application/json

{
  "dataHora": "2026-11-21T20:19:41.298Z",
  "status": 400,
  "erro": "Bad Request",
  "codigo": "PARAMETRO_INVALIDO",
  "mensagem": "O parâmetro 'id' tem um valor inválido."
}
```

Verificações:

- ✅ codigo PARAMETRO_INVALIDO

### Outras

#### 49. Rota inexistente ✅

```bash
curl --location '{{baseUrl}}/api/v1/nao-existe'
```

Esperado **404**, obtido **404**.

```http
HTTP/1.1 404
Content-Type: application/json

{
  "dataHora": "2026-11-21T20:19:41.324Z",
  "status": 404,
  "erro": "Not Found",
  "codigo": "RECURSO_NAO_ENCONTRADO",
  "mensagem": "Recurso não encontrado."
}
```

Verificações:

- ✅ codigo RECURSO_NAO_ENCONTRADO
- ✅ resposta em JSON (nunca HTML)

#### 50. Rota inexistente em método com corpo (POST) ✅

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
  "dataHora": "2026-11-21T20:19:41.376Z",
  "status": 404,
  "erro": "Not Found",
  "codigo": "RECURSO_NAO_ENCONTRADO",
  "mensagem": "Recurso não encontrado."
}
```

Verificações:

- ✅ 404 e não 405

### Limpeza

#### 51. Apagar a matéria Física ✅

```bash
curl --location --request DELETE '{{baseUrl}}/api/v1/materias/{{outraMateriaId}}'
```

Esperado **204**, obtido **204**.

```http
HTTP/1.1 204

(sem corpo)
```

#### 52. Apagar a matéria Química ✅

```bash
curl --location --request DELETE '{{baseUrl}}/api/v1/materias/{{materiaIdC}}'
```

Esperado **204**, obtido **204**.

```http
HTTP/1.1 204

(sem corpo)
```

#### 53. Apagar a matéria História ✅

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
- `GET /api/v1/materias` lista com paginação (`page`, `size` até 50), ordenação (`sort` por `nome` ou `criadoEm`, com desempate por id) e filtros (`nome` contém, sem distinguir maiúsculas e acentos; `ativo`).
- Não cobertos aqui, por não existirem ainda: restrições por papel na listagem, 401/403 e o 409 do `DELETE` por subtópicos.
