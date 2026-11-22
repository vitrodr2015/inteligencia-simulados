-- Subtópico (contrato, seção 4.2). Segue o padrão da tabela materia (V2): id interno BIGINT, public_id
-- BINARY(16) único, auditoria em DATETIME(3), utf8mb4 com utf8mb4_unicode_ci (a comparação ignora caixa e acento).
--
-- materia_id é NOT NULL e a chave estrangeira é ON DELETE RESTRICT: o banco é a garantia de que uma matéria
-- com subtópicos (ativos ou não) nunca é apagada. A aplicação traduz essa violação em 409 MATERIA_COM_SUBTOPICOS.
CREATE TABLE subtopico (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    public_id     BINARY(16)   NOT NULL,
    materia_id    BIGINT       NOT NULL,
    nome          VARCHAR(100) NOT NULL,
    ativo         BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em     DATETIME(3)  NOT NULL,
    atualizado_em DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_subtopico_public_id UNIQUE (public_id),
    -- Único DENTRO da matéria, sem distinguir caixa nem acento (collation da coluna). Este índice também serve
    -- à chave estrangeira (materia_id é o prefixo) e ao filtro por matéria ordenado por nome.
    CONSTRAINT uk_subtopico_materia_nome UNIQUE (materia_id, nome),
    CONSTRAINT fk_subtopico_materia FOREIGN KEY (materia_id) REFERENCES materia (id) ON DELETE RESTRICT,
    -- Listagem sem filtro de matéria, ordenada por nome ou por data de criação (o id interno desempata).
    INDEX idx_subtopico_nome (nome),
    INDEX idx_subtopico_criado_em (criado_em)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
