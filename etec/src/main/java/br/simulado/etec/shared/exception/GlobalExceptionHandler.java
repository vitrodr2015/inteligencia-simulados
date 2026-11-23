package br.simulado.etec.shared.exception;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.validation.method.ParameterErrors;

import br.simulado.etec.shared.exception.ErrorResponse.CampoInvalido;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import tools.jackson.databind.exc.InvalidFormatException;

/**
 * Único ponto de tradução de erros: todo erro, inclusive os do próprio Spring, sai como
 * {@link ErrorResponse}, em português e com Content-Type application/json (mesmo quando o
 * Accept pedir outro formato). A resposta nunca contém pilha, nome de classe, SQL, texto
 * original da exceção nem trechos do corpo recebido; a pilha completa vai só para o log.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	public static final String MENSAGEM_ERRO_INTERNO = "Ocorreu um erro interno. Tente novamente mais tarde.";

	/** Texto exato da D60. */
	static final String MENSAGEM_DATA_INVALIDA = "Data inválida. Use o formato aaaa-mm-dd.";

	private final Clock relogio;

	public GlobalExceptionHandler(Clock relogio) {
		this.relogio = relogio;
	}

	// ----- Exceções de negócio -----

	@ExceptionHandler(ResourceNotFoundException.class)
	ResponseEntity<Object> aoNaoEncontrar(ResourceNotFoundException ex) {
		return resposta(HttpStatus.NOT_FOUND, ex.getCodigo(), ex.getMessage(), null, ex.getDetalhes(), null);
	}

	@ExceptionHandler(InvalidRequestException.class)
	ResponseEntity<Object> aoRequisitarInvalido(InvalidRequestException ex) {
		return resposta(HttpStatus.BAD_REQUEST, ex.getCodigo(), ex.getMessage(), ex.getCampos(), ex.getDetalhes(), null);
	}

	@ExceptionHandler(ConflictException.class)
	ResponseEntity<Object> aoConflitar(ConflictException ex) {
		return resposta(HttpStatus.CONFLICT, ex.getCodigo(), ex.getMessage(), null, ex.getDetalhes(), null);
	}

	/** Signin com credenciais erradas ou refresh inválido (401). A mensagem é genérica e fixa. */
	@ExceptionHandler(AutenticacaoException.class)
	ResponseEntity<Object> aoFalharAutenticacao(AutenticacaoException ex) {
		return resposta(HttpStatus.UNAUTHORIZED, ex.getCodigo(), ex.getMessage(), null, null, null);
	}

	/** Limite de tentativas (429): Retry-After em segundos e no-store, igual ao 429 do filtro de origem (D129). */
	@ExceptionHandler(LimiteExcedidoException.class)
	ResponseEntity<Object> aoExcederLimite(LimiteExcedidoException ex) {
		HttpHeaders cabecalhos = new HttpHeaders();
		cabecalhos.set(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSegundos()));
		cabecalhos.setCacheControl(CacheControl.noStore());
		return resposta(HttpStatus.TOO_MANY_REQUESTS, ex.getCodigo(), ex.getMessage(), null, null, cabecalhos);
	}

	/** Autenticado sem permissão (403), por código que precisa do papel: defesa em profundidade além da cadeia. */
	@ExceptionHandler(AcessoNegadoException.class)
	ResponseEntity<Object> aoNegarAcesso(AcessoNegadoException ex) {
		return resposta(HttpStatus.FORBIDDEN, ex.getCodigo(), ex.getMessage(), null, null, null);
	}

	// ----- Validação -----

	/**
	 * Defesa: o conversor de paginação só deixa passar campos da lista do recurso, então esta exceção do
	 * Spring Data (propriedade inexistente na ordenação) não deveria chegar aqui. Se chegar, é 400, e não 500.
	 */
	@ExceptionHandler(PropertyReferenceException.class)
	ResponseEntity<Object> aoOrdenarPorPropriedadeInexistente(PropertyReferenceException ex) {
		return resposta(HttpStatus.BAD_REQUEST, CodigoErro.ORDENACAO_INVALIDA, "A ordenação pedida é inválida.", null,
				null, null);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<Object> aoViolarRestricoes(ConstraintViolationException ex) {
		List<Violacao> violacoes = new ArrayList<>();
		for (ConstraintViolation<?> violacao : ex.getConstraintViolations()) {
			String restricao = violacao.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
			violacoes.add(new Violacao(ultimoNo(violacao.getPropertyPath()), violacao.getMessage(), prioridade(restricao)));
		}
		return validacaoFalhou(violacoes, null);
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<Violacao> violacoes = new ArrayList<>();
		for (ObjectError erro : ex.getBindingResult().getAllErrors()) {
			String campo = (erro instanceof FieldError erroDeCampo) ? erroDeCampo.getField() : erro.getObjectName();
			violacoes.add(new Violacao(campo, textoDoErro(erro.getDefaultMessage()), prioridade(erro)));
		}
		return validacaoFalhou(violacoes, headers);
	}

	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<Violacao> violacoes = new ArrayList<>();
		ex.getParameterValidationResults().forEach(resultado -> {
			if (resultado instanceof ParameterErrors erros) {
				erros.getFieldErrors().forEach(erro -> violacoes
						.add(new Violacao(erro.getField(), textoDoErro(erro.getDefaultMessage()), prioridade(erro))));
			}
			else {
				String nome = nomeDoParametro(resultado.getMethodParameter());
				resultado.getResolvableErrors().forEach(
						erro -> violacoes.add(new Violacao(nome, textoDoErro(erro.getDefaultMessage()), prioridade(erro))));
			}
		});
		return validacaoFalhou(violacoes, headers);
	}

	// ----- Erros do Spring (400, 404, 405, 406, 415 e demais) -----

	/**
	 * Data em texto fora do formato aaaa-mm-dd, ou inexistente (D60): 400 VALIDACAO_FALHOU apontando o campo, com a
	 * mensagem fixa do contrato. Valor que NÃO é texto (número, objeto, lista) continua CORPO_ILEGIVEL (D86), e a
	 * mensagem nunca repete o que o cliente enviou.
	 */
	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		String campoDeData = campoDeDataInvalida(ex);
		if (campoDeData != null) {
			LOG.debug("Requisição rejeitada (400): data inválida");
			return validacaoFalhou(List.of(new Violacao(campoDeData, MENSAGEM_DATA_INVALIDA, 2)), headers);
		}
		return super.handleHttpMessageNotReadable(ex, headers, status, request);
	}

	/** Nome do campo se a causa é um texto que não vira LocalDate; senão null. */
	private static String campoDeDataInvalida(Throwable erro) {
		for (Throwable atual = erro; atual != null; atual = (atual.getCause() == atual) ? null : atual.getCause()) {
			if (atual instanceof InvalidFormatException formato && LocalDate.class.equals(formato.getTargetType())
					&& formato.getValue() instanceof String && !formato.getPath().isEmpty()) {
				return formato.getPath().get(formato.getPath().size() - 1).getPropertyName();
			}
		}
		return null;
	}

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		if (statusCode.is5xxServerError()) {
			LOG.error("Erro interno do framework", ex);
		}
		else {
			// Só o tipo: a mensagem de exceções do Spring pode conter trechos do que o cliente enviou.
			LOG.debug("Requisição rejeitada ({}): {}", statusCode.value(), ex.getClass().getSimpleName());
		}
		Traducao traducao = traduzir(ex, statusCode);
		return resposta(statusCode, traducao.codigo(), traducao.mensagem(), null, null, headers);
	}

	private static Traducao traduzir(Exception ex, HttpStatusCode status) {
		return switch (ex) {
			case HttpMessageNotReadableException _ -> new Traducao(CodigoErro.CORPO_ILEGIVEL,
					"O corpo da requisição está ilegível ou em formato inválido.");
			case MissingServletRequestParameterException e -> new Traducao(CodigoErro.PARAMETRO_AUSENTE,
					"O parâmetro '" + e.getParameterName() + "' é obrigatório.");
			case MethodArgumentTypeMismatchException e -> new Traducao(CodigoErro.PARAMETRO_INVALIDO,
					"O parâmetro '" + e.getName() + "' tem um valor inválido.");
			case TypeMismatchException _ -> new Traducao(CodigoErro.PARAMETRO_INVALIDO,
					"Um dos valores informados é inválido.");
			case NoHandlerFoundException _ -> new Traducao(CodigoErro.RECURSO_NAO_ENCONTRADO,
					"Recurso não encontrado.");
			case NoResourceFoundException _ -> new Traducao(CodigoErro.RECURSO_NAO_ENCONTRADO,
					"Recurso não encontrado.");
			case HttpRequestMethodNotSupportedException _ -> new Traducao(CodigoErro.METODO_NAO_PERMITIDO,
					"Método HTTP não permitido para este recurso.");
			case HttpMediaTypeNotAcceptableException _ -> new Traducao(CodigoErro.FORMATO_NAO_ACEITO,
					"Formato de resposta não aceito. Esta API responde apenas em JSON.");
			case HttpMediaTypeNotSupportedException _ -> new Traducao(CodigoErro.TIPO_NAO_SUPORTADO,
					"Tipo de conteúdo não suportado. Envie o corpo em application/json.");
			default -> status.is5xxServerError()
					? new Traducao(CodigoErro.ERRO_INTERNO, MENSAGEM_ERRO_INTERNO)
					: new Traducao(CodigoErro.PARAMETRO_INVALIDO, "A requisição é inválida.");
		};
	}

	// ----- 500 -----

	@ExceptionHandler(Exception.class)
	ResponseEntity<Object> aoFalhar(Exception ex) {
		LOG.error("Erro interno não tratado", ex);
		return resposta(HttpStatus.INTERNAL_SERVER_ERROR, CodigoErro.ERRO_INTERNO, MENSAGEM_ERRO_INTERNO, null,
				null, null);
	}

	// ----- Apoio -----

	/**
	 * D59: no máximo UMA mensagem por campo. Quando o campo viola mais de uma restrição, vale a de maior prioridade,
	 * numa ordem fixa por TIPO de restrição (obrigatório, depois tamanho, depois formato e as demais); se ainda
	 * empatar, a mensagem que vem primeiro em ordem alfabética. A saída fica ordenada pelo nome do campo.
	 */
	private ResponseEntity<Object> validacaoFalhou(List<Violacao> violacoes, HttpHeaders headers) {
		Map<String, Violacao> escolhidas = new LinkedHashMap<>();
		Comparator<Violacao> ordem = Comparator.comparingInt(Violacao::prioridade).thenComparing(Violacao::mensagem);
		for (Violacao violacao : violacoes) {
			escolhidas.merge(violacao.campo(), violacao, (a, b) -> ordem.compare(a, b) <= 0 ? a : b);
		}
		List<CampoInvalido> campos = new ArrayList<>();
		escolhidas.values().forEach(v -> campos.add(new CampoInvalido(v.campo(), v.mensagem())));
		campos.sort(Comparator.comparing(CampoInvalido::campo));
		return resposta(HttpStatus.BAD_REQUEST, CodigoErro.VALIDACAO_FALHOU, "Há campos inválidos na requisição.",
				campos, null, headers);
	}

	/** 0 = obrigatório, 1 = tamanho ou faixa, 2 = formato e as demais restrições. */
	static int prioridade(String restricao) {
		return switch (restricao) {
			case "NotNull", "NotBlank", "NotEmpty", "Null" -> 0;
			case "Size", "TamanhoEmPontosDeCodigo", "TamanhoEmBytes", "Length", "Min", "Max", "DecimalMin", "DecimalMax",
					"Positive", "PositiveOrZero", "Negative", "NegativeOrZero", "Digits", "Range" -> 1;
			default -> 2;
		};
	}

	/** O nome da restrição é o último código do erro (por exemplo "NotNull", depois dos mais específicos). */
	private static int prioridade(MessageSourceResolvable erro) {
		String[] codigos = erro.getCodes();
		return (codigos == null || codigos.length == 0) ? 2 : prioridade(codigos[codigos.length - 1]);
	}

	private ResponseEntity<Object> resposta(HttpStatusCode status, CodigoErro codigo, String mensagem,
			List<CampoInvalido> campos, Map<String, Object> detalhes, HttpHeaders headers) {
		ErrorResponse corpo = ErrorResponse.de(relogio, status.value(), codigo, mensagem, campos, detalhes);
		// Content-Type fixo: sem isso o Accept (por exemplo application/xml) faria o próprio erro virar 406.
		return ResponseEntity.status(status).headers(headers).contentType(MediaType.APPLICATION_JSON).body(corpo);
	}

	private static String nomeDoParametro(MethodParameter parametro) {
		String nome = parametro.getParameterName();
		return (nome != null) ? nome : "parametro" + parametro.getParameterIndex();
	}

	private static String ultimoNo(Path caminho) {
		String ultimo = null;
		for (Path.Node no : caminho) {
			ultimo = no.getName();
		}
		return (ultimo != null) ? ultimo : caminho.toString();
	}

	private static String textoDoErro(String mensagem) {
		return (mensagem != null) ? mensagem : "valor inválido";
	}

	private record Traducao(CodigoErro codigo, String mensagem) {
	}

	private record Violacao(String campo, String mensagem, int prioridade) {
	}

}
