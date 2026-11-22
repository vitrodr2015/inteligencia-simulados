package br.simulado.etec.usuarios.service;

import java.time.LocalDate;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.simulado.etec.shared.config.AdminInicialAmbiente;
import br.simulado.etec.shared.config.AdminInicialAmbiente.Ausente;
import br.simulado.etec.shared.config.AdminInicialAmbiente.Invalida;
import br.simulado.etec.shared.config.AdminInicialAmbiente.Leitura;
import br.simulado.etec.shared.config.AdminInicialAmbiente.Valida;
import br.simulado.etec.shared.config.TermosProperties;
import br.simulado.etec.shared.persistencia.Restricoes;
import br.simulado.etec.usuarios.model.Papel;
import br.simulado.etec.usuarios.model.Usuario;
import br.simulado.etec.usuarios.repository.UsuarioRepository;

/**
 * O primeiro administrador (contrato, seção 3.2; D45, D81, D91, D125, D126). Regras:
 *
 * <ul>
 * <li>só cria se NÃO existir ADMIN ativo (com {@code excluido_em} nulo); se já existe, as variáveis são IGNORADAS,
 * inclusive para trocar a senha;</li>
 * <li>NUNCA promove um usuário que já existe: se houver qualquer usuário com o e-mail, é erro;</li>
 * <li>em PRODUÇÃO, qualquer situação sem admin utilizável (variáveis ausentes, inválidas ou e-mail de usuário comum)
 * derruba a subida; fora de produção só se registra o aviso ou o erro e nenhum admin é criado;</li>
 * <li>é idempotente e seguro contra duas subidas ao mesmo tempo: o índice único do e-mail deixa só um nascer. LIMITAÇÃO
 * CONHECIDA: duas subidas simultâneas com {@code ADMIN_EMAIL} DIFERENTES poderiam criar dois administradores (revisitar
 * no Módulo 17).</li>
 * </ul>
 *
 * <p>Nada de senha, hash nem e-mail vai para o log ou para a mensagem de erro: as mensagens são fixas.
 */
@Service
public class AdminInicialService {

	private static final Logger LOG = LoggerFactory.getLogger(AdminInicialService.class);

	static final String NOME_DO_ADMIN = "Administrador";

	static final LocalDate NASCIMENTO_DO_ADMIN = LocalDate.of(2000, 1, 1);

	static final String MENSAGEM_SEM_ADMIN = "Nenhum administrador existe e ADMIN_EMAIL e ADMIN_SENHA não foram definidas";

	static final String MENSAGEM_EMAIL_DE_USUARIO = "Já existe um usuário com o e-mail de ADMIN_EMAIL. A aplicação nunca "
			+ "promove um usuário existente a administrador: use outro e-mail em ADMIN_EMAIL";

	/** O que o bootstrap fez (útil para os testes; a aplicação só se importa se ele lançou exceção). */
	public enum Resultado {

		CRIADO, CRIADO_POR_OUTRA_INSTANCIA, JA_EXISTIA_ADMIN, SEM_ADMIN_E_SEM_VARIAVEIS, VARIAVEIS_INVALIDAS,
		EMAIL_DE_USUARIO_EXISTENTE

	}

	private final UsuarioRepository repositorio;

	private final PasswordEncoder encoder;

	private final TermosProperties termos;

	private final TransactionTemplate transacao;

	public AdminInicialService(UsuarioRepository repositorio, PasswordEncoder encoder, TermosProperties termos,
			PlatformTransactionManager gerenteDeTransacao) {
		this.repositorio = repositorio;
		this.encoder = encoder;
		this.termos = termos;
		this.transacao = new TransactionTemplate(gerenteDeTransacao);
	}

	/**
	 * @param leitura o que o ambiente tem ({@link AdminInicialAmbiente#ler})
	 * @param producao {@code true} no perfil de produção, onde a falta de um admin utilizável derruba a subida
	 * @throws IllegalStateException só em produção, com mensagem fixa (sem nenhum valor das variáveis)
	 */
	public Resultado garantirAdministradorInicial(Leitura leitura, boolean producao) {
		if (repositorio.existsByPapelAndExcluidoEmIsNull(Papel.ADMIN)) {
			if (!(leitura instanceof Ausente)) {
				LOG.info("Já existe administrador: ADMIN_EMAIL e ADMIN_SENHA foram ignoradas (nada é criado nem alterado).");
			}
			return Resultado.JA_EXISTIA_ADMIN;
		}
		return switch (leitura) {
			case Ausente ausente -> semVariaveis(producao);
			case Invalida invalida -> invalidas(invalida, producao);
			case Valida valida -> criar(valida, producao);
		};
	}

	private Resultado semVariaveis(boolean producao) {
		if (producao) {
			throw new IllegalStateException(MENSAGEM_SEM_ADMIN + ". Em produção a aplicação não sobe sem um administrador (D45).");
		}
		LOG.warn("{}: ninguém consegue entrar como administrador (só um aviso fora de produção).", MENSAGEM_SEM_ADMIN);
		return Resultado.SEM_ADMIN_E_SEM_VARIAVEIS;
	}

	private Resultado invalidas(Invalida invalida, boolean producao) {
		if (producao) {
			throw new IllegalStateException("Administrador inicial inválido: " + invalida.resumo());
		}
		LOG.error("Administrador inicial NÃO criado, as variáveis são inválidas: {}", invalida.resumo());
		return Resultado.VARIAVEIS_INVALIDAS;
	}

	private Resultado criar(Valida valida, boolean producao) {
		if (repositorio.findByEmail(valida.email()).isPresent()) {
			return emailJaUsado(producao);
		}
		Usuario admin = new Usuario(NOME_DO_ADMIN, valida.email(), encoder.encode(valida.senha()), NASCIMENTO_DO_ADMIN,
				termos.versao(), Papel.ADMIN);
		try {
			transacao.execute(status -> repositorio.saveAndFlush(admin));
		}
		catch (DataIntegrityViolationException ex) {
			if (!Restricoes.ehUnicidade(ex)) {
				throw ex;
			}
			// Duas subidas ao mesmo tempo: o índice único do e-mail deixou só uma nascer.
			Optional<Usuario> existente = repositorio.findByEmail(valida.email());
			if (existente.isPresent() && existente.get().getPapel() == Papel.ADMIN) {
				LOG.info("O administrador inicial já foi criado por outra instância.");
				return Resultado.CRIADO_POR_OUTRA_INSTANCIA;
			}
			return emailJaUsado(producao);
		}
		LOG.info("Administrador inicial criado.");
		return Resultado.CRIADO;
	}

	private Resultado emailJaUsado(boolean producao) {
		if (producao) {
			throw new IllegalStateException(MENSAGEM_EMAIL_DE_USUARIO + " (D126).");
		}
		LOG.error("Administrador inicial NÃO criado: {}.", MENSAGEM_EMAIL_DE_USUARIO);
		return Resultado.EMAIL_DE_USUARIO_EXISTENTE;
	}

}
