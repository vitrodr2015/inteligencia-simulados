CREATE TABLE materia (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    public_id     BINARY(16)   NOT NULL,
    nome          VARCHAR(100) NOT NULL,
    descricao     VARCHAR(255) NULL,
    ativo         BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em     DATETIME(3)  NOT NULL,
    atualizado_em DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_materia_public_id UNIQUE (public_id),
    CONSTRAINT uk_materia_nome UNIQUE (nome)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
