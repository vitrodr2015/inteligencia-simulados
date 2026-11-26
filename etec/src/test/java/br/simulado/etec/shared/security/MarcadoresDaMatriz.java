package br.simulado.etec.shared.security;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.simulado.etec.shared.security.MatrizDeAutorizacao.Ctx;

/**
 * Os recursos que as linhas da {@link MatrizDeAutorizacao} usam, criados SOB DEMANDA. As linhas pedem o recurso ao {@link Ctx}
 * ({@code c.provaId()} etc.), que devolve um MARCADOR ({@code {provaId}}); {@link #resolver(String)} procura os marcadores no
 * texto que a linha montou (caminho com os parâmetros, e corpo), cria SÓ os recursos que aparecem, com as dependências deles, e
 * os substitui. Ninguém declara nada por linha, então nenhuma linha pode esquecer um recurso.
 *
 * <p>Cada execução usa uma instância NOVA: o que ela cria vale só para ela (dentro da execução, o mesmo marcador dá o mesmo
 * recurso, no caminho e no corpo). Nada é guardado fora da instância, para uma execução nunca enxergar o recurso de outra.
 *
 * <p>Falha rápida: se sobrar qualquer marcador depois da substituição (desconhecido, ou sem criação) ou se a fábrica não devolver
 * um id, o teste ERRA aqui, com mensagem clara. Sem isso, um marcador cru ou um {@code null} na URL daria um 400 ou um 404 que
 * poderia coincidir com o status esperado da linha, e o teste passaria por acaso.
 */
final class MarcadoresDaMatriz {

	static final String MATERIA = "{materiaId}";

	static final String SUBTOPICO = "{subtopicoId}";

	static final String MATERIA_VAZIA = "{materiaVaziaId}";

	static final String PROVA = "{provaId}";

	static final String QUESTAO = "{questaoId}";

	/** Qualquer {@code {nome}}. Um objeto JSON não casa: depois da chave vêm aspas ou a chave que fecha. */
	private static final Pattern MARCADOR = Pattern.compile("\\{[A-Za-z][A-Za-z0-9_]*\\}");

	/** Como criar cada recurso. Cada chamada cria um recurso NOVO e devolve o id público. */
	interface Fabrica {

		String materia();

		String subtopico(String materiaId);

		/** Uma matéria sem nenhum subtópico (a linha que a apaga espera 204). */
		String materiaVazia();

		String prova();

		/** Uma questão na prova dada, num subtópico PRÓPRIO dentro da matéria dada. */
		String questao(String provaId, String materiaId);

	}

	private final Fabrica fabrica;

	private final Map<String, String> criados = new HashMap<>();

	MarcadoresDaMatriz(Fabrica fabrica) {
		this.fabrica = fabrica;
	}

	/** O contexto que as linhas recebem: os recursos são marcadores; o id inexistente e o sufixo são valores de verdade. */
	static Ctx contexto(String idInexistente, String sufixo) {
		return new Ctx(MATERIA, SUBTOPICO, MATERIA_VAZIA, PROVA, QUESTAO, idInexistente, sufixo);
	}

	/** Troca os marcadores do texto pelos ids, criando o que faltar; {@code null} continua {@code null} (linha sem corpo). */
	String resolver(String texto) {
		if (texto == null) {
			return null;
		}
		String resolvido = texto;
		for (String marcador : new String[] { MATERIA, SUBTOPICO, MATERIA_VAZIA, PROVA, QUESTAO }) {
			if (resolvido.contains(marcador)) {
				resolvido = resolvido.replace(marcador, valor(marcador));
			}
		}
		exigirSemMarcador(resolvido);
		return resolvido;
	}

	private static void exigirSemMarcador(String texto) {
		Matcher sobra = MARCADOR.matcher(texto);
		if (sobra.find()) {
			throw new IllegalStateException("Marcador não resolvido na linha da matriz: " + sobra.group() + " em \"" + texto
					+ "\". Ensine o MarcadoresDaMatriz a criar esse recurso.");
		}
	}

	/** O recurso do marcador nesta execução: criado na primeira vez em que aparece (com as dependências) e reaproveitado. */
	private String valor(String marcador) {
		String existente = criados.get(marcador);
		if (existente != null) {
			return existente;
		}
		String novo = switch (marcador) {
			case MATERIA -> fabrica.materia();
			case SUBTOPICO -> fabrica.subtopico(valor(MATERIA));
			case MATERIA_VAZIA -> fabrica.materiaVazia();
			case PROVA -> fabrica.prova();
			case QUESTAO -> fabrica.questao(valor(PROVA), valor(MATERIA));
			default -> throw new IllegalStateException("Marcador não resolvido na linha da matriz: " + marcador + ".");
		};
		if (novo == null || novo.isBlank()) {
			throw new IllegalStateException("O recurso do marcador " + marcador + " não foi criado (a fábrica não devolveu um id).");
		}
		criados.put(marcador, novo);
		return novo;
	}

}
