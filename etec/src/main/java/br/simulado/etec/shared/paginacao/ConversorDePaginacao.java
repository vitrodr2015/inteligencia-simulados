package br.simulado.etec.shared.paginacao;

import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import br.simulado.etec.shared.exception.CodigoErro;
import br.simulado.etec.shared.exception.InvalidRequestException;

/**
 * Converte os parâmetros crus {@code page}, {@code size} e {@code sort} num {@link Pageable} SEGURO
 * (contrato, seção 1 e D29). É o ÚNICO lugar onde o tamanho padrão e o máximo são definidos.
 *
 * <p>Por que não usamos o resolvedor de {@code Pageable} do Spring: ele CORRIGE EM SILÊNCIO valores
 * inválidos ({@code page=-1} vira 0, {@code size=0} vira o padrão, {@code size} não numérico é
 * ignorado) e o contrato exige 400. Aqui:
 * <ul>
 * <li>{@code page}: inteiro a partir de 0; negativo ou não numérico = 400 (PARAMETRO_INVALIDO).
 * Muito grande vale como "além do fim" (200 com lista vazia): é limitado à maior página cujo
 * deslocamento (página x tamanho) cabe num inteiro, porque o Spring Data JPA falha com
 * {@code Page offset exceeds Integer.MAX_VALUE} (500) acima disso. Nesse caso o {@code pagina} do
 * envelope devolve a página limitada, e não a pedida.</li>
 * <li>{@code size}: padrão {@value #TAMANHO_PADRAO}, máximo {@value #TAMANHO_MAXIMO}; maior que o
 * máximo é REDUZIDO ao máximo, sem erro; zero, negativo ou não numérico = 400.</li>
 * <li>{@code sort}: {@code campo,direção} ({@code asc} ou {@code desc}; sem direção vale asc), só
 * com campos da lista do recurso; qualquer outra coisa = 400 (ORDENACAO_INVALIDA). O texto digitado
 * nunca vai para a consulta.</li>
 * <li>O desempate por {@code id} (o id numérico interno) é SEMPRE acrescentado depois da
 * ordenação pedida, em ordem crescente; sem ele, linhas com a mesma chave de ordenação podem
 * repetir ou sumir entre páginas.</li>
 * </ul>
 */
public final class ConversorDePaginacao {

	public static final int TAMANHO_PADRAO = 20;

	public static final int TAMANHO_MAXIMO = 50;

	/** Propriedade usada no desempate: o id numérico interno, que nunca sai pela API. */
	static final String PROPRIEDADE_DE_DESEMPATE = "id";

	private static final Pattern INTEIRO_SEM_SINAL = Pattern.compile("[0-9]+");

	private static final Pattern FORMATO_DO_SORT = Pattern.compile("([A-Za-z][A-Za-z0-9]*)(?:,(asc|desc))?",
			Pattern.CASE_INSENSITIVE);

	private ConversorDePaginacao() {
	}

	/**
	 * Versão para os controllers: recebe os valores CRUS do parâmetro {@code sort}
	 * ({@code request.getParameterValues("sort")}). Mais de um {@code sort} no pedido é 400
	 * (ORDENACAO_INVALIDA, D101).
	 *
	 * <p>Por que não basta receber um {@code String sort}: quando o parâmetro se repete, o Spring junta os
	 * valores com vírgula ({@code sort=nome&sort=desc} vira {@code "nome,desc"}, que é um sort válido), e o
	 * segundo parâmetro seria aceito em silêncio.
	 */
	public static Pageable converterRequisicao(String page, String size, String[] sorts, CamposOrdenaveis campos) {
		if (sorts != null && sorts.length > 1) {
			throw ordenacaoInvalida(campos);
		}
		String sort = (sorts == null || sorts.length == 0) ? null : sorts[0];
		return converter(page, size, sort, campos);
	}

	public static Pageable converter(String page, String size, String sort, CamposOrdenaveis campos) {
		BigInteger paginaPedida = converterPagina(page);
		int tamanho = converterTamanho(size);
		int pagina = limitarPagina(paginaPedida, tamanho);
		Sort ordenacao = converterOrdenacao(sort, campos);
		return PageRequest.of(pagina, tamanho, ordenacao.and(Sort.by(Sort.Order.asc(PROPRIEDADE_DE_DESEMPATE))));
	}

	private static BigInteger converterPagina(String page) {
		return (page == null) ? BigInteger.ZERO : inteiroSemSinal("page", page);
	}

	/** Maior página cujo deslocamento (página x tamanho) cabe num inteiro; acima disso é "além do fim", nunca erro. */
	private static int limitarPagina(BigInteger paginaPedida, int tamanho) {
		BigInteger maxima = BigInteger.valueOf(Integer.MAX_VALUE / tamanho);
		return paginaPedida.min(maxima).intValueExact();
	}

	private static int converterTamanho(String size) {
		if (size == null) {
			return TAMANHO_PADRAO;
		}
		BigInteger valor = inteiroSemSinal("size", size);
		if (valor.signum() == 0) {
			throw parametroInvalido("size", "deve ser um inteiro maior que zero");
		}
		return valor.min(BigInteger.valueOf(TAMANHO_MAXIMO)).intValueExact();
	}

	/** Só dígitos ASCII: negativo, decimal, vazio, com espaço ou com letra são 400. */
	private static BigInteger inteiroSemSinal(String parametro, String texto) {
		if (!INTEIRO_SEM_SINAL.matcher(texto).matches()) {
			throw parametroInvalido(parametro, "deve ser um número inteiro maior ou igual a "
					+ ("page".equals(parametro) ? "0" : "1"));
		}
		return new BigInteger(texto);
	}

	private static Sort converterOrdenacao(String sort, CamposOrdenaveis campos) {
		if (sort == null) {
			return campos.padrao();
		}
		Matcher formato = FORMATO_DO_SORT.matcher(sort);
		if (!formato.matches()) {
			throw ordenacaoInvalida(campos);
		}
		String propriedade = campos.propriedade(formato.group(1)).orElseThrow(() -> ordenacaoInvalida(campos));
		Sort.Direction direcao = (formato.group(2) == null) ? Sort.Direction.ASC
				: Sort.Direction.fromString(formato.group(2));
		return Sort.by(new Sort.Order(direcao, propriedade));
	}

	private static InvalidRequestException parametroInvalido(String parametro, String regra) {
		return new InvalidRequestException(CodigoErro.PARAMETRO_INVALIDO,
				"O parâmetro '" + parametro + "' " + regra + ".");
	}

	/** Não repete o valor recebido na mensagem: só diz o formato e os campos aceitos. */
	private static InvalidRequestException ordenacaoInvalida(CamposOrdenaveis campos) {
		return new InvalidRequestException(CodigoErro.ORDENACAO_INVALIDA,
				"O parâmetro 'sort' é inválido. Use campo,direção (asc ou desc) com um dos campos: "
						+ campos.listaParaMensagem() + ".");
	}

}
