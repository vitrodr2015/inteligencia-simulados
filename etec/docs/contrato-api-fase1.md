Contrato da API — Fase 1 (v1.0, aprovada)

O contrato é revisado e aprovado antes de qualquer código. Ele é rascunho até a aprovação; a v1.0 é a versão aprovada (D83), e depois dela qualquer mudança é registrada na seção 10. Este é o documento completo da API da fase 1 e não depende de outros.

Estado: versão aprovada (v1.0). Decisões D1 a D142 decididas, exceto D22 e D24 (pendências externas).

1. Convenções gerais (valem para todas as rotas)
Tema	Decisão
Base e formato	/api/v1. As rotas de /api/v1 respondem SOMENTE JSON (D71): Accept de XML, YAML ou qualquer outro formato = 406, no formato de erro padrão, em JSON. Exceção declarada: a documentação OpenAPI (/v3/api-docs, /v3/api-docs.yaml e /swagger-ui) existe só em desenvolvimento e homologação e pode responder YAML ou HTML. Nas respostas COM corpo, o Content-Type é exatamente application/json, sem o parâmetro charset, mas o corpo é sempre UTF-8 (o padrão do JSON); o 204 não tem corpo nem Content-Type. O JSON sai compacto, sem espaços nem quebras de linha (D74). Os exemplos JSON deste documento estão formatados para leitura; a resposta real é compacta. Todas as rotas das tabelas deste documento são relativas a /api/v1 (D78), exceto as da seção 7 (actuator), que ficam fora dele; todo Location inclui o prefixo /api/v1 (D78)
Ordem e nulos	A ordem dos campos de SAÍDA do JSON é fixa: é a ordem em que os campos aparecem escritos neste contrato, da esquerda para a direita, inclusive nos objetos entre chaves (questoes[], respostas[], itens[] e o resultado da tentativa); o envelope da lista e o ErrorResponse têm a ordem própria descrita abaixo (D72). Campo nulo de DTO de resposta sai sempre no JSON como null (por exemplo "descricao":null), nunca omitido (D73). Campo que o papel não pode ver não existe no JSON, e não sai como null: as questões da tentativa do aluno usam um DTO próprio, sem gabarito nem explicacao (D75)
Erros que podem ocorrer em qualquer rota	401 e 403 (rota ou método SEM regra de acesso é negado: 401 ao anônimo e 403 a quem está autenticado, inclusive ADMIN, porque a rota nova nasce negada, D121; em /actuator, fora dos três caminhos de saúde, e em /error vale a D113), 404 (recurso inexistente, só depois da autorização, ou rota que a segurança libera mas que não tem recurso mapeado), 405 (método que a segurança libera para a rota mas que o controller não atende; hoje não ocorre, porque o método sem regra é negado com 401 ou 403), 406 (formato não aceito, D71), 415 (corpo que não é application/json, D112), 400 REQUISICAO_INVALIDA (URL recusada pelo firewall de segurança, D124) e 500, sempre no formato de erro padrão, em JSON, mesmo com Accept de XML ou YAML. Por valerem para toda rota, não aparecem nas colunas de erro dos endpoints
Ids	Campo id é um UUID (o id numérico do banco nunca sai). UUID malformado = 400; inexistente = 404
Data e hora	ISO-8601 em UTC com exatamente 3 casas de milissegundos, mesmo quando são zero (2026-11-20T15:04:05.123Z e 2026-11-20T15:04:05.000Z). As datas são gravadas e devolvidas com precisão de milissegundos: a aplicação trunca na origem, para o banco não arredondar (D63)
Data simples	uuuu-MM-dd (aaaa-mm-dd), leitura estrita: data inexistente ou fora do formato (2000-02-30, 2000-2-3) = 400, com campos apontando o campo e a mensagem "Data inválida. Use o formato aaaa-mm-dd."; nunca é corrigida em silêncio (D60)
Entidade	Nunca sai pela API: DTOs de entrada e de saída. Campos de saída que não são do cliente (id, ativo, status, datas, nota) são ignorados se vierem no corpo. Campo de entrada que não muda depois de criado (materiaId, email, dataNascimento, provaId, numero...): valor igual ao atual é aceito; valor diferente = 400 com codigo CAMPO_IMUTAVEL. Como a comparação é com o valor atual, esse 400 é a última validação: só é avaliado depois da existência (404) e do conflito de estado (409) (D27, D42, D48, D79)
Referência no corpo	Se o corpo aponta para um recurso que não existe (ex.: materiaId, provaId em POST /tentativas), responde 400. Se a rota aponta (/materias/{id}), responde 404 (D14)
Corpo da requisição	Só application/json, com charset=utf-8 tolerado; outro tipo = 415 (D112). Campo desconhecido no corpo é ignorado; tipo errado ou JSON ilegível = 400; campo obrigatório ausente ou null = 400 com campos (D86)
Ordem de validação	autenticação e autorização (401/403) → 400 de formato (corpo e id) → existência (404) → conflito de estado (409) → 400 CAMPO_IMUTAVEL → alteração (D17, D79). O limite de abuso por origem (429) vem antes de tudo; o de e-mail e origem do signin vem depois de o corpo ser lido (D128, D129)
Autorização antes da existência	Quem não pode recebe 403 até para um id que não existe
Idempotência	PUT de resposta e de gabarito, disable/enable, publish e finalizar podem ser repetidos com o mesmo resultado. O logout também (D15). Repetir disable/enable devolve o mesmo resultado e não altera atualizadoEm (D62)
Nome e textos curtos	Campo nome (de matéria, subtópico, usuário e demais recursos) e os demais textos curtos (descricao, titulo, banca...): o servidor remove os espaços das pontas ANTES de validar; nome vazio depois disso = 400 (D57, D104). A unicidade é pela collation do banco: os espaços internos contam e não há normalização Unicode própria; o subtópico ignora caixa e acento como a matéria (D104)
Criação (201)	Location relativo, sempre com o prefixo (/api/v1/recurso/{id}, D78), e o corpo traz o recurso completo, não só o id (D58). Exceção: POST /auth/register, que não tem Location (o recurso só existe em /me)
PUT	É substituição: campo opcional omitido no corpo vira nulo (por exemplo, descricao da matéria) (D61). Exceções, por decisões próprias: PUT /admin/provas/{id}/gabarito, que é parcial (D13), e PUT /tentativas/{id}/respostas/{questaoId}, em que tempoGastoMs omitido mantém o valor salvo (D30). Campos que o servidor controla (ativo, status, datas) nunca mudam por PUT. A substituição é total, inclusive de gabarito, explicacao e fonteUrl: um PUT de questão que omite o gabarito o apaga (D111; ver a seção 5)
Limite de abuso	POST de /auth/register, /auth/signin, /auth/refresh e /auth/logout respondem 429 LIMITE_EXCEDIDO, com Retry-After (segundos, arredondado para cima), Cache-Control: no-store e o ErrorResponse padrão, ao exceder o limite (D128, D129). Três contadores, todos configuráveis por app.rate-limit.*: (1) toda requisição POST dessas quatro rotas, por origem: 120 por minuto, só proteção contra enxurrada; (2) falhas de signin por e-mail normalizado e origem: 5 por 15 minutos; (3) falhas de signin por origem: 50 por 15 minutos. Janela fixa que começa na primeira ocorrência e expira sozinha: nunca há bloqueio permanente. O contador sobe ANTES de conferir a senha e fica no banco, compartilhado entre instâncias; o sucesso do signin apaga o de e-mail e origem e devolve uma ocorrência ao de origem. A chave do e-mail é o e-mail normalizado, exista ele ou não: a resposta e o tempo do 429 são os mesmos para e-mail existente e inexistente. O limite por origem vem ANTES do corpo (antes do 400 e do 401); o de e-mail e origem vem depois de o corpo ser lido (corpo sem e-mail é 400 e não conta). O preflight de CORS não conta. A origem é o endereço do socket; o X-Forwarded-For só vale com proxy confiável configurado por propriedade (D130)
Cabeçalhos	As respostas de signin, de refresh e de troca de senha levam Cache-Control: no-store (D114, D139). Toda resposta que passa pela cadeia de segurança leva X-Content-Type-Options: nosniff e X-Frame-Options: DENY, e nenhuma leva X-Powered-By nem versão do servidor no Server (D127). Exceção: as respostas que o servidor web dá antes da cadeia de segurança (seção 7) não levam esses cabeçalhos
Formatação nos textos	Os textos de questão (enunciado, textoApoio, alternativas[].texto, explicacao) aceitam só as marcas <b>, <u>, <i>, <sup>, <sub> e <br>, exatas, em minúsculas e com <br> sem barra (D12, D32, D98). Só as entidades &lt;, &gt; e &amp; são aceitas, e os caracteres <, > e & literais se escrevem com elas. Atributos nas tags e tags mal fechadas ou mal aninhadas = 400. O servidor recusa (400) qualquer outra marcação HTML e o front também limpa por segurança. O HTML só vale nesses textos de questão. Os limites de tamanho contam pontos de código (D98)
CORS	As origens permitidas vêm de configuração por ambiente (D34). Métodos GET, POST, PUT, PATCH, DELETE e OPTIONS; cabeçalhos Authorization e Content-Type; sem credenciais; max-age 3600; o preflight OPTIONS não exige autenticação (D100). Detalhes na D123: propriedade app.cors.allowed-origins, formato validado na subida, só https em produção, Location exposto e 403 ACESSO_NEGADO para origem não permitida

Formato de erro (ErrorResponse) — o mesmo em todo status de erro, inclusive 401 e 403, em toda a aplicação, exceto /actuator (formato do próprio framework) e a documentação OpenAPI (D82):

json
{ "dataHora": "2026-11-20T15:04:05.123Z", "status": 409, "erro": "Conflict", "codigo": "MATERIA_NOME_DUPLICADO", "mensagem": "Já existe uma matéria com esse nome." }

O campo codigo é estável (texto em MAIÚSCULAS com sublinhado, por exemplo TENTATIVA_EM_ANDAMENTO); o front decide pelo codigo, nunca pelo texto da mensagem (D33). Os campos saem na ordem dataHora, status, erro, codigo, mensagem, campos, detalhes (D72); campos e detalhes são omitidos quando nulos, e os demais sempre saem (D73). O campo detalhes (opcional, um objeto) leva dados estruturados de alguns erros (D44): no 409 de tentativa em andamento, { "tentativaId": "..." }; no 409 do publish da prova, { "questoesPendentes": [3, 7] }, com os números das questões pendentes; no 409 de reimportação, { "numerosEmConflito": [4, 9] }. Os valores de codigo estão no apêndice A (catálogo de códigos de erro), e cada recurso novo acrescenta os seus códigos ao catálogo ao ser implementado (D84). Em erros 400 de validação, campos (opcional): [ { "campo": "nome", "mensagem": "O nome deve ter de 2 a 100 caracteres." } ] (decisão D1), com no máximo uma mensagem por campo. O texto da mensagem não é contratual: o front decide pelo codigo (D59). O 500 devolve mensagem genérica; a pilha só vai para o log.

Lista paginada — parâmetros page (a partir de 0), size (padrão 20, máximo 50, valor maior é reduzido ao máximo) e sort no formato campo,direção (por exemplo sort=nome,desc; sem direção vale asc e a direção aceita qualquer caixa, D64; só campos da lista de cada recurso; outro campo = 400; um único sort por pedido, e mais de um = 400; o nome do campo diferencia maiúsculas, e NOME = 400, D101). page começa em 0 e zero é válido; page negativo ou não numérico = 400; size zero, negativo ou não numérico = 400 (D76). Página além do fim devolve 200 com lista vazia e os totais corretos, e o pagina do envelope devolve a página pedida (D101); se página × tamanho passar de 2³¹-1, a resposta também é 200 vazia com os totais corretos, e o pagina do envelope é limitado à maior página possível (D70). Nos filtros, nome é "contém", ignorando maiúsculas e acentos (D29), e nome em branco equivale a filtro ausente (D65); ativo só aceita true ou false, em qualquer caixa, e outros valores = 400 (D66, D101). Filtro com enum inválido, ano não numérico ou id malformado (materiaId) = 400, e STUDENT filtrando por materiaId de matéria inativa recebe lista vazia (D103). Resposta:

