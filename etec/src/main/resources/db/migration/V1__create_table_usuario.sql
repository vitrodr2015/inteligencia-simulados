CREATE TABLE usuario (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    public_id            BINARY(16)   NOT NULL,
    nome                 VARCHAR(100) NOT NULL,
    email                VARCHAR(255) NOT NULL,
    senha_hash           VARCHAR(100) NOT NULL,
    data_nascimento      DATE         NOT NULL,
    aceite_termos_versao VARCHAR(20)  NOT NULL,
    papel                VARCHAR(20)  NOT NULL,
    criado_em            DATETIME(3)  NOT NULL,
    atualizado_em        DATETIME(3)  NOT NULL,
    excluido_em          DATETIME(3)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_usuario_public_id UNIQUE (public_id),
    CONSTRAINT uk_usuario_email UNIQUE (email),
    CONSTRAINT ck_usuario_papel CHECK (papel IN ('STUDENT', 'GUARDIAN', 'ADMIN'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
