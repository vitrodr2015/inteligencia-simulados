Contrato da API — Fase 1 (rascunho v0.8)

Escrito no formato do Módulo 2 do Playbook2. Regra do módulo: o contrato é revisado e aprovado antes de qualquer código; depois de aprovado, qualquer mudança é registrada na seção 10. Substitui, para a fase 1, a lista de alto nível de apis-backend.md.

Estado: decisões D1 a D63 decididas, exceto D22 e D24 (pendências externas).

1. Convenções gerais (valem para todas as rotas)
Tema	Decisão
Base e formato	/api/v1, JSON em UTF-8. Só JSON; nenhum outro formato na fase 1
Ids	Campo id é um UUID (o id numérico do banco nunca sai). UUID malformado = 400; inexistente = 404
Data e hora	ISO-8601 em UTC com exatamente 3 casas de milissegundos, mesmo quando são zero (2026-11-20T15:04:05.123Z e 2026-11-20T15:04:05.000Z). As datas são gravadas e devolvidas com precisão de milissegundos: a aplicação trunca na origem, para o banco não arredondar (D63)
Data simples	uuuu-MM-dd (aaaa-mm-dd), leitura estrita: data inexistente ou fora do formato (2000-02-30, 2000-2-3) = 400, com campos apontando o campo e a mensagem "Data inválida. Use o formato aaaa-mm-dd."; nunca é corrigida em silêncio (D60)
Entidade	Nunca sai pela API: DTOs de entrada e de saída. Campos de saída que não são do cliente (id, ativo, status, datas, nota) são ignorados se vierem no corpo. Campo de entrada que não muda depois de criado (materiaId, email, dataNascimento, provaId, numero...): valor igual ao atual é aceito; valor diferente = 400 com codigo CAMPO_IMUTAVEL. Como a comparação é com o valor atual, esse 400 só é avaliado depois da existência (404) (D27, D42, D48)
Referência no corpo	Se o corpo aponta para um recurso que não existe (ex.: materiaId, provaId em POST /tentativas), responde 400. Se a rota aponta (/materias/{id}), responde 404 (D14)
Ordem de validação	autenticação e autorização (401/403) → corpo (400) → id (400) → existência (404) → conflito (409) → alteração (D17)
Autorização antes da existência	Quem não pode recebe 403 até para um id que não existe
Idempotência	PUT de resposta e de gabarito, disable/enable, publish e finalizar podem ser repetidos com o mesmo resultado. O logout também (D15). Repetir disable/enable devolve o mesmo resultado e não altera atualizadoEm (D62)
Nome	Campo nome (de matéria, subtópico, usuário e demais recursos): o servidor remove os espaços das pontas ANTES de validar; nome vazio depois disso = 400 (D57)
Criação (201)	Location relativo (/api/v1/recurso/{id}) e o corpo traz o recurso completo, não só o id (D58). Exceção: POST /auth/register, que não tem Location (o recurso só existe em /me)
PUT	É substituição: campo opcional omitido no corpo vira nulo (por exemplo, descricao da matéria) (D61). Exceções, por decisões próprias: PUT /admin/provas/{id}/gabarito, que é parcial (D13), e PUT /tentativas/{id}/respostas/{questaoId}, em que tempoGastoMs omitido mantém o valor salvo (D30). Campos que o servidor controla (ativo, status, datas) nunca mudam por PUT
Limite de abuso	/auth/* responde 429 com Retry-After ao exceder o limite: 10 requisições por minuto por IP em /auth/* e 5 tentativas de signin por minuto por e-mail, ambos configuráveis por propriedade (D25; números iniciais)
Cabeçalhos	Respostas com token levam Cache-Control: no-store
Formatação nos textos	Os textos de questão (enunciado, textoApoio, alternativas[].texto, explicacao) aceitam só as marcas <b>, <u>, <i>, <sup>, <sub> e <br> (D12, D32). Os caracteres <, > e & literais se escrevem &lt;, &gt; e &amp;. Atributos nas tags e tags mal fechadas ou mal aninhadas = 400. O servidor recusa (400) qualquer outra marcação HTML e o front também limpa por segurança. Os limites de tamanho contam caracteres
CORS	As origens permitidas vêm de configuração por ambiente (D34)

Formato de erro (ErrorResponse) — o mesmo em todo status de erro, inclusive 401 e 403:

json
{ "dataHora": "2026-11-20T15:04:05.123Z", "status": 409, "erro": "Conflict", "codigo": "MATERIA_NOME_DUPLICADO", "mensagem": "Já existe uma matéria com esse nome." }

O campo codigo é estável (texto em MAIÚSCULAS com sublinhado, por exemplo TENTATIVA_EM_ANDAMENTO); o front decide pelo codigo, nunca pelo texto da mensagem (D33). O campo detalhes (opcional, um objeto) leva dados estruturados de alguns erros (D44): no 409 de tentativa em andamento, { "tentativaId": "..." }; no 409 do publish da prova, { "questoesPendentes": [3, 7] }, com os números das questões pendentes; no 409 de reimportação, { "numerosEmConflito": [4, 9] }. Os valores de codigo são definidos na implementação, um por situação de erro do contrato. Em erros 400 de validação, campos (opcional): [ { "campo": "nome", "mensagem": "O nome deve ter de 2 a 100 caracteres." } ] (decisão D1), com no máximo uma mensagem por campo. O texto da mensagem não é contratual: o front decide pelo codigo (D59). O 500 devolve mensagem genérica; a pilha só vai para o log.

Lista paginada — parâmetros page (a partir de 0), size (padrão 20, máximo 50, valor maior é reduzido ao máximo) e sort no formato campo,direção (por exemplo sort=nome,desc; só campos da lista de cada recurso; outro campo = 400). page ou size inválidos (negativo, zero ou não numérico) = 400. Página além do fim devolve 200 com lista vazia. O filtro nome é "contém", ignorando maiúsculas e acentos (D29). Resposta:

json
{ "conteudo": [ ... ], "pagina": 0, "tamanho": 20, "totalElementos": 135, "totalPaginas": 7 }

Desempate sempre por id (nas questões, por id e versao), para a paginação ser estável.

Autenticação: Authorization: Bearer <accessToken>. Papéis: STUDENT, GUARDIAN (reservado, sem rotas próprias na fase 1) e ADMIN. Rota nova nasce negada. Sem token válido = 401; sem permissão = 403.

2. Quem pode o quê
Operação	Anônimo	STUDENT	ADMIN
POST /auth/register, /auth/signin, /auth/refresh, /auth/logout; GET /termos/vigente	✅	✅	✅
GET/PATCH/DELETE /me	❌	✅	✅
GET de matérias, subtópicos e provas	❌	✅ (só ativos; provas só PUBLICADAS)	✅ (todos)
Escrever matérias e subtópicos; publicar prova	❌	❌	✅
/admin/** (provas, questões, importação, gabarito)	❌	❌	✅
/tentativas/**	❌	✅ (só as próprias)	❌
GET /actuator/health/**	✅	✅	✅

Tentativa de outro aluno responde 404 (não revela que existe).

3. Autenticação e conta

Campos de Usuário

Campo	Tipo	Regra	Quem preenche
id	UUID	gerado	saída
nome	texto	obrigatório; o servidor remove os espaços das pontas antes de validar; depois disso, 2 a 100 caracteres (vazio = 400, D57)	entrada e saída
email	texto	obrigatório, formato válido, até 255, único, normalizado em minúsculas	entrada e saída
senha	texto	obrigatório, 8 a 72 bytes (limite do BCrypt)	só entrada (nunca sai)
dataNascimento	data	obrigatória, no passado, no formato aaaa-mm-dd (data inválida = 400, D60)	entrada e saída
aceiteTermosVersao	texto	obrigatório, igual à versão devolvida por GET /termos/vigente; diferente = 400 (D23)	só entrada
papel	enum	sempre STUDENT no cadastro; o corpo não escolhe	saída
menor	booleano	calculado: menor de 18 anos. Na fase 1 é só uma marca, não muda comportamento (D24)	saída
criadoEm	data/hora	UTC	saída

Endpoints

Verbo	Rota	Sucesso	Erros
GET	/termos/vigente (pública)	200 {versao, url}	—
POST	/auth/register	201 com o usuário (mesmo corpo de GET /me), sem tokens e sem Location; o aluno entra depois pelo signin (D23)	400, 409 (e-mail já cadastrado, D37), 429
POST	/auth/signin	200 {accessToken, refreshToken, tokenType, expiresIn}; expiresIn em segundos (900, D39)	400, 401 (mesma resposta e tempo parecido para qualquer falha), 429
POST	/auth/refresh (refresh no corpo, nunca na URL)	200 (novo par; o refresh usado é invalidado; reusar um refresh já invalidado dá 401 e revoga todos os refresh do usuário, D39, D49)	400, 401 (inválido, vencido, revogado ou do tipo errado), 429
POST	/auth/logout (anônimo; o refresh no corpo é a credencial, D15)	204 sempre, inclusive para refresh desconhecido, vencido ou já revogado; repetir não dá erro; revoga só o refresh enviado (D39)	400 (corpo sem refresh), 429
GET	/me	200	401
PATCH	/me (só nome; email e dataNascimento com o valor atual são aceitos, diferentes = 400 CAMPO_IMUTAVEL, D48)	200	400, 401
DELETE	/me	204 (exclusão de conta com anonimização, ver D9 e a subseção 3.2)	401, 409 (último ADMIN)

Não existem na fase 1: recuperação de senha, login com Google, criação de ADMIN por API (o primeiro admin nasce por bootstrap controlado, ver 3.2), vínculo com responsável, envio de e-mail.

3.1 JWT (D39)
Assinatura HS256. Access token de 15 minutos, com os claims sub (UUID do usuário), papel, tipo=access, jti e exp. Refresh token de 24 horas, tipo=refresh, com o jti guardado no banco; o tempo de vida é propriedade configurável. expiresIn em segundos (900). Cada /auth/refresh invalida o refresh usado. O reuso de um refresh já invalidado revoga todos os refresh do usuário; não existe conceito de família por login (D49). O logout revoga só o refresh enviado. A cada requisição o filtro consulta o banco para confirmar que o usuário existe e não foi excluído: o token não é a fonte da verdade (D26). O segredo de assinatura vem só de variável de ambiente, sem valor padrão.

3.2 Exclusão de conta e primeiro admin (D26, D45)
O e-mail anonimizado vira removido-{uuid}@invalid, então o e-mail original pode ser recadastrado como conta nova. Todos os refresh do usuário são revogados na hora. Tentativas em andamento viram EXPIRADA. O último ADMIN não pode se excluir (409). Bootstrap do admin: as variáveis de ambiente ADMIN_EMAIL e ADMIN_SENHA criam o admin só se ainda não existir nenhum; se já existe admin, as variáveis são ignoradas. Sem as variáveis e sem nenhum admin: em desenvolvimento e teste, só um aviso no log; em produção, a aplicação não sobe (D45). Nunca vão para o repositório.

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
GET	/materias (filtros nome, ativo; sort: nome, criadoEm; padrão nome). STUDENT só vê ativas; ativo=false enviado por STUDENT = 400 (D28, D47)	200 lista paginada	400, 401
GET	/materias/{id} (STUDENT: matéria inativa = 404)	200	400, 401, 404
PUT	/materias/{id} (substituição: troca nome e descricao, e a descricao omitida vira nula; nunca altera ativo, D61)	200	400, 401, 403, 404, 409
PATCH	/materias/{id}/disable e /enable	200, idempotentes: repetir devolve o mesmo resultado e não altera atualizadoEm (D62)	400, 401, 403, 404
DELETE	/materias/{id}	204	400, 401, 403, 404, 409 (tem subtópicos, ativos ou não)
4.2 Subtópico
Campo	Tipo	Regra	Quem preenche
id	UUID	gerado	saída
materiaId	UUID	obrigatório na criação, deve existir (senão 400); não muda depois (valor igual ao atual é aceito; diferente = 400, codigo CAMPO_IMUTAVEL, D48); matéria desativada = 409, codigo MATERIA_INATIVA (D54)	entrada e saída
nome	texto	obrigatório; o servidor remove os espaços das pontas antes de validar; depois disso, 2 a 100 caracteres (vazio = 400, D57); único dentro da matéria	entrada e saída
ativo, criadoEm, atualizadoEm		como em Matéria	saída
Verbo	Rota	Sucesso	Erros
POST	/subtopicos	201 + Location relativo e o subtópico completo (D58)	400, 401, 403, 409 (nome já existe na matéria; ou matéria desativada, codigo MATERIA_INATIVA, D54)
GET	/subtopicos (filtros materiaId, nome, ativo; sort: nome, criadoEm). STUDENT só vê subtópicos ativos de matéria ativa; ativo=false enviado por STUDENT = 400 (D28, D47)	200 lista paginada	400, 401
GET	/subtopicos/{id} (STUDENT: subtópico inativo ou de matéria inativa = 404)	200	400, 401, 404
PUT	/subtopicos/{id} (só nome)	200	400, 401, 403, 404, 409
PATCH	/subtopicos/{id}/disable e /enable	200, idempotentes: repetir devolve o mesmo resultado e não altera atualizadoEm (D62)	400, 401, 403, 404
DELETE	/subtopicos/{id}	204	400, 401, 403, 404, 409 (tem questões, de qualquer status)
4.3 Prova
Campo	Tipo	Regra	Quem preenche
id	UUID	gerado	saída
titulo	texto	obrigatório, até 150	vem da importação; saída
banca	texto	obrigatório, até 80	importação; saída
ano	inteiro	2000 a 2100	importação; saída
semestre	inteiro	1 ou 2	importação; saída
duracaoMinutos	inteiro	1 a 600 (a prova da ETEC tem 180)	importação; saída
quantidadeQuestoes	inteiro	calculado	saída
fonteUrl	texto	opcional, https, até 500	importação; saída
status	enum	RASCUNHO ou PUBLICADA	saída
publicadaEm, criadoEm	data/hora	UTC	saída

Uma prova é única por banca + ano + semestre (D20).

Verbo	Rota	Sucesso	Erros
GET	/provas (filtros ano, semestre; ADMIN também status; sort: ano, semestre; padrão ano decrescente). STUDENT que envia status (qualquer valor) = 400 (D28, D47)	200 lista paginada	400, 401
GET	/provas/{id} (STUDENT só vê PUBLICADA; senão 404)	200	400, 401, 404
PATCH	/provas/{id}/publish (ADMIN)	200, idempotente	400, 401, 403, 404, 409 (sem questões, ou com questão ainda não publicada, o que inclui questão sem gabarito; detalhes.questoesPendentes lista os números das questões pendentes, D44)

Não existe POST de prova (ela nasce pela importação). PUT e DELETE existem só sob /admin/provas e só em RASCUNHO (seção 5, D21). Prova publicada é imutável: o que muda depois de publicada é a questão, por nova versão (D18). Uma prova só é publicada com o gabarito completo (D13).

5. Questões, importação e gabarito (somente ADMIN)

Campos de Questão

Campo	Tipo	Regra	Quem preenche
id	UUID	identifica a questão (estável entre versões)	saída
versao	inteiro	1, 2, ...; cada edição de questão publicada cria uma nova	saída
provaId	UUID	obrigatório, deve existir; não muda depois (valor igual ao atual é aceito; diferente = 400, codigo CAMPO_IMUTAVEL, D48)	entrada e saída
numero	inteiro	≥ 1, único dentro da prova; não muda depois (valor igual ao atual é aceito; diferente = 400, codigo CAMPO_IMUTAVEL; número errado: descartar o rascunho e reimportar, D42, D48)	entrada e saída
subtopicoId	UUID	obrigatório, deve existir e estar ativo	entrada e saída
textoApoio	texto	opcional, até 10.000 (o "Leia o texto..." que a questão usa; copiado em cada questão que o usa, D11)	entrada e saída
grupoApoio	inteiro	opcional, ≥ 1; questões da mesma prova com o mesmo valor compartilham o mesmo texto de apoio, para o app exibi-lo uma vez	entrada e saída
enunciado	texto	obrigatório, até 10.000	entrada e saída
imagemUrl	texto	opcional, https, até 500, e o host precisa estar na propriedade app.imagens.hosts-permitidos, definida por ambiente (em desenvolvimento, localhost; onde as imagens ficam fisicamente fica para o módulo de infraestrutura; D36, D46) (charge, tirinha, mapa, gráfico)	entrada e saída
alternativas	lista	exatamente 5 itens {letra, texto}, com as letras A, B, C, D, E (D2)	entrada e saída
gabarito	letra	uma das letras A a E. Opcional no rascunho; obrigatório para publicar (D13)	entrada e saída
dificuldade	enum	FACIL, MEDIA, DIFICIL; opcional	entrada e saída
explicacao	texto	opcional, até 10.000	entrada e saída
status	enum	RASCUNHO, PUBLICADA, ARQUIVADA	saída
Verbo	Rota	Sucesso	Erros
POST	/admin/importacoes	200 {provaId, questoesCriadas, questoesIgnoradas}	400 (JSON inválido, qualquer questão inválida ou mais de 100 questões: tudo ou nada, D7, D55), 401, 403, 409 (prova já publicada, ou questão do mesmo numero com conteúdo diferente; detalhes.numerosEmConflito lista os números, D40), 413 (corpo acima de 2 MB, D55)
PUT	/admin/provas/{id} (só RASCUNHO; troca titulo, banca, ano, semestre, duracaoMinutos e fonteUrl)	200	400, 401, 403, 404, 409 (prova publicada, ou outra prova com a mesma banca + ano + semestre)
DELETE	/admin/provas/{id} (só RASCUNHO)	204	400, 401, 403, 404, 409 (prova publicada, ou com questão já publicada alguma vez; nada é apagado junto, D43)
PUT	/admin/provas/{id}/gabarito (corpo { "itens": [ {numero, gabarito} ] })	200 {atualizadas}, idempotente; pode ser parcial	400 (letra inválida, numero repetido ou que não existe na prova; tudo ou nada), 401, 403, 404, 409 (prova publicada ou questão já publicada)
GET	/admin/questoes (filtros provaId, subtopicoId, status; status aceita só RASCUNHO e PUBLICADA, ARQUIVADA = 400; sort: numero, criadoEm). Traz só a versão mais recente de cada questão, e o filtro status vale para essa versão exibida (D56); a ARQUIVADA se lê por ?versao=n (D41)	200 lista paginada	400, 401, 403
GET	/admin/questoes/{id} (devolve o rascunho aberto, se houver, senão a última publicada; ?versao=n lê uma versão específica, inclusive ARQUIVADA)	200	400, 401, 403, 404 (questão ou versão inexistente)
PUT	/admin/questoes/{id}	200	400 (inclui provaId ou numero com valor diferente do atual, codigo CAMPO_IMUTAVEL, D27, D42, D48), 401, 403, 404, 409 (só RASCUNHO pode ser editada)
POST	/admin/questoes/{id}/versoes	201 + Location (aponta para /admin/questoes/{id}; cópia em RASCUNHO)	400, 401, 403, 404, 409 (já há rascunho aberto)
PATCH	/admin/questoes/{id}/publish	200, idempotente; a versão anterior vira ARQUIVADA	400, 401, 403, 404, 409 (sem subtópico, subtópico desativado, sem gabarito, gabarito inválido ou enunciado vazio)
DELETE	/admin/questoes/{id} (se nunca foi publicada, apaga a questão; se tem versão publicada, descarta só o rascunho aberto)	204	400, 401, 403, 404, 409 (questão publicada sem rascunho aberto)

Importação: entrada { prova: {titulo, banca, ano, semestre, duracaoMinutos, fonteUrl}, questoes: [ {numero, subtopicoId, textoApoio, grupoApoio, enunciado, imagemUrl, alternativas, gabarito, dificuldade, explicacao} ] }, com gabarito opcional. Tudo entra como RASCUNHO. A prova é identificada por banca + ano + semestre: reimportar uma prova em RASCUNHO reutiliza o provaId e atualiza titulo, duracaoMinutos e fonteUrl; se ela já estiver publicada, 409 (D20). Se já existe questão no mesmo numero com conteúdo diferente, responde 409 listando os números em conflito (nunca sobrescreve em silêncio); para editar essa questão, usa-se PUT /admin/questoes/{id} (D40). Reimportar o mesmo conteúdo não duplica: o hash identifica o que já existe e conta em questoesIgnoradas. O hash inclui prova, número e conteúdo da questão, e não inclui o gabarito, para que aplicar ou corrigir o gabarito não faça uma reimportação criar questões de novo. Limites: corpo de 2 MB (acima disso, 413) e 100 questões por importação (acima disso, 400) (D55). O hash é gerado na importação, guardado com a questão e nunca muda: nem por PUT, nem por nova versão, e todas as versões da questão carregam o mesmo hash (D51).

Fluxo esperado: importar a prova (sem gabarito) → aplicar o gabarito oficial de uma vez (PUT .../gabarito) → revisar → publicar as questões → publicar a prova. Para corrigir uma questão ou gabarito depois de publicado: POST /versoes, editar o rascunho e publicar; tentativas novas usam a última versão publicada e as antigas mantêm o snapshot (D18).

Não existem: POST de questão avulsa (as questões entram pela importação).

6. Tentativas (aluno)

Campos de Tentativa (saída)

Campo	Tipo	Regra
id	UUID	gerado
provaId	UUID	da prova
status	enum	EM_ANDAMENTO, FINALIZADA, EXPIRADA
iniciadaEm, prazoEm	data/hora	UTC, definidos pelo servidor (prazoEm = início + duração da prova)
tempoRestanteSegundos	inteiro	só em andamento; vale 0 entre prazoEm e prazoEm + 3 s (D16)
finalizadaEm	data/hora	nulo até encerrar
nota	inteiro	nulo até encerrar. Na ETEC, nota é a quantidade de acertos (0 até o total de questões), sem percentual e sem desconto por erro (D3)
questoes	lista	{ordem, questaoId, versao, textoApoio, grupoApoio, enunciado, imagemUrl, alternativas[{letra, texto}]} (5 alternativas) sem gabarito. A ordem é a numeração da prova e as alternativas são sempre A a E, sem embaralhar (D30)
respostas	lista	{questaoId, alternativa, tempoGastoMs} já salvas

Resposta (entrada): alternativa (uma das letras A a E, ou null para deixar em branco) e tempoGastoMs (inteiro ≥ 0, opcional). tempoGastoMs é o total acumulado da questão; se for omitido, mantém o valor já salvo (D30).

Verbo	Rota	Sucesso	Erros
POST	/tentativas ({provaId}); passa por podeIniciarTentativa(usuario, prova), que devolve sempre verdadeiro na fase 1 (D35)	201 + Location, com a tentativa completa (mesmo corpo de GET /tentativas/{id}, D52); fixa as questões (última versão publicada de cada uma) e o prazoEm (snapshot)	400 (prova inexistente ou não publicada, a mesma resposta para as duas, D14), 401, 403, 409 (já há tentativa em andamento nessa prova, D4; detalhes.tentativaId informa o id da tentativa aberta, D31, D44)
GET	/tentativas/{id}	200	400, 401, 403, 404
PUT	/tentativas/{id}/respostas/{questaoId}	200, idempotente (vale a última)	400 (alternativa fora de A a E, D53), 401, 403, 404 (tentativa inexistente, de outro aluno, ou questaoId que não pertence à tentativa, D53), 409 (tentativa encerrada ou prazo vencido além da tolerância, D5)
POST	/tentativas/{id}/finalizar	200 {id, status, finalizadaEm, nota, total, acertos, erros, emBranco}, sem itens (D52); repetir devolve o mesmo. Entre prazoEm e prazoEm + 3 s encerra como FINALIZADA; passada a tolerância, devolve 200 com status EXPIRADA (D31, D50)	400, 401, 403, 404
GET	/tentativas/{id}/resultado	200 {nota, total, acertos, erros, emBranco, itens[{ordem, questaoId, alternativaMarcada, gabarito, correta, explicacao}]} (na ETEC nota = acertos; gabarito e explicação vêm da versão do snapshot)	400, 401, 403, 404, 409 (ainda em andamento)
GET	/tentativas (histórico; filtros status, provaId; sort: iniciadaEm; padrão decrescente)	200 lista paginada	400, 401, 403

Depois de FINALIZADA ou EXPIRADA, o aluno pode refazer a mesma prova, sem limite na fase 1 (o D4 só barra duas tentativas simultâneas).

Encerramento automático: a tentativa só expira quando agora > prazoEm + 3 s (tolerância do D5); até lá continua EM_ANDAMENTO e aceita respostas. Passado esse ponto, ela vira EXPIRADA e é pontuada com o que foi salvo (D6). Se o aluno chamar finalizar antes desse ponto, ainda que depois do prazoEm, a tentativa encerra como FINALIZADA (D50). Isso acontece ao ler/salvar e por um job de varredura; o cliente nunca decide (D16). Não existem: PUT, PATCH e DELETE de tentativa; resposta fora do prazo; gabarito antes de encerrar.

7. Operação
Verbo	Rota	Sucesso	Erros
GET	/actuator/health e /actuator/health/liveness, /readiness (fora de /api/v1; sem detalhes)	200 UP	503 (banco fora do ar)

Qualquer outro caminho de /actuator e /error: negado. A documentação OpenAPI (/v3/api-docs e /swagger-ui) fica ligada em desenvolvimento e homologação e desligada em produção (D34).

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
D22	Questão anulada fica fora da fase 1. Atenção: como a prova só publica com gabarito completo, uma anulada bloquearia a publicação; conferir como a banca trata, para prever um campo anulada	⏳ Pendência externa (P3)
D23	GET /termos/vigente (pública) devolve {versao, url}. aceiteTermosVersao errada = 400. O register devolve 201 com o usuário, sem tokens	✅ Confirmada
D24	Na fase 1, menor é só uma marca calculada, sem mudar comportamento. Antes do beta, definir idade mínima e consentimento do responsável com apoio jurídico	⏳ Pendência externa (P2)
D25	Limites do 429: 10 requisições por minuto por IP em /auth/* e 5 tentativas de signin por minuto por e-mail, configuráveis por propriedade (números iniciais). Tokens: ver 3.1 e D39	✅ Confirmada
D26	DELETE /me: e-mail vira removido-{uuid}@invalid (o original pode ser recadastrado); refresh revogados na hora; tentativas em andamento viram EXPIRADA; último ADMIN = 409. Bootstrap por ADMIN_EMAIL e ADMIN_SENHA só se não existir admin. O filtro consulta o banco a cada requisição	✅ Confirmada
D27	Campos de saída (id, ativo...) são ignorados no corpo; campos de entrada que não mudam (materiaId, email, provaId...) dão 400 se o valor for diferente do atual; valor igual é aceito (refinada pelo D48)	✅ Confirmada
D28	Aluno vê só matérias e subtópicos ativos (e subtópicos só de matéria ativa); admin vê todos. status em /provas enviado por STUDENT = 400. Publicar questão com subtópico desativado = 409. Desativar matéria não desativa subtópicos em cascata. DELETE bloqueado por qualquer subtópico ou questão, ativo ou não	✅ Confirmada
D29	sort=campo,direção. page ou size inválidos = 400. Página além do fim = 200 vazio. Filtro nome é "contém", sem distinguir maiúsculas e acentos	✅ Confirmada
D30	tempoGastoMs é o total acumulado da questão; omitido, mantém o valor salvo. Ordem das questões = numeração da prova; alternativas sempre A a E, sem embaralhar	✅ Confirmada
D31	O aluno pode refazer a prova sem limite na fase 1. O 409 informa o id da tentativa aberta. finalizar depois da tolerância do prazo devolve 200 com EXPIRADA (refinada pelo D50)	✅ Confirmada
D32	Texto: <, > e & se escrevem &lt;, &gt;, &amp;. Atributos nas tags e tags mal fechadas ou mal aninhadas = 400. <br> entra na lista. Limite de 10.000 conta caracteres	✅ Confirmada
D33	ErrorResponse ganha o campo codigo estável (ex.: TENTATIVA_EM_ANDAMENTO)	✅ Confirmada
D34	Origens do CORS por configuração por ambiente. Swagger ligado em desenvolvimento e homologação, desligado em produção	✅ Confirmada
D35	POST /tentativas passa por podeIniciarTentativa(usuario, prova), que devolve sempre verdadeiro na fase 1	✅ Confirmada
D36	imagemUrl só aceita hosts de uma lista permitida (o armazenamento do projeto)	✅ Confirmada
D37	409 no cadastro revela e-mail existente; o 429 é a mitigação. Não há envio de e-mail na fase 1	✅ Confirmada
D38	Removido o 403 dos GET de matéria, subtópico e prova (só podem dar 401)	✅ Confirmada
D39	Tokens (valores do Playbook): HS256; access de 15 min com claims sub (UUID), papel, tipo=access, jti e exp; refresh de 24 h (propriedade configurável), tipo=refresh, com jti guardado no banco; expiresIn em segundos (900). Cada /auth/refresh invalida o refresh usado; reuso de um refresh já invalidado revoga todos os refresh do usuário (D49); logout revoga só o refresh enviado	✅ Confirmada
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
9. Fora da fase 1 (de propósito)

Pagamento e planos, entitlements como API (na fase 1 é um serviço interno que libera tudo, por podeIniciarTentativa), simulado curto, relatórios por subtópico, notas de corte, responsável e relatório semanal, plano de estudo, caderno de erros, IA, recuperação de senha, login com Google, envio de e-mail, questão anulada (D22), comportamento diferente para menores de idade (D24), outros exames (ENEM, OAB).

10. Registro de mudanças após a aprovação

(vazio)

11. Pendências abertas (só as externas)

P2. Menores e LGPD (externa, D24): idade mínima e consentimento do responsável, com apoio jurídico, antes do beta.
P3. Questão anulada (externa, D22): conferir como a banca trata a anulada e prever o campo anulada.

As pendências técnicas P1 e P4 a P12 foram decididas na v0.6 (D39 a D47). A P9 (catálogo dos valores de codigo) ficou como tarefa de implementação (D44).

Histórico do rascunho: v0.2 — D2 resolvida e correção da referência da seção de mudanças. v0.3 — D1 a D10 confirmadas; D3 muda: nota é a quantidade de acertos; duração da prova da ETEC (180 min) anotada. v0.4 — D11 a D13 confirmadas; novos campos textoApoio e grupoApoio; gabarito opcional no rascunho; nova rota PUT /admin/provas/{id}/gabarito; regra de formatação mínima nos textos (o servidor recusa outras marcações HTML). v0.5 — primeira sabotagem: D14 a D38 (25 respostas); logout anônimo; nova rota GET /termos/vigente; novas rotas PUT e DELETE /admin/provas/{id} e DELETE /admin/questoes/{id}; campo codigo no erro; nova ordem de validação (401/403 antes de 400); <br> na formatação; subseção 3.1 (JWT, proposta) e seção 11 (pendências abertas). v0.6 — D39 a D47 confirmadas (P1 corrigida com os valores do Playbook e P4 a P12 decididas); campo detalhes no ErrorResponse; tokens HS256 com claims definidos e refresh de 24 h; reimportação atualiza titulo, duracaoMinutos e fonteUrl e dá 409 em conflito de questão; numero imutável; filtro de status de questões só RASCUNHO e PUBLICADA; aluno com ativo=false ou status recebe 400; hosts de imagem por propriedade; bootstrap do admin por ambiente; seção 11 reduzida às pendências externas. v0.7 — D48 a D56 decididas; campo de entrada imutável aceita o valor igual ao atual e recusa o diferente com codigo CAMPO_IMUTAVEL (D27 e D42 refinadas); reuso de refresh invalidado revoga todos os refresh do usuário, sem conceito de família (D39 refinada); finalizar dentro da tolerância de 3 s encerra como FINALIZADA (D31 refinada); hash da questão imutável; POST /tentativas devolve a tentativa completa e finalizar devolve o resumo sem itens; questaoId alheio = 404 e letra inválida = 400; subtópico em matéria desativada = 409 (MATERIA_INATIVA); importação acima de 2 MB = 413 e acima de 100 questões = 400 (D20 refinada); filtro status de questões vale para a versão mais recente; linha Estado corrigida. v0.8 — D57 a D63 decididas; o servidor remove os espaços das pontas do nome antes de validar (D57); Location relativo e 201 com o recurso completo (D58); campos com no máximo uma mensagem por campo e texto de mensagem não contratual (D59); data inválida = 400 com campos e mensagem fixa (D60); PUT é substituição, com as exceções registradas na seção 1 (D61); disable/enable repetido não altera atualizadoEm (D62); datas com precisão de milissegundos, truncadas na origem (D63); exemplo de campos atualizado.
