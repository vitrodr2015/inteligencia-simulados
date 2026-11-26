-- Prova (Sprint 2, fatia 2a; D149 a D151). A prova nasce ANTES das questões, em RASCUNHO, pelo POST /admin/provas.
-- O programa é interno (D143): a prova carrega programa_id, e o service atribui a ETEC; a API não o mostra.
--
-- banca, ano e semestre são OPCIONAIS (D150). A unicidade por programa + banca + ano + semestre só vale quando os TRÊS estão
-- preenchidos: no MySQL, NULL nunca conflita num índice único, então combinações parciais convivem.
-- quantidade_questoes é dado DECLARADO na criação, de 1 a 300 (D151); mudar o teto exige uma migration nova.
--
-- Os CHECKs de status comparam com a collation binária: a collation da tabela (utf8mb4_unicode_ci) ignora caixa e deixaria
-- passar 'publicada'. A regra "status PUBLICADA se e somente se publicada_em preenchida" também mora no banco.
CREATE TABLE prova (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    public_id           BINARY(16)   NOT NULL,
    programa_id         BIGINT       NOT NULL,
    titulo              VARCHAR(150) NOT NULL,
    banca               VARCHAR(80)  NULL,
    ano                 SMALLINT     NULL,
    semestre            TINYINT      NULL,
    duracao_minutos     SMALLINT     NOT NULL,
    quantidade_questoes SMALLINT     NOT NULL,
    fonte_url           VARCHAR(500) NULL,
    status              VARCHAR(20)  NOT NULL,
    publicada_em        DATETIME(3)  NULL,
    criado_em           DATETIME(3)  NOT NULL,
    atualizado_em       DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_prova_public_id UNIQUE (public_id),
    CONSTRAINT uk_prova_programa_banca_ano_semestre UNIQUE (programa_id, banca, ano, semestre),
    CONSTRAINT fk_prova_programa FOREIGN KEY (programa_id) REFERENCES programa (id) ON DELETE RESTRICT,
    CONSTRAINT ck_prova_ano CHECK (ano BETWEEN 2000 AND 2100),
    CONSTRAINT ck_prova_semestre CHECK (semestre IN (1, 2)),
    CONSTRAINT ck_prova_duracao_minutos CHECK (duracao_minutos BETWEEN 1 AND 600),
    CONSTRAINT ck_prova_quantidade_questoes CHECK (quantidade_questoes BETWEEN 1 AND 300),
    CONSTRAINT ck_prova_status CHECK (status COLLATE utf8mb4_bin IN ('RASCUNHO', 'PUBLICADA')),
    CONSTRAINT ck_prova_publicada_em CHECK ((status COLLATE utf8mb4_bin = 'PUBLICADA') = (publicada_em IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_prova_ano_semestre ON prova (ano, semestre);

CREATE INDEX idx_prova_status ON prova (status);
