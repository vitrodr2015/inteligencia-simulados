-- Versão da questão e alternativas (Sprint 2, fatia 2b-i; D146, D18). Cada edição de questão publicada cria uma versão nova; a
-- tentativa guarda o snapshot pela versão (por isso o subtópico é da VERSÃO e não da identidade, D146: a FK com ON DELETE RESTRICT
-- impede apagar um subtópico que tem versão de questão em QUALQUER status).
--
-- Os textos longos são TEXT com CHECK de CHAR_LENGTH (não VARCHAR(10000): três colunas assim estourariam o limite de linha do
-- InnoDB). Os CHECKs de enumeração comparam com a collation binária (a da tabela ignora caixa).
--
-- "Um rascunho aberto e uma publicada por questão" (plano, item 11): colunas GERADAS (valem 1 no estado e NULL nos demais) com
-- índice único junto de questao_id. NULL não conflita num índice único, então vale qualquer número de versões ARQUIVADA. As colunas
-- geradas não são mapeadas na entidade.
CREATE TABLE questao_versao (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    questao_id        BIGINT       NOT NULL,
    versao            INT          NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    subtopico_id      BIGINT       NOT NULL,
    texto_apoio       TEXT         NULL,
    grupo_apoio       INT          NULL,
    enunciado         TEXT         NOT NULL,
    imagem_url        VARCHAR(500) NULL,
    gabarito          CHAR(1)      NULL,
    dificuldade       VARCHAR(10)  NULL,
    explicacao        TEXT         NULL,
    publicada_em      DATETIME(3)  NULL,
    criado_em         DATETIME(3)  NOT NULL,
    atualizado_em     DATETIME(3)  NOT NULL,
    rascunho_aberto   TINYINT GENERATED ALWAYS AS (CASE WHEN status COLLATE utf8mb4_bin = 'RASCUNHO' THEN 1 ELSE NULL END) STORED,
    publicada_vigente TINYINT GENERATED ALWAYS AS (CASE WHEN status COLLATE utf8mb4_bin = 'PUBLICADA' THEN 1 ELSE NULL END) STORED,
    PRIMARY KEY (id),
    CONSTRAINT uk_questao_versao_questao_versao UNIQUE (questao_id, versao),
    CONSTRAINT uk_questao_versao_rascunho_aberto UNIQUE (questao_id, rascunho_aberto),
    CONSTRAINT uk_questao_versao_publicada_vigente UNIQUE (questao_id, publicada_vigente),
    CONSTRAINT fk_questao_versao_questao FOREIGN KEY (questao_id) REFERENCES questao (id) ON DELETE RESTRICT,
    CONSTRAINT fk_questao_versao_subtopico FOREIGN KEY (subtopico_id) REFERENCES subtopico (id) ON DELETE RESTRICT,
    CONSTRAINT ck_questao_versao_versao CHECK (versao >= 1),
    CONSTRAINT ck_questao_versao_status CHECK (status COLLATE utf8mb4_bin IN ('RASCUNHO', 'PUBLICADA', 'ARQUIVADA')),
    CONSTRAINT ck_questao_versao_texto_apoio CHECK (CHAR_LENGTH(texto_apoio) <= 10000),
    CONSTRAINT ck_questao_versao_enunciado CHECK (CHAR_LENGTH(enunciado) <= 10000),
    CONSTRAINT ck_questao_versao_explicacao CHECK (CHAR_LENGTH(explicacao) <= 10000),
    CONSTRAINT ck_questao_versao_grupo_apoio CHECK (grupo_apoio >= 1),
    CONSTRAINT ck_questao_versao_gabarito CHECK (gabarito COLLATE utf8mb4_bin IN ('A', 'B', 'C', 'D', 'E')),
    CONSTRAINT ck_questao_versao_dificuldade CHECK (dificuldade COLLATE utf8mb4_bin IN ('FACIL', 'MEDIA', 'DIFICIL'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_questao_versao_subtopico ON questao_versao (subtopico_id);

-- As 5 alternativas de uma versão. "Exatamente 5, A a E" fica no service (e em teste): o banco só garante a letra válida e única.
CREATE TABLE alternativa (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    questao_versao_id BIGINT        NOT NULL,
    letra             CHAR(1)       NOT NULL,
    texto             VARCHAR(2000) NOT NULL,
    criado_em         DATETIME(3)   NOT NULL,
    atualizado_em     DATETIME(3)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_alternativa_versao_letra UNIQUE (questao_versao_id, letra),
    CONSTRAINT fk_alternativa_questao_versao FOREIGN KEY (questao_versao_id) REFERENCES questao_versao (id) ON DELETE RESTRICT,
    CONSTRAINT ck_alternativa_letra CHECK (letra COLLATE utf8mb4_bin IN ('A', 'B', 'C', 'D', 'E'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
