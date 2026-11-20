-- Dados de exemplo só para os testes (nenhum seed em migration). Sem id explícito, para não
-- quebrar a sequência do H2.
INSERT INTO usuario (public_id, nome, email, senha_hash, data_nascimento, aceite_termos_versao, papel, criado_em, atualizado_em)
VALUES ('11111111-1111-1111-1111-111111111111', 'Aluno de Exemplo', 'aluno.exemplo@exemplo.invalid',
        'hash-falso-nao-e-real', DATE '2008-05-10', 'v1', 'STUDENT',
        TIMESTAMP '2026-01-01 00:00:00', TIMESTAMP '2026-01-01 00:00:00');
