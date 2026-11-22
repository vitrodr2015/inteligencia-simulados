package br.simulado.etec.usuarios.service;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import br.simulado.etec.shared.config.AdminInicialAmbiente;
import br.simulado.etec.shared.config.Perfis;

/**
 * Dispara o bootstrap do administrador (D45, D126) no fim da criação dos beans: o Flyway já migrou e o servidor web
 * ainda NÃO aceita conexões, então uma falha em produção derruba a subida antes de qualquer requisição. Lê as variáveis
 * pelo {@link Environment} (nunca do yml) e decide o modo pelo perfil ({@link Perfis#emProducao}).
 */
@Component
public class AdminInicialInicializador implements SmartInitializingSingleton {

	private final AdminInicialService servico;

	private final Environment ambiente;

	public AdminInicialInicializador(AdminInicialService servico, Environment ambiente) {
		this.servico = servico;
		this.ambiente = ambiente;
	}

	@Override
	public void afterSingletonsInstantiated() {
		servico.garantirAdministradorInicial(AdminInicialAmbiente.ler(ambiente), Perfis.emProducao(ambiente));
	}

}
