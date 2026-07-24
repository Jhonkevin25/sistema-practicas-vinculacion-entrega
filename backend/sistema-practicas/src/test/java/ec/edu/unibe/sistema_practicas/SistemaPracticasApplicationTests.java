package ec.edu.unibe.sistema_practicas;

import ec.edu.unibe.sistema_practicas.auditoria.Auditoria;
import ec.edu.unibe.sistema_practicas.auditoria.AuditoriaEmitter;
import ec.edu.unibe.sistema_practicas.auditoria.AuditoriaRepository;
import ec.edu.unibe.sistema_practicas.cierre.CierreExpedienteComponent;
import ec.edu.unibe.sistema_practicas.documento.DocEstudiante;
import ec.edu.unibe.sistema_practicas.documento.DocEstudianteRepository;
import ec.edu.unibe.sistema_practicas.documento.DocumentoRequerido;
import ec.edu.unibe.sistema_practicas.documento.DocumentoRequeridoRepository;
import ec.edu.unibe.sistema_practicas.estudiante.Estudiante;
import ec.edu.unibe.sistema_practicas.estudiante.EstudianteRepository;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Mismas anotaciones que SecurityMatrixTests para que Spring reutilice UN solo
// contexto de test: el pooler de Supabase admite max 15 conexiones y cada
// contexto adicional abre su propio pool
@SpringBootTest(properties = {
		"spring.datasource.hikari.maximum-pool-size=2",
		"spring.datasource.hikari.minimum-idle=1"
})
@AutoConfigureMockMvc
class SistemaPracticasApplicationTests {

	@Autowired private AuditoriaEmitter auditoriaEmitter;
	@Autowired private AuditoriaRepository auditoriaRepository;
	@Autowired private CierreExpedienteComponent cierreExpedienteComponent;
	@Autowired private DocumentoRequeridoRepository requeridoRepository;
	@Autowired private DocEstudianteRepository documentoRepository;
	@Autowired private EstudianteRepository estudianteRepository;

	@Test
	void contextLoads() {
	}

	@Test
	@Transactional
	void auditoria_compartida_persiste_en_el_contexto_real() {
		Auditoria auditoria = auditoriaEmitter.registrar(
				"FASE22_PRUEBA", "VERIFICACION_INTEGRADA", null,
				Map.of("estado", "antes"), Map.of("estado", "despues"));

		assertNotNull(auditoria.getId());
		Auditoria persistida = auditoriaRepository.findById(auditoria.getId()).orElseThrow();
		assertEquals("FASE22_PRUEBA", persistida.getTablaAfectada());
		assertEquals("VERIFICACION_INTEGRADA", persistida.getAccion());
	}

	@Test
	@Transactional
	void flujo_documento_obligatorio_habilita_validacion_de_cierre() {
		Estudiante estudiante = estudianteRepository.findAll().stream().findFirst().orElse(null);
		Assumptions.assumeTrue(estudiante != null, "No hay estudiantes para probar el flujo documental");
		satisfacerRequisitosExistentes(estudiante);

		String tipo = "fase22_cierre_" + System.nanoTime();
		DocumentoRequerido requerido = new DocumentoRequerido();
		requerido.setProceso("PRACTICAS");
		requerido.setCarrera(estudiante.getCarrera());
		requerido.setTipoDocumento(tipo);
		requerido.setNombre("Documento integrado Fase 22");
		requerido.setMomento("CIERRE");
		requerido.setObligatorio(true);
		requerido.setActivo(true);
		requerido.setCreatedAt(LocalDateTime.now());
		requerido.setUpdatedAt(LocalDateTime.now());
		requerido = requeridoRepository.save(requerido);

		assertThrows(IllegalArgumentException.class,
				() -> cierreExpedienteComponent.validarDocumentosCierre(estudiante, "PRACTICAS", null));

		DocEstudiante documento = new DocEstudiante();
		documento.setEstudiante(estudiante);
		documento.setRequerido(requerido);
		documento.setTipoDocumento(tipo);
		documento.setProceso("PRACTICAS");
		documento.setCarrera(estudiante.getCarrera());
		documento.setEstado("aprobado");
		documento.setUrlArchivo("fase22/prueba.pdf");
		documento.setFechaSubida(LocalDateTime.now());
		documentoRepository.save(documento);

		assertDoesNotThrow(
				() -> cierreExpedienteComponent.validarDocumentosCierre(estudiante, "PRACTICAS", null));
	}

	private void satisfacerRequisitosExistentes(Estudiante estudiante) {
		requeridoRepository.findByActivoTrue().stream()
				.filter(req -> Boolean.TRUE.equals(req.getObligatorio()))
				.filter(req -> "CIERRE".equalsIgnoreCase(valor(req.getMomento())))
				.filter(req -> "GENERAL".equalsIgnoreCase(valor(req.getProceso()))
						|| "PRACTICAS".equalsIgnoreCase(valor(req.getProceso())))
				.filter(req -> valor(req.getCarrera()).isBlank()
						|| valor(req.getCarrera()).equals(estudiante.getCarrera()))
				.filter(req -> valor(req.getEtapa()).isBlank())
				.forEach(req -> {
					String proceso = "GENERAL".equalsIgnoreCase(valor(req.getProceso())) ? "GENERAL" : "PRACTICAS";
					DocEstudiante doc = documentoRepository
							.findByEstudianteIdAndTipoDocumentoAndProceso(
									estudiante.getId(), req.getTipoDocumento(), proceso)
							.orElseGet(DocEstudiante::new);
					doc.setEstudiante(estudiante);
					doc.setRequerido(req);
					doc.setTipoDocumento(req.getTipoDocumento());
					doc.setProceso(proceso);
					doc.setCarrera(estudiante.getCarrera());
					doc.setEstado("aprobado");
					doc.setUrlArchivo("fase22/requisito-aprobado.pdf");
					doc.setFechaSubida(LocalDateTime.now());
					documentoRepository.save(doc);
				});
	}

	private String valor(String valor) {
		return valor == null ? "" : valor.trim();
	}

}
