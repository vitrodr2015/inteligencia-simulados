-- Contadores do limite de tentativas (Módulo 13A, D128 a D130). Uma linha por chave: a chave é um HMAC-SHA256 em hexadecimal
-- (nunca o e-mail nem o IP em claro). A janela é fixa e a linha expira sozinha; a limpeza apaga as vencidas.
CREATE TABLE limite_tentativa (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    chave         VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    contador      INT         NOT NULL,
    janela_inicio DATETIME(3) NOT NULL,
    janela_fim    DATETIME(3) NOT NULL,
    criado_em     DATETIME(3) NOT NULL,
    atualizado_em DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_limite_tentativa_chave UNIQUE (chave),
    CONSTRAINT ck_limite_tentativa_contador CHECK (contador >= 0),
    INDEX idx_limite_tentativa_janela_fim (janela_fim)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
