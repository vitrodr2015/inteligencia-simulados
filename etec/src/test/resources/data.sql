-- Dados de exemplo só para os testes (nenhum seed em migration). Sem id explícito, para não
-- quebrar a sequência do H2.
INSERT INTO usuario (public_id, nome, email, senha_hash, data_nascimento, aceite_termos_versao, papel, criado_em, atualizado_em, versao_token)
VALUES ('11111111-1111-1111-1111-111111111111', 'Aluno de Exemplo', 'aluno.exemplo@exemplo.invalid',
        'hash-falso-nao-e-real', DATE '2008-05-10', 'v1', 'STUDENT',
        TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-01-01 00:00:00', 0);

-- O programa ETEC (no MySQL real ele vem da migration V7); o H2 monta o schema pelo Hibernate, então entra aqui.
INSERT INTO programa (public_id, codigo, nome, criado_em, atualizado_em)
VALUES ('22222222-2222-2222-2222-222222222222', 'ETEC', 'ETEC',
        TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-01-01 00:00:00');
