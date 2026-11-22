package br.simulado.etec.usuarios.service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.simulado.etec.shared.config.TermosProperties;
import br.simulado.etec.shared.exception.CodigoErro;
import br.simulado.etec.shared.exception.ConflictException;
import br.simulado.etec.shared.exception.InvalidRequestException;
import br.simulado.etec.shared.persistencia.Restricoes;
import br.simulado.etec.usuarios.model.Papel;
import br.simulado.etec.usuarios.model.Usuario;
import br.simulado.etec.usuarios.repository.UsuarioRepository;

/**
 * Regras do usuário (contrato, seção 3). É a porta do domínio para os outros (auth e segurança): ninguém fora de
 * {@code usuarios} usa o repositório.
 */
@Service
@Transactional(readOnly = true)
public class UsuarioService {

	private final UsuarioRepository repositorio;

	private final PasswordEncoder encoder;

	private final TermosProperties termos;

	public UsuarioService(UsuarioRepository repositorio, PasswordEncoder encoder, TermosProperties termos) {
		this.repositorio = repositorio;
		this.encoder = encoder;
		this.termos = termos;
	}

	public TermosProperties termosVigentes() {
		return termos;
	}

	/**
	 * Cria um STUDENT (o corpo nunca escolhe o papel). Os dados já chegam validados no formato (400) e com o e-mail
	 * normalizado; aqui ficam as regras de estado: termos diferentes dos vigentes = 400 (D23) e e-mail já usado =
	 * 409 (D37, D105), inclusive na corrida entre dois cadastros (o índice único barra o segundo).
	 */
	@Transactional
	public Usuario criarAluno(String nome, String emailNormalizado, String senha, LocalDate dataNascimento,
			String aceiteTermosVersao) {
		if (!termos.versao().equals(aceiteTermosVersao)) {
			throw InvalidRequestException.campoInvalido("aceiteTermosVersao",
					"Aceite a versão vigente dos termos de uso.");
		}
		if (repositorio.findByEmail(emailNormalizado).isPresent()) {
			throw emailDuplicado();
		}
		Usuario novo = new Usuario(nome, emailNormalizado, encoder.encode(senha), dataNascimento, aceiteTermosVersao,
				Papel.STUDENT);
		try {
			return repositorio.saveAndFlush(novo);
		}
		catch (DataIntegrityViolationException ex) {
			if (Restricoes.ehUnicidade(ex)) {
				throw emailDuplicado();
			}
			throw ex;
		}
	}

	/** Para o signin: qualquer usuário com esse e-mail (inclusive excluído; quem chama decide). */
	public Optional<Usuario> buscarPorEmail(String emailNormalizado) {
		return repositorio.findByEmail(emailNormalizado);
	}

	/** Usuário que existe e não foi excluído: a fonte da verdade a cada requisição (D26). */
	public Optional<Usuario> buscarAtivoPorPublicId(UUID publicId) {
		return repositorio.findByPublicIdAndExcluidoEmIsNull(publicId);
	}

	private static ConflictException emailDuplicado() {
		return new ConflictException(CodigoErro.EMAIL_DUPLICADO, "Já existe um usuário com esse e-mail.");
	}

}
