-- Versão do token (Módulo 13B, D136): o access e o refresh levam este número num claim, e o filtro recusa o token cujo claim
-- seja diferente do valor do usuário. Incrementar derruba, de uma vez, TODOS os tokens já emitidos (troca de senha, reuso de
-- refresh, encerrar todas as sessões), sem relógio e sem depender do iat. Usuários que já existem começam em 0.
ALTER TABLE usuario ADD COLUMN versao_token INT NOT NULL DEFAULT 0;
