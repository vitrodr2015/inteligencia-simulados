-- Questão, a IDENTIDADE (Sprint 2, fatia 2b-i; D145, D146, D152). Só o que não muda entre as versões: a prova, o numero, o tipo e o
-- hash do conteúdo (D51: gerado no cadastro e nunca muda; todas as versões da questão carregam o mesmo hash). O conteúdo editável
-- (enunciado, alternativas, gabarito, subtópico...) mora em questao_versao e alternativa (migration V10).
--
-- Sem programa_id: a questão deriva o programa pela prova (D143). O tipo é interno (D145): hoje só MULTIPLA_ESCOLHA, e o CHECK
-- compara com a collation binária (a da tabela ignora caixa). O numero é único dentro da prova; o índice também serve à chave
-- estrangeira (prova_id é o prefixo). O índice (prova_id, hash_conteudo) NÃO é único: a idempotência do cadastro (D153) é
-- decidida pelo service, com a linha da prova travada.
CREATE TABLE questao (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    public_id     BINARY(16)  NOT NULL,
    prova_id      BIGINT      NOT NULL,
    numero        SMALLINT    NOT NULL,
    tipo          VARCHAR(30) NOT NULL,
    hash_conteudo CHAR(64)    NOT NULL,
    criado_em     DATETIME(3) NOT NULL,
    atualizado_em DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_questao_public_id UNIQUE (public_id),
    CONSTRAINT uk_questao_prova_numero UNIQUE (prova_id, numero),
    CONSTRAINT fk_questao_prova FOREIGN KEY (prova_id) REFERENCES prova (id) ON DELETE RESTRICT,
    CONSTRAINT ck_questao_numero CHECK (numero >= 1),
    CONSTRAINT ck_questao_tipo CHECK (tipo COLLATE utf8mb4_bin = 'MULTIPLA_ESCOLHA')
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_questao_prova_hash ON questao (prova_id, hash_conteudo);
