-- A data de publicação acompanha o status da versão da questão (Sprint 2, fatia 4, passada 2a; D179), como já é na prova (V8):
-- RASCUNHO não tem publicada_em; PUBLICADA e ARQUIVADA têm (a arquivada guarda a data em que foi publicada). Até aqui a API só
-- criava rascunhos, sem data, então os dados que ela gravou satisfazem a regra; o MySQL confere as linhas existentes ao criar o
-- CHECK, e a migration falha se houver versão publicada ou arquivada sem data (só possível com SQL manual). A comparação é
-- binária, como nos outros CHECKs de enumeração (a collation da tabela ignora caixa).
ALTER TABLE questao_versao
    ADD CONSTRAINT ck_questao_versao_publicada_em CHECK ((status COLLATE utf8mb4_bin = 'RASCUNHO') = (publicada_em IS NULL));
