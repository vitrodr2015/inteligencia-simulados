package br.simulado.etec.auth.service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.simulado.etec.auth.dto.TrocaDeSenhaRequest;
import br.simulado.etec.auth.model.MotivoRevogacao;
import br.simulado.etec.shared.exception.CodigoErro;
import br.simulado.etec.shared.exception.InvalidRequestException;
import br.simulado.etec.shared.exception.InvalidTokenException;
import br.simulado.etec.shared.security.UsuarioAutenticado;
import br.simulado.etec.shared.security.limite.Escopo;
import br.simulado.etec.shared.security.limite.LimiteDeTentativas;
import br.simulado.etec.shared.security.limite.RateLimitProperties;
import br.simulado.etec.shared.validacao.PoliticaDeSenha;
import br.simulado.etec.shared.validacao.PoliticaDeSenha.Violacao;
import br.simulado.etec.usuarios.model.Papel;
import br.simulado.etec.usuarios.model.Usuario;
import br.simulado.etec.usuarios.service.UsuarioService;

/**
 * Troca de senha do próprio usuário logado (Módulo 13B, D139). Ordem: (autenticação e papel, na cadeia de segurança) →
 * 429 (contador POR USUÁRIO, "conta antes", fora da transação principal) → 400 de formato → senha atual → alteração.
 *
 * <p>A senha atual errada dá 400 {@code SENHA_ATUAL_INCORRETA}, com UMA comparação de BCrypt (o mesmo custo de um signin
 * falho); uma senha atual acima de 72 bytes nunca é comparada com o hash real (o BCrypt a truncaria). A alteração roda numa
 * transação: novo hash, encerrar todas as sessões (revoga os refresh e incrementa a versão do token) com o motivo
 * TROCA_SENHA. O contador zera só no sucesso. Nada daqui loga senha, hash ou e-mail. <b>Não é {@code @Transactional}</b>: o
 * contador roda em transação própria (REQUIRES_NEW), e a transação da alteração é aberta aqui, só em volta da alteração.
 */
@Service
public class TrocaDeSenhaService {

	private static final String SENHA_DE_COMPARACAO = "texto-so-para-gastar-o-mesmo-tempo";

	static final String MENSAGEM_SENHA_ATUAL_INCORRETA = "A senha atual está incorreta.";

	private final UsuarioService usuarios;

	private final SessaoService sessoes;

	private final PasswordEncoder encoder;

	private final LimiteDeTentativas limite;

	private final RateLimitProperties limites;

	private final TransactionTemplate transacao;

	/** Hash de uma senha aleatória descartada, para comparar quando a senha atual não pode ser comparada com o hash real. */
	private final String hashFicticio;

	public TrocaDeSenhaService(UsuarioService usuarios, SessaoService sessoes, PasswordEncoder encoder,
			LimiteDeTentativas limite, RateLimitProperties limites, PlatformTransactionManager gerenteDeTransacao) {
		this.usuarios = usuarios;
		this.sessoes = sessoes;
		this.encoder = encoder;
		this.limite = limite;
		this.limites = limites;
		this.transacao = new TransactionTemplate(gerenteDeTransacao);
		this.hashFicticio = encoder.encode(UUID.randomUUID().toString());
	}

	/**
	 * @throws br.simulado.etec.shared.exception.LimiteExcedidoException 429, antes de qualquer validação
	 * @throws InvalidRequestException 400: campo ausente, política da nova senha, senha atual incorreta ou nova igual à atual
	 */
	public void trocar(UsuarioAutenticado quem, TrocaDeSenhaRequest dados) {
		String chave = quem.id().toString();
		limite.consumir(Escopo.TROCA_SENHA_USUARIO, chave, limites.trocaSenhaUsuario());

		if (dados == null || dados.senhaAtual() == null) {
			throw InvalidRequestException.campoInvalido("senhaAtual", "A senha atual é obrigatória.");
		}
		if (dados.novaSenha() == null) {
			throw InvalidRequestException.campoInvalido("novaSenha", "A nova senha é obrigatória.");
		}
		Usuario usuario = usuarios.buscarAtivoPorPublicId(quem.id()).orElseThrow(InvalidTokenException::new);
		List<Violacao> violacoes = (usuario.getPapel() == Papel.ADMIN) ? PoliticaDeSenha.verificarDoAdmin(dados.novaSenha())
				: PoliticaDeSenha.verificarDoAluno(dados.novaSenha());
		if (!violacoes.isEmpty()) {
			throw InvalidRequestException.campoInvalido("novaSenha", mensagem(violacoes.get(0), usuario.getPapel()));
		}

		// Sempre exatamente UMA comparação de BCrypt, como no signin.
		boolean cabeNoBcrypt = dados.senhaAtual().getBytes(StandardCharsets.UTF_8).length <= PoliticaDeSenha.MAXIMO_EM_BYTES;
		boolean confere = encoder.matches(cabeNoBcrypt ? dados.senhaAtual() : SENHA_DE_COMPARACAO,
				cabeNoBcrypt ? usuario.getSenhaHash() : hashFicticio);
		if (!(cabeNoBcrypt && confere)) {
			throw new InvalidRequestException(CodigoErro.SENHA_ATUAL_INCORRETA, MENSAGEM_SENHA_ATUAL_INCORRETA);
		}
		if (dados.novaSenha().equals(dados.senhaAtual())) {
			throw InvalidRequestException.campoInvalido("novaSenha", "A nova senha deve ser diferente da atual.");
		}

		String novoHash = encoder.encode(dados.novaSenha());
		transacao.executeWithoutResult(status -> {
			usuarios.trocarSenhaHash(usuario.getId(), novoHash);
			sessoes.encerrarTodasAsSessoes(usuario.getId(), MotivoRevogacao.TROCA_SENHA);
		});
		limite.zerar(Escopo.TROCA_SENHA_USUARIO, chave);
	}

	private static String mensagem(Violacao violacao, Papel papel) {
		int minimo = (papel == Papel.ADMIN) ? PoliticaDeSenha.MINIMO_DO_ADMIN_EM_BYTES
				: PoliticaDeSenha.MINIMO_DO_ALUNO_EM_BYTES;
		return switch (violacao) {
			case MUITO_CURTA, MUITO_LONGA ->
				"A nova senha deve ter de " + minimo + " a " + PoliticaDeSenha.MAXIMO_EM_BYTES + " bytes.";
			case ESPACO_NAS_PONTAS -> "A nova senha não pode começar nem terminar com espaço, tabulação ou quebra de linha.";
			case QUEBRA_DE_LINHA -> "A nova senha não pode ter quebra de linha.";
			case MARCADOR_DE_EXEMPLO -> "A nova senha não pode ser um texto de exemplo.";
		};
	}

}
