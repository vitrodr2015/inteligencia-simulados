package br.simulado.etec.assinatura.service;

import java.util.Map;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.simulado.etec.assinatura.model.Programa;
import br.simulado.etec.assinatura.model.Recurso;
import br.simulado.etec.shared.security.UsuarioAutenticado;

/**
 * Um endpoint "premium" FALSO, só para provar o caminho completo (chamador, serviço, política, 403 em {@code ErrorResponse}).
 * Existe SÓ no escopo de teste ({@code src/test}) e só entra nos contextos que o importam por {@code @Import}: o
 * {@code @TestComponent} o tira da varredura de componentes, então nenhum outro teste (nem a varredura de rotas) o enxerga.
 * Nunca existe no código de produção.
 *
 * <p>Ele é o "chamador de exemplo" do registro {@link RotasPremium}: quem esquecer a consulta aqui faz o teste das rotas
 * premium falhar.
 */
@TestComponent
@RestController
@RequestMapping("/api/v1/teste/premium")
public class ControllerPremiumDeTeste {

	private final EntitlementsService entitlements;

	public ControllerPremiumDeTeste(EntitlementsService entitlements) {
		this.entitlements = entitlements;
	}

	@PostMapping("/consultando")
	public ResponseEntity<Map<String, String>> consultando(@AuthenticationPrincipal UsuarioAutenticado quem) {
		entitlements.exigir(quem, Recurso.INICIAR_TENTATIVA, Programa.ETEC);
		return ResponseEntity.ok(Map.of("resultado", "liberado"));
	}

}
