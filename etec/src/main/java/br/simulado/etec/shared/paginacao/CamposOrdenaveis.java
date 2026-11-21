package br.simulado.etec.shared.paginacao;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Sort;

/**
 * Lista FIXA dos campos pelos quais um recurso aceita ordenação, mais a ordenação padrão. É a
 * defesa contra {@code sort} arbitrário: o que o cliente escreve nunca chega à consulta; só o nome
 * da propriedade que está nesta lista chega.
 *
 * <p>Cada recurso declara a sua, ligando o nome público do campo (o que a API aceita em
 * {@code sort}) à propriedade da entidade.
 */
public final class CamposOrdenaveis {

	private final Map<String, String> propriedadePorCampo;

	private final Sort padrao;

	private CamposOrdenaveis(Map<String, String> propriedadePorCampo, Sort padrao) {
		this.propriedadePorCampo = Map.copyOf(propriedadePorCampo);
		this.padrao = padrao;
	}

	/** Campos cujo nome público é igual ao da propriedade da entidade; {@code padrao} é a ordenação sem {@code sort}. */
	public static CamposOrdenaveis de(Sort padrao, String... campos) {
		Map<String, String> mapa = new LinkedHashMap<>();
		for (String campo : campos) {
			mapa.put(campo, campo);
		}
		return new CamposOrdenaveis(mapa, padrao);
	}

	/** Propriedade da entidade para o campo público, ou vazio se o campo não é ordenável. */
	Optional<String> propriedade(String campo) {
		return Optional.ofNullable(propriedadePorCampo.get(campo));
	}

	Sort padrao() {
		return padrao;
	}

	/** Nomes públicos, em ordem alfabética, para a mensagem de erro. */
	String listaParaMensagem() {
		return String.join(", ", propriedadePorCampo.keySet().stream().sorted().toList());
	}

}