json
{ "conteudo": [ ... ], "pagina": 0, "tamanho": 20, "totalElementos": 135, "totalPaginas": 7 }

Desempate sempre pelo id numérico interno (o id do banco, que nunca sai pela API), em ordem crescente, inclusive quando o sort é desc (nas questões, pelo id interno e depois pela versao), para a paginação ser estável (D67, D93). O campo tamanho do envelope é o tamanho aplicado (já reduzido ao máximo), e não a quantidade de itens da página (D68). A ordem alfabética, quando o sort é por texto (como nome), segue a collation do MySQL, que ignora acento e caixa (D69). O envelope sai na ordem do exemplo acima: conteudo, pagina, tamanho, totalElementos, totalPaginas (D72).

Autenticação: Authorization: Bearer <accessToken>. Papéis: STUDENT, GUARDIAN (reservado, sem rotas próprias na fase 1) e ADMIN. Rota nova nasce negada. Sem token válido = 401; sem permissão = 403. A autorização usa o papel gravado no banco; o claim papel do token é só informativo (D87).

2. Quem pode o quê
Operação	Anônimo	STUDENT	ADMIN
POST /auth/register, /auth/signin, /auth/refresh, /auth/logout; GET /termos/vigente	✅	✅	✅
GET/PATCH/DELETE /me	❌	✅	✅
POST /me/senha	❌	✅	✅
GET de matérias, subtópicos e provas	❌	✅ (só ativos; provas só PUBLICADAS)	✅ (todos)
Escrever matérias e subtópicos; publicar prova	❌	❌	✅
/admin/** (provas, questões, importação, gabarito)	❌	❌	✅
/tentativas/**	❌	✅ (só as próprias)	❌
GET /actuator/health, /actuator/health/liveness e /actuator/health/readiness	✅	✅	✅
Outros caminhos de /actuator e /error	❌ (401)	❌ (403)	❌ (403)

Anotação da tabela quem pode o quê:
- O que não consta da tabela é NEGADO (D121): rota ou método sem regra responde 401 ao anônimo e 403 a quem está autenticado, inclusive ADMIN, mesmo que a rota nem exista. O 404 é só para recurso inexistente, depois da autorização.
- Rota PREMIUM (futura): além do papel, passa pelo serviço de entitlements; sem o recurso no plano, 403 ENTITLEMENT_NEGADO (D140, D141). Nenhuma rota premium existe ainda.
- GUARDIAN não tem nenhuma permissão na fase 1 (D122): 403 em tudo que exige papel, inclusive GET /me; as rotas públicas funcionam.
- O HEAD segue a regra do GET da mesma rota.
- O OPTIONS de preflight de CORS (com Origin e Access-Control-Request-Method) é público (D100); um OPTIONS comum é negado.
- A autorização vem antes da existência, do formato e do conflito: STUDENT recebe 403 numa escrita mesmo com id inexistente, id malformado ou corpo inválido.

Tentativa de outro aluno responde 404 (não revela que existe).

3. Autenticação e conta

Campos de Usuário

Campo	Tipo	Regra	Quem preenche
id	UUID	gerado	saída
nome	texto	obrigatório; o servidor remove os espaços das pontas antes de validar; depois disso, 2 a 100 caracteres (vazio = 400, D57)	entrada e saída
email	texto	obrigatório, formato válido, até 255, único; o servidor remove os espaços das pontas e converte para minúsculas antes de validar e antes do 409 (D105)	entrada e saída
senha	texto	obrigatório, 8 a 72 bytes (limite do BCrypt)	só entrada (nunca sai)
dataNascimento	data	obrigatória, no passado (em UTC) e a partir de 1900-01-01 (D106), no formato aaaa-mm-dd (data inválida = 400, D60)	entrada e saída
aceiteTermosVersao	texto	obrigatório, igual à versão devolvida por GET /termos/vigente; diferente = 400 (D23)	só entrada
papel	enum	sempre STUDENT no cadastro; o corpo não escolhe	saída
menor	booleano	calculado na leitura (D106): menor de 18 anos. Na fase 1 é só uma marca, não muda comportamento (D24)	saída
criadoEm	data/hora	UTC	saída

Endpoints

Verbo	Rota	Sucesso	Erros
GET	/termos/vigente (pública)	200 {versao, url}; os dois valores vêm de propriedades por ambiente (D94)	—
POST	/auth/register	201 com o usuário (mesmo corpo de GET /me), sem tokens e sem Location; o aluno entra depois pelo signin (D23)	400, 409 (e-mail já cadastrado, D37), 429
POST	/auth/signin (entrada {email, senha})	200 {accessToken, refreshToken, tokenType, expiresIn}; tokenType é "Bearer"; expiresIn em segundos (900, D39, D96, D107)	400, 401 (mesma resposta e tempo parecido para qualquer falha), 429
POST	/auth/refresh (entrada {refreshToken}, nunca na URL)	200 com o mesmo corpo do signin (D96; novo par; o refresh usado é invalidado; reusar um refresh já ROTACIONADO dá 401 e revoga todos os refresh do usuário e derruba os access vivos, D39, D49, D133, D134)	400, 401 (inválido, vencido, revogado ou do tipo errado, inclusive um access token, D107), 429
POST	/auth/logout (anônimo; entrada {refreshToken}, que é a credencial, D15)	204 para qualquer texto presente, inclusive malformado, desconhecido, vencido, já revogado ou de outro tipo; repetir não dá erro; revoga só o refresh enviado (D39, D107)	400 (refreshToken ausente ou que não é texto), 429
GET	/me	200	401, 403
PATCH	/me (só nome; email e dataNascimento com o valor atual são aceitos, diferentes = 400 CAMPO_IMUTAVEL, D48)	200 com o usuário completo (D96)	400, 401
POST	/me/senha (entrada {senhaAtual, novaSenha}; só STUDENT e ADMIN, GUARDIAN = 403, D122)	204 sem corpo e sem tokens, com Cache-Control: no-store; todos os tokens antigos, access e refresh, deixam de valer (D139)	400 (VALIDACAO_FALHOU com campos; SENHA_ATUAL_INCORRETA), 401, 403, 429
DELETE	/me	204 (exclusão de conta com anonimização, ver D9 e a subseção 3.2)	401, 409 (último ADMIN)

Não existem na fase 1: recuperação de senha, login com Google, criação de ADMIN por API (o primeiro admin nasce por bootstrap controlado, ver 3.2), vínculo com responsável, envio de e-mail.

3.1 JWT (D39)
Assinatura HS256. Access token de 15 minutos, com os claims iss (emissor, propriedade), sub (UUID do usuário), papel, tipo=access, jti, iat, exp e versao (a versão do token do usuário na emissão, D118, D136); o refresh leva os mesmos, com tipo=refresh. Refresh token de 24 horas, tipo=refresh, com o jti guardado no banco; o tempo de vida é propriedade configurável. expiresIn em segundos (900). Cada /auth/refresh invalida o refresh usado. O reuso de um refresh já invalidado revoga todos os refresh do usuário; não existe conceito de família por login (D49). O logout revoga só o refresh enviado. A cada requisição o filtro consulta o banco para confirmar que o usuário existe e não foi excluído: o token não é a fonte da verdade (D26). A autorização usa o papel do banco; o claim papel é só informativo (D87). O tokenType é "Bearer" (D107). O segredo de assinatura vem só de variável de ambiente, sem valor padrão, é tratado como texto UTF-8 (não como Base64) e tem no mínimo 32 bytes. Três coisas derrubam um token: a conta excluída (usuario.excluido_em preenchido, D26), a versão do token diferente da do usuário (usuario.versao_token, D136: access e refresh sem o claim versao também são recusados) e, só para o refresh, a linha revogada (D133). A troca de senha, o reuso de um refresh já rotacionado e o encerramento de todas as sessões incrementam a versão e derrubam, de uma vez, todos os tokens já emitidos, sem relógio. Não existe estado de suspensão. O logout revoga só o refresh enviado e NÃO derruba o access token vivo, que vale até vencer (no máximo 15 minutos, D135).

3.2 Exclusão de conta e primeiro admin (D26, D45)
A anonimização grava nome = "Usuário removido", e-mail = removido-{uuid}@invalid (então o e-mail original pode ser recadastrado como conta nova), senha inutilizada (o hash deixa de servir para o login) e dataNascimento = null (D92). Isso exige uma migration futura que torne a coluna da data de nascimento anulável; a migration já aplicada não se edita (D92). A migration que torna usuario.data_nascimento anulável é pré-requisito da tarefa do DELETE /me e vem antes dela, numerada na ordem da fila de migrations (D92). Todos os refresh do usuário são revogados na hora. Tentativas em andamento viram EXPIRADA, com finalizadaEm = o menor entre o prazoEm e o momento da exclusão (D88). O último ADMIN não pode se excluir (409). Na fase 1 há um único admin (não existe criação de ADMIN por API), então ele nunca se exclui (D81). Bootstrap do admin: as variáveis de ambiente ADMIN_EMAIL e ADMIN_SENHA criam o admin só se ainda não existir nenhum; se já existe admin, as variáveis são ignoradas. Sem as variáveis e sem nenhum admin: em desenvolvimento e teste, só um aviso no log; em produção, a aplicação não sobe (D45). O admin nasce com nome "Administrador", dataNascimento 2000-01-01 e aceiteTermosVersao igual à versão vigente; ADMIN_EMAIL e ADMIN_SENHA são validados como num cadastro comum (e-mail válido, senha de 8 a 72 bytes) e, se forem inválidos em produção, a aplicação não sobe (D91). Nunca vão para o repositório. Refinamentos do Módulo 12 (D125 e D126): a senha do admin tem de 12 a 72 bytes em UTF-8, sem espaço nas pontas e sem \r nem \n em posição alguma (D125, refina D91); o bootstrap só cria se não existir ADMIN ativo (excluido_em nulo), ignora as variáveis quando já existe admin (inclusive para trocar a senha), nunca promove um usuário que já existe, roda antes de o servidor abrir e só o perfil de produção derruba a subida; nenhuma senha, hash ou e-mail vai para o log (D126). Limitação conhecida: duas subidas simultâneas com ADMIN_EMAIL diferentes podem criar dois administradores; com o mesmo e-mail só um nasce (revisitar no Módulo 17). Recuperação de senha do admin: não há rota; em ambiente controlado remove-se o admin por SQL e sobe-se de novo com as variáveis (README).

4. Catálogo
4.1 Matéria
Campo	Tipo	Regra	Quem preenche
id	UUID	gerado	saída
nome	texto	obrigatório; o servidor remove os espaços das pontas antes de validar; depois disso, 2 a 100 caracteres (vazio = 400, D57); único (ignora maiúsculas e acentos)	entrada e saída
descricao	texto	opcional, até 255; omitida no PUT, vira nula (D61)	entrada e saída
ativo	booleano	nasce true; só muda por disable/enable. Desativar uma matéria não desativa os subtópicos em cascata	saída
criadoEm, atualizadoEm	data/hora	UTC, com precisão de milissegundos (D63). atualizadoEm não muda quando disable/enable é repetido (D62)	saída
Verbo	Rota	Sucesso	Erros
POST	/materias	201 + Location relativo e a matéria completa (D58)	400, 401, 403, 409 (nome existe)
GET	/materias (filtros nome e ativo; sort: nome, criadoEm; padrão nome; sem direção vale asc, D64; nome em branco = filtro ausente, D65; ativo só aceita true ou false, em qualquer caixa, D66, D101; o desempate pelo id interno é sempre crescente, D67, D93; a ordem por nome segue a collation do MySQL, D69). STUDENT só vê ativas; ativo=false enviado por STUDENT = 400 (D28, D47)	200 lista paginada	400, 401, 403
GET	/materias/{id} (STUDENT: matéria inativa = 404 MATERIA_NAO_ENCONTRADA, o mesmo código da matéria inexistente, D103)	200	400, 401, 403, 404
PUT	/materias/{id} (substituição: troca nome e descricao, e a descricao omitida vira nula; nunca altera ativo, D61)	200 com a matéria completa (D96)	400, 401, 403, 404, 409
PATCH	/materias/{id}/disable e /enable	200 com a matéria completa (D96), idempotentes: repetir devolve o mesmo resultado e não altera atualizadoEm (D62)	400, 401, 403, 404
DELETE	/materias/{id}	204	400, 401, 403, 404, 409 (tem subtópicos, ativos ou não)
4.2 Subtópico
Campo	Tipo	Regra	Quem preenche
id	UUID	gerado	saída
materiaId	UUID	obrigatório na criação, deve existir (senão 400); não muda depois (valor igual ao atual é aceito; diferente = 400, codigo CAMPO_IMUTAVEL, D48); matéria desativada = 409, codigo MATERIA_INATIVA, na criação e quando o enable de fato ativaria um subtópico inativo; enable em subtópico já ativo responde 200 sem efeito; renomear (PUT) e desativar continuam permitidos (D54, D109)	entrada e saída
nome	texto	obrigatório; o servidor remove os espaços das pontas antes de validar; depois disso, 2 a 100 caracteres (vazio = 400, D57); único dentro da matéria, ignorando maiúsculas e acentos como a matéria (D104)	entrada e saída
ativo, criadoEm, atualizadoEm		como em Matéria	saída
Verbo	Rota	Sucesso	Erros
POST	/subtopicos	201 + Location relativo e o subtópico completo (D58)	400, 401, 403, 409 (nome já existe na matéria; ou matéria desativada, codigo MATERIA_INATIVA, D54)
GET	/subtopicos (filtros materiaId, nome, ativo; sort: nome, criadoEm; padrão nome asc, D77). STUDENT só vê subtópicos ativos de matéria ativa; ativo=false enviado por STUDENT = 400 (D28, D47)	200 lista paginada	400, 401, 403
GET	/subtopicos/{id} (STUDENT: subtópico inativo ou de matéria inativa = 404)	200	400, 401, 403, 404
PUT	/subtopicos/{id} (só nome)	200 com o subtópico completo (D96)	400, 401, 403, 404, 409
PATCH	/subtopicos/{id}/disable e /enable	200 com o subtópico completo (D96), idempotentes: repetir devolve o mesmo resultado e não altera atualizadoEm (D62)	400, 401, 403, 404, 409 (só quando o enable de fato ativaria um subtópico inativo de matéria desativada, codigo MATERIA_INATIVA; enable em subtópico já ativo responde 200 sem efeito, mesmo de matéria desativada, D109, D62)
DELETE	/subtopicos/{id}	204	400, 401, 403, 404, 409 (tem questões, de qualquer status)
4.3 Prova
Campo	Tipo	Regra	Quem preenche
id	UUID	gerado	saída
titulo	texto	obrigatório, até 150	vem da importação; saída
banca	texto	obrigatório, até 80	importação; saída
ano	inteiro	2000 a 2100	importação; saída
semestre	inteiro	1 ou 2	importação; saída
duracaoMinutos	inteiro	1 a 600 (a prova da ETEC tem 180)	importação; saída
quantidadeQuestoes	inteiro	calculado: o número de questões da prova, uma por numero, em qualquer status (D108)	saída
fonteUrl	texto	opcional, https, até 500	importação; saída
status	enum	RASCUNHO ou PUBLICADA	saída
publicadaEm, criadoEm	data/hora	UTC	saída

Uma prova é única por banca + ano + semestre (D20).

Verbo	Rota	Sucesso	Erros
GET	/provas (filtros ano, semestre; ADMIN também status; sort: ano, semestre; padrão ano decrescente; sort=ano sem direção vale asc, e isso é intencional, D102). STUDENT que envia status (qualquer valor) = 400 (D28, D47)	200 lista paginada	400, 401, 403
GET	/provas/{id} (STUDENT só vê PUBLICADA; senão 404)	200	400, 401, 403, 404
PATCH	/provas/{id}/publish (ADMIN)	200 com a prova completa (D96), idempotente	400, 401, 403, 404, 409 (sem questões, ou com questão ainda não publicada, o que inclui questão sem gabarito; detalhes.questoesPendentes lista os números das questões pendentes, D44)

Não existe POST de prova (ela nasce pela importação). PUT e DELETE existem só sob /admin/provas e só em RASCUNHO (seção 5, D21). Prova publicada é imutável: o que muda depois de publicada é a questão, por nova versão (D18). Uma prova só é publicada com o gabarito completo (D13).

5. Questões, importação e gabarito (somente ADMIN)

Campos de Questão

Campo	Tipo	Regra	Quem preenche
id	UUID	identifica a questão (estável entre versões)	saída
versao	inteiro	1, 2, ...; cada edição de questão publicada cria uma nova	saída
provaId	UUID	obrigatório, deve existir; não muda depois (valor igual ao atual é aceito; diferente = 400, codigo CAMPO_IMUTAVEL, D48)	entrada e saída
numero	inteiro	≥ 1, único dentro da prova; não muda depois (valor igual ao atual é aceito; diferente = 400, codigo CAMPO_IMUTAVEL; número errado: descartar o rascunho e reimportar, D42, D48)	entrada e saída
subtopicoId	UUID	obrigatório (na criação e na importação), deve existir e estar ativo (D80)	entrada e saída
textoApoio	texto	opcional, até 10.000 (o "Leia o texto..." que a questão usa; copiado em cada questão que o usa, D11)	entrada e saída
grupoApoio	inteiro	opcional, ≥ 1; questões da mesma prova com o mesmo valor compartilham o mesmo texto de apoio, para o app exibi-lo uma vez	entrada e saída
enunciado	texto	obrigatório (D80), até 10.000	entrada e saída
imagemUrl	texto	opcional, https, até 500, e o host precisa estar na propriedade app.imagens.hosts-permitidos, definida por ambiente (em desenvolvimento, localhost; onde as imagens ficam fisicamente fica para o módulo de infraestrutura; D36, D46) (charge, tirinha, mapa, gráfico)	entrada e saída
alternativas	lista	exatamente 5 itens {letra, texto}, com as letras A, B, C, D, E, cada uma uma vez e em qualquer ordem na entrada; na saída, de A a E (D2, D80, D90)	entrada e saída
gabarito	letra	uma das letras A a E. Opcional no rascunho, na criação e na importação; obrigatório para publicar (D13, D80)	entrada e saída
dificuldade	enum	FACIL, MEDIA, DIFICIL; opcional	entrada e saída
explicacao	texto	opcional, até 10.000	entrada e saída
status	enum	RASCUNHO, PUBLICADA, ARQUIVADA	saída
Verbo	Rota	Sucesso	Erros
POST	/admin/importacoes	200 {provaId, questoesCriadas, questoesIgnoradas}	400 (JSON inválido, qualquer questão inválida, numero repetido no payload, lista de questões vazia ou mais de 100 questões: tudo ou nada, D7, D55, D90), 401, 403, 409 (prova já publicada, ou questão do mesmo numero com conteúdo diferente; detalhes.numerosEmConflito lista os números, D40), 413 (corpo acima de 2 MB, D55)
PUT	/admin/provas/{id} (só RASCUNHO; troca titulo, banca, ano, semestre, duracaoMinutos e fonteUrl)	200 com a prova completa (D96)	400, 401, 403, 404, 409 (prova publicada, ou outra prova com a mesma banca + ano + semestre)
DELETE	/admin/provas/{id} (só RASCUNHO)	204 (apaga também as questões em rascunho da prova, D110)	400, 401, 403, 404, 409 (prova publicada, ou com questão já publicada alguma vez; nada é apagado junto, D43)
PUT	/admin/provas/{id}/gabarito (corpo { "itens": [ {numero, gabarito} ] })	200 {atualizadas}, idempotente; pode ser parcial	400 (letra inválida, numero repetido ou que não existe na prova; tudo ou nada), 401, 403, 404, 409 (prova publicada ou questão já publicada: um item de questão publicada derruba o lote inteiro, D110)
GET	/admin/questoes (filtros provaId, subtopicoId, status; status aceita só RASCUNHO e PUBLICADA, ARQUIVADA = 400; sort: só numero, padrão numero asc, D77). Traz só a versão mais recente de cada questão, e o filtro status vale para essa versão exibida (D56); a ARQUIVADA se lê por ?versao=n (D41)	200 lista paginada	400, 401, 403
GET	/admin/questoes/{id} (devolve o rascunho aberto, se houver, senão a última publicada; ?versao=n lê uma versão específica, inclusive ARQUIVADA)	200	400, 401, 403, 404 (questão ou versão inexistente)
PUT	/admin/questoes/{id}	200 com a questão completa (D96)	400 (inclui provaId ou numero com valor diferente do atual, codigo CAMPO_IMUTAVEL, avaliado por último, depois do 404 e do 409, D27, D42, D48, D79), 401, 403, 404, 409 (só RASCUNHO pode ser editada)
POST	/admin/questoes/{id}/versoes	201 + Location (/api/v1/admin/questoes/{id}, D78) e a nova versão no corpo (cópia em RASCUNHO, D96)	400, 401, 403, 404, 409 (já há rascunho aberto)
PATCH	/admin/questoes/{id}/publish	200 com a questão completa (D96), idempotente (publicar uma questão já publicada e sem rascunho aberto = 200 sem efeito, D110); a versão anterior vira ARQUIVADA	400, 401, 403, 404, 409 (subtópico desativado ou sem gabarito, D80)
DELETE	/admin/questoes/{id} (se nunca foi publicada, apaga a questão; se tem versão publicada, descarta só o rascunho aberto)	204	400, 401, 403, 404, 409 (questão publicada sem rascunho aberto)

Criação e importação de questão exigem subtopicoId (existente e ativo), enunciado e as 5 alternativas; o gabarito é opcional (D80). O PUT de questão e o PUT de prova são substituição total (D61, D111): o campo opcional omitido vira nulo, inclusive gabarito, explicacao e fonteUrl. Um PUT de questão que omite o gabarito o apaga, e essa advertência também vai no texto do Swagger do endpoint (D111).

Importação: entrada { prova: {titulo, banca, ano, semestre, duracaoMinutos, fonteUrl}, questoes: [ {numero, subtopicoId, textoApoio, grupoApoio, enunciado, imagemUrl, alternativas, gabarito, dificuldade, explicacao} ] }, com gabarito opcional. Tudo entra como RASCUNHO. A prova é identificada por banca + ano + semestre: reimportar uma prova em RASCUNHO reutiliza o provaId e atualiza titulo, duracaoMinutos e fonteUrl; se ela já estiver publicada, 409 (D20). Se já existe questão no mesmo numero com conteúdo diferente, responde 409 listando os números em conflito (nunca sobrescreve em silêncio); para editar essa questão, usa-se PUT /admin/questoes/{id} (D40). Reimportar o mesmo conteúdo não duplica: o hash identifica o que já existe e conta em questoesIgnoradas. O hash é o SHA-256 de provaId, numero, textoApoio, grupoApoio, enunciado, imagemUrl e alternativas (ordenadas por letra), com os espaços das pontas aparados e as quebras de linha normalizadas; NÃO inclui subtopicoId, dificuldade, explicacao nem gabarito (D90). A reimportação de questão que já existe com o mesmo hash, mas com subtopicoId, dificuldade, explicacao ou gabarito diferentes, é ignorada e contada em questoesIgnoradas, e a resposta informa isso (não é silencioso); para mudar esses campos o caminho é o PUT (numa questão publicada, antes se cria a versão em rascunho por POST /versoes, D8; o gabarito também se aplica por PUT .../gabarito, D13). A regra do gabarito ignorado vale só para questão que já existe: a questão nova criada na reimportação recebe o gabarito normalmente (D90). Questões que existem e não vêm no payload ficam como estão. numero repetido no payload ou lista de questões vazia = 400. As alternativas são A a E, cada uma uma vez, em qualquer ordem no payload (D90). Limites: corpo de 2 MB (acima disso, 413) e 100 questões por importação (acima disso, 400) (D55). O hash é gerado na importação, guardado com a questão e nunca muda: nem por PUT, nem por nova versão, e todas as versões da questão carregam o mesmo hash (D51).

Fluxo esperado: importar a prova (sem gabarito) → aplicar o gabarito oficial de uma vez (PUT .../gabarito) → revisar → publicar as questões → publicar a prova. Para corrigir uma questão ou gabarito depois de publicado: POST /versoes, editar o rascunho e publicar; tentativas novas usam a última versão publicada e as antigas mantêm o snapshot (D18).

Não existem: POST de questão avulsa (as questões entram pela importação).

6. Tentativas (aluno)

Campos de Tentativa (saída)

Campo	Tipo	Regra
id	UUID	gerado
provaId	UUID	da prova
status	enum	EM_ANDAMENTO, FINALIZADA, EXPIRADA
iniciadaEm, prazoEm	data/hora	UTC, definidos pelo servidor (prazoEm = início + duração da prova)
tempoRestanteSegundos	inteiro	nulo quando a tentativa não está em andamento (D73); vale 0 entre prazoEm e prazoEm + 3 s (D16); null na EXPIRADA (D88)
finalizadaEm	data/hora	nulo até encerrar; na tentativa EXPIRADA é o menor entre o prazoEm e o momento da expiração (D88)
nota	inteiro	nulo até encerrar. Na ETEC, nota é a quantidade de acertos (0 até o total de questões), sem percentual e sem desconto por erro (D3)
questoes	lista	{ordem, questaoId, versao, textoApoio, grupoApoio, enunciado, imagemUrl, alternativas[{letra, texto}]} (5 alternativas) sem gabarito. A ordem é o numero da questão na prova (buracos são permitidos) e as alternativas são sempre A a E, sem embaralhar (D30, D108)
respostas	lista	{questaoId, alternativa, tempoGastoMs} já salvas

Resposta (entrada): alternativa é obrigatória (uma das letras A a E, ou null para deixar em branco), e omitida dá 400 (D97); tempoGastoMs (inteiro ≥ 0 e ≤ a duração da prova em milissegundos, opcional) é o total acumulado da questão e, se for omitido, mantém o valor já salvo (D30, D97).

Verbo	Rota	Sucesso	Erros
POST	/tentativas ({provaId}); passa pelo serviço de entitlements: o serviço de tentativas chama exigir(usuario, INICIAR_TENTATIVA, programa), com o programa da prova (ETEC até a prova ter programa); na fase 1 o stub libera STUDENT e ADMIN no ETEC, e a negação é 403 ENTITLEMENT_NEGADO (D35, D140, D141)	201 + Location, com a tentativa completa (mesmo corpo de GET /tentativas/{id}, D52); fixa as questões (última versão publicada de cada uma) e o prazoEm (snapshot)	400 (prova inexistente ou não publicada, a mesma resposta para as duas, D14), 401, 403, 409 (já há tentativa em andamento nessa prova, D4; detalhes.tentativaId informa o id da tentativa aberta, D31, D44)
GET	/tentativas/{id}	200	400, 401, 403, 404
PUT	/tentativas/{id}/respostas/{questaoId}	200 com a resposta salva {questaoId, alternativa, tempoGastoMs} (D96), idempotente (vale a última)	400 (alternativa fora de A a E, D53; alternativa omitida ou tempoGastoMs fora de 0 até a duração da prova, D97), 401, 403, 404 (tentativa inexistente, de outro aluno, ou questaoId que não pertence à tentativa, D53), 409 (tentativa encerrada ou prazo vencido além da tolerância, D5)
POST	/tentativas/{id}/finalizar	200 {id, status, finalizadaEm, nota, total, acertos, erros, emBranco}, sem itens (D52); repetir devolve o mesmo. Entre prazoEm e prazoEm + 3 s encerra como FINALIZADA; passada a tolerância, devolve 200 com status EXPIRADA (D31, D50)	400, 401, 403, 404
GET	/tentativas/{id}/resultado	200 {nota, total, acertos, erros, emBranco, itens[{ordem, questaoId, alternativaMarcada, gabarito, correta, explicacao}]} (na ETEC nota = acertos; gabarito e explicação vêm da versão do snapshot; a questão em branco tem correta = false e alternativaMarcada = null; erros exclui emBranco; acertos + erros + emBranco = total; total é o número de questões do snapshot, D89)	400, 401, 403, 404, 409 (ainda em andamento)
GET	/tentativas (histórico; filtros status, provaId; sort: iniciadaEm; padrão decrescente)	200 lista paginada	400, 401, 403

Depois de FINALIZADA ou EXPIRADA, o aluno pode refazer a mesma prova, sem limite na fase 1 (o D4 só barra duas tentativas simultâneas).

Encerramento automático: a tentativa só expira quando agora > prazoEm + 3 s (tolerância do D5); até lá continua EM_ANDAMENTO e aceita respostas. Passado esse ponto, ela vira EXPIRADA e é pontuada com o que foi salvo (D6); o finalizadaEm dela é o menor entre o prazoEm e o momento da expiração (na varredura, o prazoEm; na exclusão da conta antes do prazo, o momento da exclusão) e o tempoRestanteSegundos é null (D88). Se o aluno chamar finalizar antes desse ponto, ainda que depois do prazoEm, a tentativa encerra como FINALIZADA (D50). Isso acontece ao ler/salvar e por um job de varredura que roda a cada minuto (propriedade, D88); o cliente nunca decide (D16). Não existem: PUT, PATCH e DELETE de tentativa; resposta depois de prazoEm + 3 s (D85); gabarito antes de encerrar.

7. Operação
Verbo	Rota	Sucesso	Erros
GET	/actuator/health e /actuator/health/liveness, /readiness (fora de /api/v1; sem detalhes)	200 UP	503 (banco fora do ar, só em /actuator/health e /actuator/health/readiness; a liveness responde sempre 200, D99)

Qualquer outro caminho de /actuator e /error: 401 para anônimo e 403 para autenticado, inclusive ADMIN; nunca 404 (D113). O /actuator responde no formato do próprio framework, e não no ErrorResponse (D82). A documentação OpenAPI (/v3/api-docs e /swagger-ui) fica ligada em desenvolvimento e homologação e desligada em produção (D34). Erros sem vazamento (D127): nenhuma resposta de erro traz pilha, nome de classe, mensagem de exceção nem versão do servidor. Nota do servidor web: o container (Tomcat) responde ANTES da cadeia de segurança a /WEB-INF/* e /META-INF/* com 404 (para qualquer perfil, inclusive o ADMIN, no formato padrão do Spring Boot: timestamp, status, error e path; o path é o que o próprio cliente pediu, o Boot 4.2 não permite tirá-lo) e à barra invertida e à barra codificada (%2F) com 400 em texto HTML (D124); essas respostas não levam os cabeçalhos de segurança do Spring Security e não trazem nada além disso.

8. Decisões
#	Decisão	Estado
D1	Erros 400 de validação trazem a lista opcional campos	✅ Confirmada
D2	Exatamente 5 alternativas, A a E	✅ Resolvida (as questões da ETEC têm sempre A, B, C, D, E)
D3	nota = quantidade de acertos (inteiro), sem percentual	✅ Resolvida (a nota só mostra a quantidade de acertos; o caderno da prova também diz que só os acertos contam)
D4	Uma tentativa em andamento por aluno e prova; outra = 409	✅ Confirmada
D5	Tolerância de 3 s após o prazoEm para salvar resposta	✅ Confirmada
D6	Tentativa expirada é pontuada com o que foi salvo (EXPIRADA)	✅ Confirmada
D7	Importação tudo ou nada	✅ Confirmada
D8	Edição de questão publicada = POST /versoes; PUT só em rascunho	✅ Confirmada
D9	DELETE /me: exclusão com anonimização dos dados pessoais, mantendo as tentativas sem identificação	✅ Confirmada (revisar com a política de privacidade na implementação)
D10	Aluno só vê provas PUBLICADA; ADMIN vê todas	✅ Confirmada
D11	Texto de apoio: campo opcional textoApoio copiado em cada questão que o usa, mais grupoApoio opcional para o app agrupar	✅ Confirmada
D12	Formatação mínima nos textos (<b>, <u>, <i>, <sup>, <sub>, mais <br> pelo D32); outras marcações são recusadas (400)	✅ Confirmada (atualizada na v0.5)
D13	A prova pode ser importada sem gabarito (rascunho) e só publica com gabarito; gabarito aplicado em lote por PUT /admin/provas/{id}/gabarito; o hash de duplicidade não inclui o gabarito	✅ Confirmada
D14	Prova inexistente ou não publicada em POST /tentativas = 400, a mesma resposta para as duas (regra geral de referência no corpo)	✅ Confirmada
D15	Logout é anônimo, como o refresh; o refresh no corpo é a credencial. Sempre 204, até para refresh desconhecido, para ninguém ficar preso por access token vencido	✅ Confirmada
D16	A tentativa só expira quando agora > prazoEm + 3 s; antes disso fica EM_ANDAMENTO com tempoRestanteSegundos = 0	✅ Confirmada
D17	Ordem de validação: autenticação e autorização (401/403), depois corpo e id (400), existência (404), conflito (409) e alteração	✅ Confirmada
D18	Corrigir gabarito ou texto de prova publicada = POST /versoes + publish. Tentativas novas usam a última versão publicada; as antigas mantêm o snapshot	✅ Confirmada
D19	GET /admin/questoes/{id} devolve o rascunho aberto, se houver, senão a última publicada, e aceita ?versao=n. A lista traz só a versão mais recente de cada questão. Location do POST /versoes = /admin/questoes/{id}. Desempate (id, versao)	✅ Confirmada
D20	Prova única por banca + ano + semestre. Reimportar prova em RASCUNHO reutiliza o provaId; publicada = 409. Hash da questão inclui prova, número e conteúdo (sem gabarito). Limites: 2 MB e 100 questões (refinados pelo D55)	✅ Confirmada
D21	Acrescentar PUT e DELETE /admin/provas/{id} (só RASCUNHO) e DELETE /admin/questoes/{id} (só se nunca publicada; em versão rascunho, descarta o rascunho)	✅ Confirmada
D22	Questão anulada fica fora da fase 1. Atenção: como a prova só publica com gabarito completo, uma anulada bloquearia a publicação; conferir como a banca trata, para prever um campo anulada	⏳ Pendência externa
D23	GET /termos/vigente (pública) devolve {versao, url}. aceiteTermosVersao errada = 400. O register devolve 201 com o usuário, sem tokens	✅ Confirmada
D24	Na fase 1, menor é só uma marca calculada, sem mudar comportamento. Antes do beta, definir idade mínima e consentimento do responsável com apoio jurídico	⏳ Pendência externa
D25	Limites do 429: 10 requisições por minuto por IP em /auth/* e 5 tentativas de signin por minuto por e-mail, configuráveis por propriedade (números iniciais). Tokens: ver 3.1 e D39	↪ SUBSTITUÍDA pela D128 a D131 no que diz respeito aos limites (o IP e o e-mail deixam de ser chaves separadas; 10 por minuto e 5 por minuto deixam de valer); o trecho sobre tokens continua valendo
D26	DELETE /me: e-mail vira removido-{uuid}@invalid (o original pode ser recadastrado); refresh revogados na hora; tentativas em andamento viram EXPIRADA; último ADMIN = 409. Bootstrap por ADMIN_EMAIL e ADMIN_SENHA só se não existir admin. O filtro consulta o banco a cada requisição	✅ Confirmada
D27	Campos de saída (id, ativo...) são ignorados no corpo; campos de entrada que não mudam (materiaId, email, provaId...) dão 400 se o valor for diferente do atual; valor igual é aceito (refinada pelo D48)	✅ Confirmada
D28	Aluno vê só matérias e subtópicos ativos (e subtópicos só de matéria ativa); admin vê todos. status em /provas enviado por STUDENT = 400. Publicar questão com subtópico desativado = 409. Desativar matéria não desativa subtópicos em cascata. DELETE bloqueado por qualquer subtópico ou questão, ativo ou não	✅ Confirmada
D29	sort=campo,direção. page ou size inválidos = 400. Página além do fim = 200 vazio. Filtro nome é "contém", sem distinguir maiúsculas e acentos	✅ Confirmada
D30	tempoGastoMs é o total acumulado da questão; omitido, mantém o valor salvo. Ordem das questões = numeração da prova; alternativas sempre A a E, sem embaralhar	✅ Confirmada
D31	O aluno pode refazer a prova sem limite na fase 1. O 409 informa o id da tentativa aberta. finalizar depois da tolerância do prazo devolve 200 com EXPIRADA (refinada pelo D50)	✅ Confirmada
D32	Texto: <, > e & se escrevem &lt;, &gt;, &amp;. Atributos nas tags e tags mal fechadas ou mal aninhadas = 400. <br> entra na lista. Limite de 10.000 conta caracteres	✅ Confirmada
D33	ErrorResponse ganha o campo codigo estável (ex.: TENTATIVA_EM_ANDAMENTO)	✅ Confirmada
D34	Origens do CORS por configuração por ambiente. Swagger ligado em desenvolvimento e homologação, desligado em produção	✅ Confirmada
D35	POST /tentativas passa por podeIniciarTentativa(usuario, prova), que devolve sempre verdadeiro na fase 1	✅ Confirmada (refinada pela D140: podeIniciarTentativa vira só um nome dentro da Tentativa, que chama exigir(usuario, INICIAR_TENTATIVA, programa))
D36	imagemUrl só aceita hosts de uma lista permitida (o armazenamento do projeto)	✅ Confirmada
D37	409 no cadastro revela e-mail existente; o 429 é a mitigação. Não há envio de e-mail na fase 1	✅ Confirmada
D38	Removido o 403 dos GET de matéria, subtópico e prova (só podem dar 401)	✅ Confirmada
D39	Tokens: HS256; access de 15 min com claims sub (UUID), papel, tipo=access, jti e exp; refresh de 24 h (propriedade configurável), tipo=refresh, com jti guardado no banco; expiresIn em segundos (900). Cada /auth/refresh invalida o refresh usado; reuso de um refresh já invalidado revoga todos os refresh do usuário (D49); logout revoga só o refresh enviado	✅ Confirmada
D40	Reimportar prova em RASCUNHO atualiza titulo, duracaoMinutos e fonteUrl. Questão já existente no mesmo numero com conteúdo diferente = 409 listando os números em conflito (nunca sobrescreve em silêncio); para editar, usa-se PUT /admin/questoes/{id}	✅ Confirmada
D41	O filtro status de GET /admin/questoes aceita só RASCUNHO e PUBLICADA; a ARQUIVADA se lê por ?versao=n	✅ Confirmada
D42	numero não muda no PUT de questão (valor diferente do atual = 400, como os demais campos imutáveis; igual é aceito, D48); número errado: descartar o rascunho e reimportar	✅ Confirmada
D43	DELETE de prova em RASCUNHO que tenha questão já publicada = 409, sem apagar junto	✅ Confirmada
D44	ErrorResponse ganha o campo opcional genérico detalhes (objeto): no 409 de tentativa em andamento { "tentativaId": "..." } e no 409 do publish da prova (números das questões pendentes). Os valores de codigo são definidos na implementação	✅ Confirmada
D45	Bootstrap do admin: sem ADMIN_EMAIL e ADMIN_SENHA e sem nenhum admin, em desenvolvimento e teste só aviso no log; em produção a aplicação não sobe. Se já existe admin, as variáveis são ignoradas	✅ Confirmada
D46	Hosts permitidos de imagemUrl vêm da propriedade por ambiente app.imagens.hosts-permitidos; o armazenamento físico fica para o módulo de infraestrutura; em desenvolvimento, localhost	✅ Confirmada
D47	Aluno que envia ativo=false (ou status em /provas) recebe 400	✅ Confirmada
D48	Campo de entrada que não muda (materiaId, email, dataNascimento, provaId, numero): valor igual ao atual é aceito; valor diferente = 400 com codigo CAMPO_IMUTAVEL. Refina D27 e D42	✅ Confirmada
D49	Reuso de um refresh já invalidado revoga todos os refresh do usuário; não existe conceito de família por login. Refina D39	✅ Confirmada
D50	finalizar entre prazoEm e prazoEm + 3 s encerra como FINALIZADA; passada a tolerância, devolve 200 com EXPIRADA. Refina D31	✅ Confirmada
D51	O hash da questão é gerado na importação, guardado com a questão e nunca muda (nem por PUT, nem por nova versão)	✅ Confirmada
D52	POST /tentativas devolve 201 com a tentativa completa (mesmo corpo de GET /tentativas/{id}). finalizar devolve {id, status, finalizadaEm, nota, total, acertos, erros, emBranco}, sem itens	✅ Confirmada
D53	questaoId na rota que não pertence à tentativa = 404. Letra fora de A a E = 400	✅ Confirmada
D54	Criar subtópico em matéria desativada = 409 (codigo MATERIA_INATIVA)	✅ Confirmada
D55	Corpo de importação acima de 2 MB = 413; mais de 100 questões = 400. Refina D20	✅ Confirmada
D56	O filtro status de GET /admin/questoes vale para a versão mais recente exibida	✅ Confirmada
D57	Nome (de matéria e dos demais recursos): o servidor remove os espaços das pontas antes de validar; nome vazio depois disso = 400. O texto "sem espaço nas pontas" deixa de sugerir que isso é obrigação do cliente	✅ Confirmada
D58	Location é relativo (/api/v1/recurso/{id}) e o 201 devolve o recurso completo	✅ Confirmada
D59	O campo campos do 400 traz no máximo uma mensagem por campo. O texto da mensagem não é contratual; o front decide pelo codigo	✅ Confirmada
D60	Data inválida no corpo (por exemplo 2000-02-30 ou 2000-2-3) = 400 com campos apontando o campo e a mensagem "Data inválida. Use o formato aaaa-mm-dd."	✅ Confirmada (a implementação entra junto com o primeiro DTO que tem data de entrada)
D61	PUT é substituição: campo opcional omitido (descricao) vira nulo	✅ Confirmada
D62	disable/enable repetido devolve o mesmo resultado e não altera atualizadoEm	✅ Confirmada
D63	Datas gravadas e devolvidas com precisão de milissegundos; a aplicação trunca na origem para o banco não arredondar	✅ Confirmada
D64	sort sem direção vale asc; a direção aceita qualquer caixa	✅ Confirmada
D65	Nome em branco no filtro equivale a filtro ausente	✅ Confirmada
D66	ativo só aceita true ou false; outros valores = 400	✅ Confirmada
D67	O desempate por id é sempre crescente, inclusive quando o sort é desc	✅ Confirmada
D68	O campo tamanho do envelope é o tamanho aplicado (já reduzido), e não a quantidade de itens	✅ Confirmada
D69	A ordem alfabética segue a collation do MySQL (ignora acento e caixa)	✅ Confirmada
D70	Quando página × tamanho passa de 2³¹-1, a resposta é 200 vazia com os totais corretos, e o pagina do envelope é limitado à maior página possível	✅ Confirmada
D71	As rotas de /api/v1 respondem SOMENTE JSON. Accept de XML, YAML ou qualquer outro formato = 406, no formato de erro padrão, em JSON. A documentação OpenAPI (/v3/api-docs, /v3/api-docs.yaml, /swagger-ui) é exceção declarada: existe só em desenvolvimento e homologação e pode responder YAML ou HTML	✅ Confirmada
D72	A ordem dos campos de SAÍDA do JSON é fixa: a ordem em que os campos aparecem escritos no contrato, da esquerda para a direita, inclusive nos objetos entre chaves (questoes[], respostas[], itens[] e o resultado da tentativa); o ErrorResponse sai na ordem dataHora, status, erro, codigo, mensagem, campos, detalhes	✅ Confirmada
D73	Campos nulos dos DTOs de resposta saem sempre no JSON como null (por exemplo "descricao":null); no ErrorResponse, campos e detalhes são omitidos quando nulos	✅ Confirmada
D74	O Content-Type das respostas com corpo é exatamente application/json, sem o parâmetro charset, mas o corpo é sempre UTF-8 (padrão do JSON); o 204 não tem Content-Type. O JSON sai compacto; os exemplos do documento são formatados para leitura	✅ Confirmada
D75	Campo que o papel não pode ver não existe no JSON (não sai como null). Em particular, as questões da tentativa do aluno usam um DTO próprio, sem gabarito nem explicacao	✅ Confirmada
D76	page começa em 0 (zero é válido); size zero, negativo ou não numérico = 400	✅ Confirmada (refina D29)
D77	O sort de GET /admin/questoes só aceita numero, e o padrão é numero asc. O padrão de GET /subtopicos é nome asc. O criadoEm sai do sort das questões	✅ Confirmada
D78	Toda rota das tabelas é relativa a /api/v1, exceto a seção 7; todo Location inclui o prefixo	✅ Confirmada
D79	Ordem de validação final: 401/403, depois 400 de formato (corpo e id), depois 404, depois 409 de estado, depois 400 CAMPO_IMUTAVEL	✅ Confirmada (refina D17, D27, D42 e D48)
D80	A criação e a importação de questão exigem subtopicoId (existente e ativo), enunciado e as 5 alternativas; o gabarito é opcional. O 409 do publish de questão fica só com: subtópico desativado e sem gabarito	✅ Confirmada
D81	A regra do último ADMIN permanece; na fase 1 há um único admin, então ele nunca se exclui	✅ Confirmada
D82	O ErrorResponse vale em toda a aplicação, exceto /actuator (formato do próprio framework) e a documentação OpenAPI. A seção 2 cita /actuator/health, /actuator/health/liveness e /actuator/health/readiness	✅ Confirmada
D83	O contrato é rascunho até a aprovação; a v1.0 é a versão aprovada e a seção 10 registra as mudanças a partir dela	✅ Confirmada
D84	Apêndice A, "Catálogo de códigos de erro", com os códigos já existentes ou nomeados; cada recurso novo acrescenta os seus códigos ao implementar	✅ Confirmada (refina D44)
D85	"Não existe resposta fora do prazo" passa a "não existe resposta depois de prazoEm + 3 s"	✅ Confirmada
D86	Corpo da requisição: campo desconhecido é ignorado; tipo errado ou JSON ilegível = 400; campo obrigatório ausente ou null = 400 com campos	✅ Confirmada
D87	A autorização usa o papel do banco; o claim papel do token é só informativo	✅ Confirmada (refina D39)
D88	Tentativa EXPIRADA: finalizadaEm = o menor entre prazoEm e o momento da expiração (na varredura é o prazoEm; na exclusão da conta antes do prazo é o momento da exclusão); o job de varredura roda a cada minuto (propriedade); tempoRestanteSegundos = null	✅ Confirmada
D89	Resultado: questão em branco tem correta = false e alternativaMarcada = null; erros exclui emBranco; acertos + erros + emBranco = total; total é o número de questões do snapshot	✅ Confirmada
D90	Hash de questão: SHA-256 de provaId, numero, textoApoio, grupoApoio, enunciado, imagemUrl e alternativas (ordenadas por letra), com espaços das pontas aparados e quebras de linha normalizadas; NÃO inclui subtopicoId, dificuldade, explicacao nem gabarito. A reimportação de questão que já existe com o mesmo hash, mas com subtopicoId, dificuldade, explicacao ou gabarito diferentes, é ignorada e contada em questoesIgnoradas, e a resposta informa isso (não é silencioso); para mudar esses campos o caminho é o PUT. A regra do gabarito ignorado vale só para questão que já existe: a questão nova criada na reimportação recebe o gabarito normalmente. Questões que existem e não vêm no payload ficam como estão. numero repetido no payload ou lista vazia = 400. As alternativas são A a E, cada uma uma vez, em qualquer ordem no payload	✅ Confirmada (refina D20, D40 e D51)
D91	Bootstrap do admin: nome "Administrador", dataNascimento 2000-01-01, aceiteTermosVersao igual à versão vigente. ADMIN_EMAIL e ADMIN_SENHA são validados como num cadastro comum (senha de 8 a 72 bytes); inválidos em produção, a aplicação não sobe	✅ Confirmada (refina D45)
D92	Anonimização do DELETE /me: nome = "Usuário removido"; e-mail = removido-{uuid}@invalid; senha inutilizada; dataNascimento = null, o que exige uma migration futura que torne a coluna anulável (a migration já aplicada não se edita); essa migration é pré-requisito da tarefa do DELETE /me e vem antes dela, na ordem da fila de migrations	✅ Confirmada (refina D26)
D93	O desempate é pelo id numérico interno, crescente (nas questões, id interno e depois versao)	✅ Confirmada (refina D19 e D67)
D94	GET /termos/vigente lê versao e url de propriedades por ambiente	✅ Confirmada
D95	429: o IP vem do X-Forwarded-For só se o proxy for confiável por propriedade; contador em memória, janela fixa de 1 minuto; a chave do signin é o e-mail normalizado, mesmo inexistente; o 429 vem antes do 400 e do 401; Retry-After em segundos	↪ SUBSTITUÍDA pela D128 a D131 (contador no banco e não em memória, janela de 15 minutos no signin, chaves e-mail+origem e origem; o 429 continua antes do 400 só para o limite por origem)
D96	Corpos de sucesso: PATCH /me, disable/enable, publish (prova e questão), PUT /materias/{id}, PUT /subtopicos/{id}, PUT /admin/provas/{id} e PUT /admin/questoes/{id} devolvem 200 com o recurso completo (mesmo DTO do GET); POST /versoes devolve 201 com a nova versão; PUT .../respostas/{questaoId} devolve a resposta salva {questaoId, alternativa, tempoGastoMs}; refresh devolve o mesmo corpo do signin. Entradas: signin {email, senha}; refresh e logout {refreshToken}	✅ Confirmada
D97	PUT de resposta: alternativa é obrigatória (A a E ou null, que é em branco); omitida = 400; tempoGastoMs ≥ 0 e ≤ duração da prova em milissegundos; omitido, mantém o valor salvo	✅ Confirmada (refina D30)
D98	Formatação dos textos: só as marcas <b>, <u>, <i>, <sup>, <sub> e <br> exatas, em minúsculas e <br> sem barra; só as entidades &lt;, &gt; e &amp;; os limites contam pontos de código; o HTML só vale nos textos de questão (enunciado, textoApoio, alternativas[].texto, explicacao)	✅ Confirmada (refina D12 e D32)
D99	/actuator/health/liveness responde sempre 200; só /actuator/health e /readiness dão 503 com o banco fora	✅ Confirmada
D100	CORS: métodos GET, POST, PUT, PATCH, DELETE e OPTIONS; cabeçalhos Authorization e Content-Type; sem credenciais; max-age 3600; o preflight OPTIONS não exige autenticação	✅ Confirmada (refina D34)
D101	Em página comum além do fim, pagina devolve a página pedida; mais de um sort = 400; o nome do campo do sort diferencia maiúsculas (NOME = 400); ativo aceita true ou false em qualquer caixa	✅ Confirmada (refina D64, D66 e D70)
D102	O padrão de /provas (ano decrescente) com sort=ano sem direção valendo asc é intencional	✅ Confirmada
D103	Filtro com enum inválido, ano não numérico ou materiaId malformado = 400; STUDENT filtrando por materiaId de matéria inativa recebe lista vazia; o STUDENT que busca por id uma matéria inativa recebe 404 MATERIA_NAO_ENCONTRADA, igual ao da matéria inexistente	✅ Confirmada
D104	Todos os textos curtos (nome, descricao, titulo, banca...) têm os espaços das pontas aparados; a unicidade é pela collation (espaços internos contam; sem normalização Unicode própria); o subtópico ignora caixa e acento como a matéria	✅ Confirmada (refina D57)
D105	O e-mail é aparado e convertido para minúsculas antes de validar e antes do 409; o CAMPO_IMUTAVEL do e-mail compara o valor normalizado	✅ Confirmada
D106	dataNascimento: "no passado" em UTC, mínimo 1900-01-01; menor é calculado na leitura	✅ Confirmada
D107	tokenType é "Bearer"; o logout responde 204 para qualquer texto presente (inclusive malformado ou de outro tipo) e 400 só se o refresh estiver ausente ou não for texto; um access token enviado ao refresh = 401	✅ Confirmada (refina D15 e D39)
D108	A ordem da questão na tentativa é o numero dela na prova (buracos permitidos); quantidadeQuestoes é o número de questões da prova (uma por numero, qualquer status)	✅ Confirmada (refina D30)
D109	Subtópico: a criação, e o enable que de fato ativaria um subtópico inativo, respondem 409 MATERIA_INATIVA se a matéria está desativada; enable em subtópico que já está ativo responde 200 sem efeito, mesmo de matéria desativada (D62); renomear (PUT) e desativar continuam permitidos	✅ Confirmada (refina D54)
D110	Publish de questão já publicada e sem rascunho aberto = 200 sem efeito; o gabarito em lote é tudo ou nada (um item de questão publicada derruba o lote com 409); DELETE de prova em RASCUNHO apaga também as suas questões em rascunho (409 se alguma já foi publicada, D43)	✅ Confirmada
D111	PUT é substituição total (D61), inclusive de gabarito, explicacao e fonteUrl; o contrato (seção 5) e o texto do Swagger dizem que um PUT de questão que omite o gabarito o apaga	✅ Confirmada (refina D61)
D112	415: só application/json, com charset=utf-8 tolerado	✅ Confirmada
D113	Outros caminhos de /actuator e /error: 401 para anônimo e 403 para autenticado, inclusive ADMIN; nunca 404	✅ Confirmada
D114	Cache-Control: no-store vale para as respostas de signin e de refresh	✅ Confirmada
D115	O contrato é autônomo: sem referências a outros documentos nem a numeração de pendências; a seção 11 descreve por extenso as duas pendências externas (D22 e D24)	✅ Confirmada
D116	Em /api/v1, rota inexistente responde 401 a quem não está autenticado e 404 a quem está (rota nova nasce negada); o 404 de rota inexistente só vale após a autenticação	✅ Confirmada (refina D82 e D113); refinada pela D121 (a quem está autenticado, rota sem regra dá 403)
D117	A `descricao` que sobra só de espaços, depois de aparada, vira null: não existe "" como segundo jeito de dizer "sem descrição"; vale para Matéria e Subtópico. O `nome` em branco continua 400	✅ Confirmada (refina D104)
D118	Os tokens também levam os claims iss (emissor, vindo de propriedade) e iat; o claim papel continua só informativo (D87). Refina D39	✅ Confirmada (refina D39)
D119	O e-mail do cadastro só aceita caracteres ASCII, em formato válido, com parte local de até 64 caracteres e total de até 255, sem aspas, sem endereço IP entre colchetes e com domínio que tenha ponto; qualquer outro caractere = 400 VALIDACAO_FALHOU com campos[email]. A collation do banco ignora acento e caixa, então um e-mail com acento colidiria com o equivalente sem acento	✅ Confirmada (refina D105)
D120	O 401 e o 403 de /actuator/** (fora dos três caminhos de saúde) e de /error saem em ErrorResponse, porque nascem na cadeia de segurança; a D82 vale só para o corpo do próprio actuator (health e 503). Refina D82 e D113	✅ Confirmada (refina D82 e D113)
D121	Rota ou método sem regra de acesso é NEGADO: 401 ao anônimo e 403 a quem está autenticado, inclusive ADMIN, em /api/v1 e fora dele, mesmo que a rota nem exista; vale também para método sem regra numa rota que existe (por exemplo, DELETE na coleção de matérias: 403, e não 405). O 404 fica para o recurso inexistente depois da autorização, e para a rota que a segurança libera mas que não tem recurso mapeado. Refina D116	✅ Confirmada (refina D116)
D122	GUARDIAN não tem nenhuma permissão na fase 1: 403 em tudo que exige papel, inclusive GET /me, porque a tabela da seção 2 só dá acesso a STUDENT e ADMIN; as rotas públicas continuam funcionando	✅ Confirmada
D123	CORS: as origens vêm de app.cors.allowed-origins (variável CORS_ALLOWED_ORIGINS, separadas por vírgula), vazia por padrão, e vazia significa sem CORS. A aplicação não sobe com *, com origem fora do formato http(s)://domínio[:porta] (sem barra, caminho, consulta ou usuário) nem, em produção (perfil prod), com origem http. Métodos GET, POST, PUT, PATCH, DELETE e OPTIONS; cabeçalhos Authorization e Content-Type; sem credenciais; Max-Age 3600; Location exposto. Só o preflight de verdade (OPTIONS com Origin e Access-Control-Request-Method) dispensa autenticação; um OPTIONS comum é negado. Origem, método ou cabeçalho não permitido = 403 ACESSO_NEGADO em ErrorResponse, e os 401 e 403 também levam os cabeçalhos de CORS. O HEAD cross-origin é permitido pelo navegador sem preflight, por isso não consta da lista de métodos. Refina D34 e D100	✅ Confirmada (refina D34 e D100)
D124	O firewall do Spring Security (barra dupla, ;jsessionid, %2e e semelhantes) responde 400 REQUISICAO_INVALIDA em ErrorResponse, com mensagem fixa e sem detalhe técnico. Fora do alcance: a barra invertida e a barra codificada (%2F) são recusadas antes pelo próprio servidor web, com a resposta dele (400, texto HTML)	✅ Confirmada
D125	Senha do admin inicial (ADMIN_SENHA): 12 a 72 bytes em UTF-8, sem espaço, tabulação ou quebra de linha no começo ou no fim, sem \r nem \n em posição alguma, e que não seja marcador de exemplo (comparação sem diferenciar maiúsculas: começa com "troque-", como "troque-a-senha-do-admin", ou contém "<" e ">", como "<defina-aqui>"; o motivo da recusa é um texto fixo, sem o valor; em produção a aplicação não sobe e nos demais perfis há ERROR no log e nenhum admin). O e-mail (ADMIN_EMAIL) segue as regras do cadastro (ASCII, minúsculas, até 255). Valor em branco conta como ausente; as duas vêm juntas ou nenhuma. Refina D91 (que dizia 8 a 72)	✅ Confirmada (refina D91)
D126	Regras do bootstrap do admin: só cria se não existir ADMIN ativo (excluido_em nulo); com admin ativo as variáveis são ignoradas, inclusive para trocar a senha; nunca promove um usuário existente (e-mail já usado é erro); roda antes de o servidor aceitar conexões; só o perfil de produção derruba a subida (nos demais, aviso ou erro no log e nenhum admin é criado); nenhuma senha, hash ou e-mail no log ou em mensagem de erro. Limitação aceita: duas subidas simultâneas com ADMIN_EMAIL diferentes podem criar dois admins (revisitar no Módulo 17). Refina D45 e D91	✅ Confirmada (refina D45 e D91)
D127	Erros sem vazamento e cabeçalhos: nenhum erro traz pilha, classe, mensagem de exceção nem versão do servidor (server.error.include-message, -exception, -stacktrace e -binding-errors = never); respostas da cadeia de segurança levam nosniff e X-Frame-Options DENY, sem X-Powered-By e sem versão em Server; caminhos sensíveis (actuator fora da saúde, /h2-console, Swagger fora de desenvolvimento, arquivos de configuração) nunca abrem, nem para o ADMIN. Nota do container: /WEB-INF e /META-INF (404) e barra invertida e %2F (400) são respondidos pelo Tomcat antes da cadeia, sem os cabeçalhos de segurança (seção 7)	✅ Confirmada
D128	Limite de tentativas (429), SUBSTITUI D25 e D95: três contadores configuráveis (app.rate-limit.*), todos de janela fixa que começa na primeira ocorrência e expira sozinha, sem nunca bloquear de forma permanente: (1) toda requisição POST de /auth/register, /auth/signin, /auth/refresh e /auth/logout, por origem, 120 por minuto (só proteção contra enxurrada); (2) falhas de signin por e-mail normalizado e origem, 5 por 15 minutos; (3) falhas de signin por origem (qualquer e-mail), 50 por 15 minutos. O contador só por e-mail fica fora (um atacante trancaria a conta de outra pessoa). O contador sobe ANTES de conferir a senha, para valer sob concorrência (no máximo o limite chega ao BCrypt); o sucesso do signin apaga o de e-mail e origem e devolve uma ocorrência ao de origem (que conta só falhas). Os contadores ficam numa tabela do banco, compartilhada entre instâncias, cada um numa transação própria e curta (o 401 não desfaz o incremento e o lock não dura o BCrypt). A janela tem no máximo 1 hora, e as linhas vencidas são apagadas de hora em hora. Os números são propriedades validadas na subida (inteiro positivo; janela de 1 segundo a 1 hora)	✅ Confirmada (substitui D25 e D95)
D129	Resposta do 429: status 429, codigo LIMITE_EXCEDIDO, mensagem fixa e igual para qualquer contador, Retry-After em segundos (arredondado para cima, no mínimo 1), Cache-Control: no-store e ErrorResponse padrão, com os cabeçalhos de CORS dos demais erros. Ordem: o limite por origem vem antes de tudo (antes do corpo, do 400 e do 401); o de e-mail e origem vem depois de o corpo ser lido, então corpo sem e-mail (ausente ou nulo) é 400 e não conta; e-mail de formato inválido segue sendo 401 (D86) e conta como qualquer outro texto. O 429 é o mesmo para e-mail existente e inexistente (a chave é o e-mail normalizado, exista ou não) e não gasta BCrypt. O preflight de CORS (OPTIONS) e as demais rotas e métodos não contam	✅ Confirmada (refina D95)
D130	Origem da requisição: é o endereço do socket (IPv6 agrupado pelo prefixo /64; IPv4 mapeado em IPv6 vale como o IPv4); o X-Forwarded-For e cabeçalhos parecidos NUNCA são lidos pela aplicação. Atrás de um balanceador, o IP real só vale pela válvula nativa do Tomcat, ligada por propriedade (server.tomcat.remoteip.remote-ip-header e internal-proxies, ou as variáveis de ambiente equivalentes), com lista EXPLÍCITA de proxies: o cabeçalho é lido da direita para a esquerda, e só o primeiro endereço que não é proxy vale (o valor forjado à esquerda é ignorado). Por padrão fica desligado. Em produção, com o proxy ligado, lista vazia ou permissiva derruba a subida; sem proxy configurado há só um aviso no log (atrás de um balanceador todos dividiriam o mesmo limite; item do checklist do Módulo 17)	✅ Confirmada (refina D95)
D131	IP e e-mail no limite (LGPD): a tabela guarda só o HMAC-SHA256 da chave (escopo mais valor), com a chave do HMAC derivada do JWT_SECRET por um rótulo de contexto fixo (sem segredo novo); nunca o e-mail nem o IP. Trocar o JWT_SECRET apenas zera os contadores. Retenção: a janela tem no máximo 1 hora e a limpeza roda de hora em hora, então nenhuma linha passa de 1 dia. O IP nunca vai para o log; o log registra só o escopo do limite excedido, em DEBUG	✅ Confirmada
D132	Falha FECHADA do contador: se o contador não consegue gravar por disputa de lock (deadlock ou espera de lock que se repete depois de 5 repetições da transação), a requisição NÃO passa sem contar e NÃO vira 500: responde 429 LIMITE_EXCEDIDO com Retry-After fixo de 5 segundos, sem expor o motivo (a mesma resposta do limite excedido). Outros erros do banco (por exemplo, banco fora do ar) seguem como 500 genérico, nunca como "deixa passar". Ao zerar ou devolver uma ocorrência depois de um login correto, a falha de lock é ignorada (a ocorrência já foi contada: só deixa o contador mais restritivo)	✅ Confirmada (refina D128)
D133	Refresh guardado e com rotação (refina D39): a tabela refresh_token guarda SÓ o jti (nunca o token), o usuário, a validade e, se revogado, quando e por quê (ROTACAO, LOGOUT, REUSO, TROCA_SENHA, EXCLUSAO). O signin grava uma linha por refresh emitido. A rotação revoga o refresh por UPDATE condicional (jti, não revogado, não vencido) e, NA MESMA transação, insere o novo: se a inserção falhar, o rollback devolve a validade ao antigo. Dois refresh simultâneos com o mesmo token: exatamente um ganha. Todo caso de falha é o mesmo 401 TOKEN_INVALIDO	✅ Confirmada (refina D39)
D134	Reuso de refresh (refina D49): reapresentar um refresh JÁ ROTACIONADO (revogado por ROTACAO) revoga TODOS os refresh do usuário, incrementa a versão do token (D136) e responde 401; a revogação grava mesmo com o 401 (transação própria concluída antes de a resposta sair). Refresh vencido, desconhecido ou revogado por outro motivo (logout, troca de senha) dá só 401, sem revogação em massa. Risco conhecido (seção 11): o perdedor de dois refresh simultâneos é indistinguível de um reuso e dispara a revogação em massa	✅ Confirmada (refina D49)
D135	Logout (refina D15 e D107): POST /auth/logout, anônimo e idempotente, 204 para qualquer texto presente; revoga SÓ o jti daquele refresh, quando a assinatura é válida. Não derruba o access token vivo (no máximo 15 minutos). "Sair de todos" existe só como método de serviço (encerrarTodasAsSessoes: revoga todos os refresh e incrementa a versão, atomicamente), usado pelo reuso e pela troca de senha, sem rota	✅ Confirmada (refina D15 e D107)
D136	Versão do token (refina D118): a coluna usuario.versao_token (INT, começa em 0) vai nos tokens como o claim versao. O filtro JWT, que já lê o usuário a cada requisição (D87), recusa o access cujo claim seja diferente da versão do usuário ou falte; o refresh faz a mesma checagem. Incrementar a versão derruba, de uma vez, todos os tokens já emitidos, sem relógio e sem iat. Tokens emitidos antes desta versão (sem o claim) deixam de valer	✅ Confirmada (refina D118)
D137	Limpeza dos refresh: de hora em hora, em lotes, só as linhas vencidas há mais de 1 dia. A linha revogada e ainda não vencida fica, porque é ela que detecta o reuso	✅ Confirmada
D138	Política de senha compartilhada: uma classe só (PoliticaDeSenha) vale para o cadastro (aluno, 8 a 72 bytes, D91), para o bootstrap do administrador (12 a 72 bytes, sem espaço nas pontas, sem quebra de linha e sem marcador de exemplo, D125) e para a troca de senha	✅ Confirmada (refina D91 e D125)
D139	Troca de senha: POST /api/v1/me/senha, corpo {senhaAtual, novaSenha}, 204 sem corpo e sem tokens, com Cache-Control: no-store; STUDENT e ADMIN (GUARDIAN = 403, D122; anônimo = 401). Ordem: autenticação e papel, 429 (bucket próprio POR USUÁRIO, 5 erros da senha atual em 15 minutos, mesmo mecanismo da D128: conta antes, zera no sucesso, fora da transação principal), 400 de formato (campo ausente ou nova senha fora da política do papel, com campos), senha atual (400 SENHA_ATUAL_INCORRETA, com UMA comparação de BCrypt, o mesmo custo de um signin falho), nova igual à atual (400 VALIDACAO_FALHOU com campos[novaSenha]) e, por fim, a alteração numa transação: novo hash e encerrar todas as sessões com o motivo TROCA_SENHA. O corpo não leva @Valid: o 429 vem antes do 400 de formato (JSON ilegível ainda é 400 antes do 429)	✅ Confirmada
D140	Serviço de entitlements, ponto único de consulta (refina D35): o domínio assinatura tem o EntitlementsService (decidir e exigir), que consulta uma PoliticaDeEntitlements, a peça SUBSTITUÍVEL. A consulta recebe o usuário (UsuarioAutenticado, que o filtro monta do banco, D87), o recurso (enum Recurso; hoje só INICIAR_TENTATIVA) e o programa (Programa, um código como ETEC; a tabela exam_program ainda não existe). A fase 1 usa o stub PoliticaStubLiberaTudo, uma política EXPLÍCITA e não a ausência de checagem: libera INICIAR_TENTATIVA no programa ETEC para STUDENT e ADMIN, nega o GUARDIAN (D122), nega programa desconhecido e nega todo recurso que não está na sua lista (negar por padrão); todo Recurso novo exige decisão explícita (liberado ou negado de propósito). Argumento nulo é erro de programação (IllegalArgumentException, 500 genérico) e nunca vira "permitido". Quem chama conhece só o serviço: trocar o stub por regras reais é trocar a implementação. A Tentativa chamará exigir(usuario, INICIAR_TENTATIVA, programa), com o programa da prova quando houver (ETEC até lá). Fora desta decisão: explicação da questão, cota de simulados curtos e histórico; GET /entitlements não existe na fase 1	✅ Confirmada (refina D35)
D141	Negação do entitlement: 403 com codigo ENTITLEMENT_NEGADO, mensagem fixa "Este recurso não está disponível no seu plano." e detalhes opcional { recurso, programa } (D44), igual para qualquer motivo (o motivo só vai para o log e o teste). Nada de 402	✅ Confirmada
D142	Escolha da política: a propriedade app.entitlements.politica (hoje só stub), SEM valor padrão no código; ausente, em branco ou desconhecida derruba a subida; existe exatamente um bean de política. Em produção com o stub ativo, só um aviso (WARN) por subida, sem derrubar: o stub tem de ser trocado antes de cobrar (checklist do memoria.md)	✅ Confirmada
9. Fora da fase 1 (de propósito)

Pagamento e planos, entitlements como API (não existe GET /entitlements na fase 1; é um serviço interno com política stub explícita, D140), simulado curto, relatórios por subtópico, notas de corte, responsável e relatório semanal, plano de estudo, caderno de erros, IA, recuperação de senha, login com Google, envio de e-mail, questão anulada (D22), comportamento diferente para menores de idade (D24), outros exames (ENEM, OAB).

10. Registro de mudanças após a aprovação

A v1.0 é a versão aprovada (D83). Toda mudança a partir dela entra aqui, com a decisão e o motivo.

2026-11-22 — D117 (esclarecimento da D104): descricao só de espaços, depois de aparada, vira null (Matéria e Subtópico). Apêndice A: acrescentados SUBTOPICO_NOME_DUPLICADO, SUBTOPICO_NAO_ENCONTRADO e MATERIA_COM_SUBTOPICOS; CAMPO_IMUTAVEL e MATERIA_INATIVA passam de "a implementar" para "Implementado"; registrado que o 400 de materiaId inexistente no corpo usa VALIDACAO_FALHOU (D14), sem código novo. Decisões: D117 e D84 (cada recurso novo acrescenta os seus códigos ao implementar). Motivo: implementação do Subtópico (Tarefa 09).

2026-11-22 — Módulo 10 (autenticação). D118: os tokens também levam iss e iat (refina D39; texto da seção 3.1 atualizado, com a nota de que só o estado de excluído derruba o token e de que o segredo é texto UTF-8 de no mínimo 32 bytes). D119: e-mail do cadastro só em ASCII (refina D105). D120: 401 e 403 de /actuator/** e /error em ErrorResponse (refina D82 e D113). Apêndice A: acrescentados NAO_AUTENTICADO, CREDENCIAIS_INVALIDAS, TOKEN_INVALIDO, ACESSO_NEGADO e EMAIL_DUPLICADO, e registrado que os 400 de data inválida (D60), e-mail fora do ASCII, senha fora de 8 a 72 bytes e termos diferentes dos vigentes usam VALIDACAO_FALHOU com campos. Nenhuma migration nova (a V1 já tem excluido_em). Motivo: implementação do Módulo 10.

2026-11-22 — Módulo 11 (autorização e CORS). D121 (refina D116): rota ou método sem regra é negado, 401 ao anônimo e 403 a quem está autenticado; o 404 só para recurso inexistente depois da autorização (a seção 1 e a D116 atualizadas, e o 405 deixa de ocorrer em rota com regra). D122: GUARDIAN sem nenhuma permissão. D123 (refina D34 e D100): CORS por app.cors.allowed-origins, validado na subida, só https em produção, só preflight de verdade sem autenticação, 403 ACESSO_NEGADO em ErrorResponse para origem não permitida, HEAD cross-origin permitido pelo navegador. D124: o firewall responde 400 REQUISICAO_INVALIDA em ErrorResponse. Seção 2: anotação da tabela quem pode o quê. Seções 3 e 4: a coluna de erros dos GET e do /me passa a incluir 403 (GUARDIAN). Apêndice A: acrescentado REQUISICAO_INVALIDA e atualizadas as descrições de NAO_AUTENTICADO, ACESSO_NEGADO, RECURSO_NAO_ENCONTRADO e METODO_NAO_PERMITIDO. Motivo: implementação do Módulo 11.

2026-11-22 — Módulo 12 (admin inicial e endurecimento). D125 (refina D91): senha do admin de 12 a 72 bytes, sem espaço nas pontas, sem \r nem \n e que não seja marcador de exemplo ("troque-..." ou "<...>"). D126 (refina D45 e D91): regras do bootstrap (só cria sem ADMIN ativo; ignora as variáveis quando já existe; nunca promove; só produção derruba a subida; nada sensível no log; limitação dos dois e-mails simultâneos). D127: erros sem vazamento, cabeçalhos de segurança, caminhos sensíveis fechados e a nota do container. Seção 1 (linha Cabeçalhos), seção 3.2 e seção 7 atualizadas; o Apêndice A não muda (nenhum código de erro novo). Motivo: implementação do Módulo 12.

2026-11-22 — Módulo 13A (limite de tentativas). D128 (substitui D25 e D95): três contadores no banco (/auth/* por origem; falhas de signin por e-mail e origem; falhas de signin por origem), janela fixa, sem bloqueio permanente, conta antes de conferir a senha. D129: resposta 429 LIMITE_EXCEDIDO (Retry-After, no-store, ErrorResponse, CORS) e a ordem em relação ao 400. D130: origem da requisição pelo socket, proxy confiável só por propriedade e lista explícita (válvula do Tomcat). D131: HMAC na tabela, retenção, IP fora do log. D132: falha fechada do contador (429 com Retry-After fixo quando o lock se esgota). Seção 1 (limite de abuso e ordem de validação), seção 11 (limitações) e apêndice A (LIMITE_EXCEDIDO) atualizados. Migration V4 (limite_tentativa). Motivo: implementação do Módulo 13A.

2026-11-23 — Módulo 13B (ciclo de vida do token). D133 (refresh guardado e rotação, refina D39), D134 (reuso revoga tudo, refina D49), D135 (logout e "sair de todos" só como serviço), D136 (versão do token: coluna usuario.versao_token e claim versao, refina D118), D137 (limpeza), D138 (política de senha compartilhada), D139 (troca de senha). Seção 1 (Cache-Control), seção 2 (POST /me/senha), seção 3 (refresh, troca de senha e a nota da 3.1), seção 11 (riscos) e apêndice A (SENHA_ATUAL_INCORRETA; TOKEN_INVALIDO inclui o revogado e a versão antiga) atualizados. Migrations V5 (refresh_token) e V6 (usuario.versao_token). Motivo: implementação do Módulo 13B.

2026-11-24 — Tarefa 12 (serviço de entitlements, stub). D140 (ponto único de consulta, política explícita e substituível, refina D35), D141 (403 ENTITLEMENT_NEGADO) e D142 (escolha da política por app.entitlements.politica, aviso em produção). Seção 2 (anotação da rota premium), seção 6 (POST /tentativas), seção 9 (sem GET /entitlements), seção 11 (o stub) e apêndice A (ENTITLEMENT_NEGADO) atualizados. Nenhum endpoint e nenhuma migration novos. Motivo: implementação da Tarefa 12.

11. Pendências abertas e limitações conhecidas

Menores de idade e LGPD (externa, D24): na fase 1, menor é só uma marca calculada, sem mudar comportamento. Antes do beta, é preciso definir a idade mínima e o consentimento do responsável, com apoio jurídico.
Questão anulada (externa, D22): fica fora da fase 1. Como a prova só publica com o gabarito completo, uma questão anulada bloquearia a publicação; é preciso conferir como a banca trata a anulada, para prever um campo anulada.

Não há outra pendência de decisão aberta. O catálogo dos valores de codigo está no apêndice A (D84).

Limites de abuso (D128 a D131), limitações conhecidas: (a) redes com NAT, como as escolas, dividem a origem: 120 requisições por minuto e 50 falhas de signin por 15 minutos valem para todos que saem pelo mesmo endereço; (b) atrás de um balanceador sem proxy confiável configurado, todos os usuários dividem a origem do balanceador (item do checklist do Módulo 17); (c) o contador por e-mail e origem não cobre um ataque distribuído por muitos IPs contra uma única conta (o contador só por e-mail ficou de fora de propósito, para ninguém trancar a conta alheia); (d) /auth/logout já conta no limite por origem, mas a rota só passa a existir no Módulo 13B. O contador em memória por instância (D95) deixou de existir: o contador é compartilhado pelo banco.

Ciclo de vida do token (D133 a D139), riscos conhecidos: (a) PWA em rede ruim: se a resposta de um refresh se perde e o cliente repete o mesmo token, ou se duas abas renovam ao mesmo tempo, o perdedor é indistinguível de um reuso (D49) e a revogação em massa desloga o usuário de todos os aparelhos; a rever na Sprint do front (por exemplo, renovação única entre abas); (b) depois do logout o access token vivo continua válido até vencer (no máximo 15 minutos); a troca de senha, o reuso e o encerramento de todas as sessões o derrubam pela versão; (c) os tokens emitidos antes do 13B não têm o claim versao e deixam de valer na subida (todos entram de novo); (d) o encerramento de todas as sessões ainda não tem rota própria (só serviço); o DELETE /me, quando existir, deve chamá-lo com o motivo EXCLUSAO.

Entitlements (D140 a D142), limitações conhecidas: (a) o stub libera tudo o que conhece (INICIAR_TENTATIVA no ETEC) para STUDENT e ADMIN e PRECISA ser trocado por regras reais antes de cobrar; (b) só existe o recurso INICIAR_TENTATIVA e o programa ETEC: explicação da questão, cota de simulados curtos e histórico entram com o primeiro endpoint que os usa (cota e histórico pedem estado, e a Decisao deixa a porta aberta); (c) o programa é só um código até existir exam_program, na Sprint 2.

Apêndice A. Catálogo de códigos de erro (D84)

Cada recurso novo acrescenta os seus códigos a este catálogo ao ser implementado. O código de 413 entra quando a rota que o produz for implementada.

Código	Status	Quando	Situação
VALIDACAO_FALHOU	400	campos inválidos no corpo (traz campos); também é o código do 400 de materiaId inexistente no corpo do POST de subtópico, com campos[materiaId] (D14), do 400 de data fora do formato ou inexistente (D60, com a mensagem "Data inválida. Use o formato aaaa-mm-dd."), de e-mail fora do ASCII (D119), de senha fora de 8 a 72 bytes e de termos diferentes dos vigentes, sem código novo	Implementado
CORPO_ILEGIVEL	400	JSON ilegível ou de tipo errado	Implementado
PARAMETRO_AUSENTE	400	parâmetro obrigatório ausente	Implementado
PARAMETRO_INVALIDO	400	parâmetro com valor inválido (page, size, ativo, id malformado...)	Implementado
ORDENACAO_INVALIDA	400	sort com formato ou campo inválido	Implementado
CAMPO_IMUTAVEL	400	campo de entrada que não muda, enviado com valor diferente do atual	Implementado (materiaId no PUT de subtópico)
NAO_AUTENTICADO	401	rota protegida sem token válido (ausente, malformado, vencido, adulterado, de outro tipo ou de usuário excluído); rota ou método sem regra, para o anônimo (D121); também o /actuator/** (fora da saúde) e o /error para o anônimo (D113, D120)	Implementado
CREDENCIAIS_INVALIDAS	401	signin com e-mail ou senha que não conferem (mesma resposta para qualquer falha)	Implementado
TOKEN_INVALIDO	401	refresh inválido, vencido, REVOGADO (rotação, logout, reuso, troca de senha), de outro tipo (inclusive um access token), de versão antiga ou sem o claim de versão, ou de usuário excluído (D107, D133, D136); reapresentar um refresh já rotacionado também revoga todas as sessões (D134)	Implementado
ACESSO_NEGADO	403	autenticado sem permissão: papel que não pode a rota (STUDENT nas escritas do catálogo, GUARDIAN em tudo que exige papel, D122), rota ou método sem regra (D121), /actuator/** (fora da saúde) e /error, inclusive para ADMIN (D113, D120), e origem, método ou cabeçalho de CORS não permitido (D123)	Implementado
RECURSO_NAO_ENCONTRADO	404	rota liberada pela segurança mas sem recurso mapeado (hoje, só os três caminhos de saúde do actuator, que ainda não existe); a rota sem regra NÃO chega aqui: é negada com 401 ou 403 (D121)	Implementado
MATERIA_NAO_ENCONTRADA	404	matéria inexistente (ou inativa para o STUDENT)	Implementado
SUBTOPICO_NAO_ENCONTRADO	404	subtópico inexistente (para o STUDENT, também o inativo ou de matéria inativa, quando os papéis existirem)	Implementado
METODO_NAO_PERMITIDO	405	método HTTP que a segurança libera para a rota mas que o controller não atende (o método sem regra é negado com 401 ou 403, D121, então hoje não ocorre)	Implementado
FORMATO_NAO_ACEITO	406	Accept que não seja JSON	Implementado
CONFLITO	409	conflito sem código próprio	Implementado
MATERIA_NOME_DUPLICADO	409	já existe matéria com o nome	Implementado
MATERIA_COM_SUBTOPICOS	409	matéria com subtópicos, ativos ou não, não pode ser apagada	Implementado
SUBTOPICO_NOME_DUPLICADO	409	já existe subtópico com o nome nessa matéria	Implementado
MATERIA_INATIVA	409	subtópico criado, ou inativo que seria reativado, em matéria desativada	Implementado
EMAIL_DUPLICADO	409	já existe usuário com o e-mail (normalizado, D105; D37)	Implementado
TENTATIVA_EM_ANDAMENTO	409	já há tentativa em andamento nessa prova (traz detalhes.tentativaId)	Nomeado no contrato; a implementar
REQUISICAO_INVALIDA	400	URL recusada pelo firewall de segurança (barra dupla, ;jsessionid, %2e e semelhantes), mensagem fixa (D124)	Implementado
SENHA_ATUAL_INCORRETA	400	a senha atual enviada na troca de senha não confere (D139); conta no limite por usuário	Implementado
ENTITLEMENT_NEGADO	403	o recurso não está no plano do usuário, pela política de entitlements (D140, D141); mensagem fixa e detalhes { recurso, programa }	Nomeado no contrato; a implementar (o primeiro endpoint premium o produz)
LIMITE_EXCEDIDO	429	limite de tentativas excedido (D128, D129): requisições POST demais em /auth/* por origem, ou falhas de signin demais por e-mail e origem ou por origem; traz o cabeçalho Retry-After (segundos) e é a mesma resposta para e-mail existente e inexistente	Implementado
TIPO_NAO_SUPORTADO	415	corpo que não é application/json	Implementado
ERRO_INTERNO	500	erro inesperado (mensagem genérica)	Implementado

Histórico das versões: o contrato nasceu como lista de rotas e foi refinado em rodadas de perguntas e respostas (v0.2 a v0.10). Elas fecharam as regras de tentativa e prazo (D3 a D6, D16, D31, D50), de importação e versões de questão (D7, D8, D13, D18 a D21, D40 a D43, D51, D55, D56), de autenticação e tokens (D15, D23 a D26, D37, D39, D45, D49), do formato de erro (D1, D33, D44, D59, D60), das convenções de dados (D57 a D63), da listagem (D29, D64 a D70) e do formato da resposta (D71 a D75). v1.0 — versão aprovada: D76 a D116 (e as D117 a D124, depois da aprovação) resolveram as contradições e lacunas apontadas na leitura independente do contrato (paginação, ordem de validação, regras de questão e importação, tentativa e resultado, autenticação, CORS, actuator, textos e e-mail), acrescentaram o catálogo de códigos de erro e tornaram o documento autônomo.
