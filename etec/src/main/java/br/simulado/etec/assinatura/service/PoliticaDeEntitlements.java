package br.simulado.etec.assinatura.service;

import br.simulado.etec.assinatura.model.Decisao;
import br.simulado.etec.shared.programa.Programa;
import br.simulado.etec.assinatura.model.Recurso;
import br.simulado.etec.shared.security.UsuarioAutenticado;

/**
 * A política de entitlements (D140): a regra que diz se este usuário pode usar este recurso neste programa. É a PEÇA
 * SUBSTITUÍVEL: trocar o stub por regras reais (plano, assinatura, cota) é trocar a implementação escolhida em
 * {@code app.entitlements.politica}, sem mudar nenhum chamador. Quem chama usa o {@link EntitlementsService}, nunca a
 * política direto.
 *
 * <p>Contrato: nenhum argumento pode ser nulo (erro de programação: {@link IllegalArgumentException}, que sai como 500
 * genérico), e a identidade vem do {@link UsuarioAutenticado}, que o filtro monta lendo o BANCO a cada requisição (D87),
 * nunca do token sozinho. Recurso ou programa que a política não conhece é NEGADO.
 */
public interface PoliticaDeEntitlements {

	Decisao decidir(UsuarioAutenticado quem, Recurso recurso, Programa programa);

}
