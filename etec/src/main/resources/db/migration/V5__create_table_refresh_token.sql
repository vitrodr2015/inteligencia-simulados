-- Refresh tokens guardados (Módulo 13B, D133 a D137). SÓ o jti vai ao banco, nunca o token. Uma linha por refresh emitido;
-- a rotação, o logout, o reuso e a troca de senha só preenchem revogado_em e motivo_revogacao. Linha revogada e ainda não
-- vencida FICA: é ela que detecta o reuso. A limpeza apaga só as vencidas há mais de 1 dia.
CREATE TABLE refresh_token (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    jti              BINARY(16)  NOT NULL,
    usuario_id       BIGINT      NOT NULL,
    expira_em        DATETIME(3) NOT NULL,
    revogado_em      DATETIME(3) NULL,
    motivo_revogacao VARCHAR(20) NULL,
    criado_em        DATETIME(3) NOT NULL,
    atualizado_em    DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_token_jti UNIQUE (jti),
    CONSTRAINT fk_refresh_token_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE RESTRICT,
    CONSTRAINT ck_refresh_token_motivo CHECK (motivo_revogacao IS NULL
        OR motivo_revogacao IN ('ROTACAO', 'LOGOUT', 'REUSO', 'TROCA_SENHA', 'EXCLUSAO')),
    CONSTRAINT ck_refresh_token_revogacao CHECK ((revogado_em IS NULL) = (motivo_revogacao IS NULL)),
    INDEX idx_refresh_token_usuario_revogado (usuario_id, revogado_em),
    INDEX idx_refresh_token_expira_em (expira_em)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
