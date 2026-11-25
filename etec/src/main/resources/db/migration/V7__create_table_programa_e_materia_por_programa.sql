-- Programa de prova (D143): a tabela nasce com UMA linha, a ETEC, e a matéria passa a pertencer a um programa. O
-- programa é interno: não aparece em rota, corpo nem resposta da API v1. O subtópico NÃO ganha coluna: o programa dele é o da
-- matéria (a regra do plano: programa_id só nas raízes, matéria e prova).
--
-- codigo casa com o record Programa (shared.programa): o CHECK usa o mesmo formato [A-Z][A-Z0-9_]{1,19}, e a coluna
-- é comparada com a caixa EXATA (o 'c' do REGEXP_LIKE vale mesmo com a collation utf8mb4_unicode_ci, que ignora caixa).
CREATE TABLE programa (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    public_id     BINARY(16)   NOT NULL,
    codigo        VARCHAR(20)  NOT NULL,
    nome          VARCHAR(100) NOT NULL,
    criado_em     DATETIME(3)  NOT NULL,
    atualizado_em DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_programa_public_id UNIQUE (public_id),
    CONSTRAINT uk_programa_codigo UNIQUE (codigo),
    CONSTRAINT ck_programa_codigo CHECK (REGEXP_LIKE(codigo, '^[A-Z][A-Z0-9_]{1,19}$', 'c'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

INSERT INTO programa (public_id, codigo, nome, criado_em, atualizado_em)
VALUES (UNHEX(REPLACE(UUID(), '-', '')), 'ETEC', 'ETEC', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

-- Matéria: acrescenta NULL, preenche as linhas que já existem com a ETEC e só então fecha como NOT NULL.
ALTER TABLE materia ADD COLUMN programa_id BIGINT NULL AFTER public_id;

UPDATE materia SET programa_id = (SELECT id FROM programa WHERE codigo = 'ETEC');

ALTER TABLE materia MODIFY COLUMN programa_id BIGINT NOT NULL;

-- O nome passa a ser único POR PROGRAMA (D144). O índice novo também serve à chave estrangeira (programa_id é o prefixo).
ALTER TABLE materia ADD CONSTRAINT uk_materia_programa_nome UNIQUE (programa_id, nome);

ALTER TABLE materia ADD CONSTRAINT fk_materia_programa FOREIGN KEY (programa_id) REFERENCES programa (id) ON DELETE RESTRICT;

ALTER TABLE materia DROP INDEX uk_materia_nome;
