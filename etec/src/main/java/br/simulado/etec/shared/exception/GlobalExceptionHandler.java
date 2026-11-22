package br.simulado.etec.shared.exception;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.MethodParameter;
import org.springframework.data.core.PropertyReferenceException;
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

/**
 * Único ponto de tradução de erros: todo erro, inclusive os do próprio Spring, sai como
 * {@link ErrorResponse}, em português e com Content-Type application/json (mesmo quando o
 * Accept pedir outro formato). A resposta nunca contém pilha, nome de classe, SQL, texto
 * original da exceção nem trechos do corpo recebido; a pilha completa vai só para o log.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	static final String MENSAGEM_ERRO_INTERNO = "Ocorreu um erro interno. Tente novamente mais tarde.";

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
		List<CampoInvalido> campos = new ArrayList<>();
		for (ConstraintViolation<?> violacao : ex.getConstraintViolations()) {
			campos.add(new CampoInvalido(ultimoNo(violacao.getPropertyPath()), violacao.getMessage()));
		}
		return validacaoFalhou(campos, null);
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<CampoInvalido> campos = new ArrayList<>();
		for (ObjectError erro : ex.getBindingResult().getAllErrors()) {
			String campo = (erro instanceof FieldError erroDeCampo) ? erroDeCampo.getField() : erro.getObjectName();
			campos.add(new CampoInvalido(campo, textoDoErro(erro.getDefaultMessage())));
		}
		return validacaoFalhou(campos, headers);
	}

	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<CampoInvalido> campos = new ArrayList<>();
		ex.getParameterValidationResults().forEach(resultado -> {
			if (resultado instanceof ParameterErrors erros) {
				erros.getFieldErrors().forEach(erro -> campos
						.add(new CampoInvalido(erro.getField(), textoDoErro(erro.getDefaultMessage()))));
			}
			else {
				String nome = nomeDoParametro(resultado.getMethodParameter());
				resultado.getResolvableErrors()
						.forEach(erro -> campos.add(new CampoInvalido(nome, textoDoErro(erro.getDefaultMessage()))));
			}
		});
		return validacaoFalhou(campos, headers);
	}

	// ----- Erros do Spring (400, 404, 405, 406, 415 e demais) -----

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

	private ResponseEntity<Object> validacaoFalhou(List<CampoInvalido> campos, HttpHeaders headers) {
		campos.sort(Comparator.comparing(CampoInvalido::campo).thenComparing(CampoInvalido::mensagem));
		return resposta(HttpStatus.BAD_REQUEST, CodigoErro.VALIDACAO_FALHOU, "Há campos inválidos na requisição.",
				campos, null, headers);
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

}
