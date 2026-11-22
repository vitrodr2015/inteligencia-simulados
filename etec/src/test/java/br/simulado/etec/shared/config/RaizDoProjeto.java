package br.simulado.etec.shared.config;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Acha a pasta do projeto ({@code etec/}) sem depender do diretório de trabalho do teste: sobe a partir do diretório
 * atual até achar o {@code pom.xml} do projeto e, se não achar (IDE rodando de outra pasta), parte do local das classes de
 * teste ({@code etec/target/test-classes}). Os testes que leem arquivos REAIS (yml, migrations, pom, README, contrato)
 * resolvem tudo a partir daqui.
 */
public final class RaizDoProjeto {

	private static final Path RAIZ = localizar();

	private RaizDoProjeto() {
	}

	/** A pasta {@code etec/} (a do {@code pom.xml}). */
	public static Path raiz() {
		return RAIZ;
	}

	/** Um caminho relativo à pasta {@code etec/}. */
	public static Path resolver(String relativo) {
		return RAIZ.resolve(relativo);
	}

	/** A raiz do repositório (a pasta acima de {@code etec/}: README.md, memoria.md, postman/). */
	public static Path repositorio() {
		return RAIZ.getParent();
	}

	private static Path localizar() {
		Path atual = Path.of("").toAbsolutePath();
		for (Path candidato = atual; candidato != null; candidato = candidato.getParent()) {
			if (ehOPomDoProjeto(candidato.resolve("pom.xml"))) {
				return candidato;
			}
			if (ehOPomDoProjeto(candidato.resolve("etec/pom.xml"))) {
				return candidato.resolve("etec");
			}
		}
		try {
			Path classes = Path.of(RaizDoProjeto.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			for (Path candidato = classes; candidato != null; candidato = candidato.getParent()) {
				if (ehOPomDoProjeto(candidato.resolve("pom.xml"))) {
					return candidato;
				}
			}
		}
		catch (URISyntaxException | RuntimeException e) {
			// cai na mensagem abaixo
		}
		throw new IllegalStateException("Não achei a pasta do projeto (pom.xml do etec) a partir de " + atual);
	}

	private static boolean ehOPomDoProjeto(Path pom) {
		try {
			return Files.isRegularFile(pom) && Files.readString(pom).contains("<artifactId>etec</artifactId>")
					&& Files.readString(pom).contains("<groupId>br.simulado</groupId>");
		}
		catch (IOException e) {
			return false;
		}
	}

}
