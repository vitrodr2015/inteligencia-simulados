/**
 * Limite de tentativas (429, D128 a D130): contadores compartilhados no banco, origem da requisição e o filtro por
 * origem. Não conhece nenhum domínio: quem decide O QUE contar (por exemplo, as falhas do signin) é o service do domínio.
 */
package br.simulado.etec.shared.security.limite;
