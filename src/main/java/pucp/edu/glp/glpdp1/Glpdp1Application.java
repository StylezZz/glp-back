package pucp.edu.glp.glpdp1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;

import pucp.edu.glp.glpdp1.domain.Mapa;
import pucp.edu.glp.glpdp1.service.PedidoService;
import pucp.edu.glp.glpdp1.service.AveriaService;
import pucp.edu.glp.glpdp1.service.MapaService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication(exclude = {
		DataSourceAutoConfiguration.class,
		HibernateJpaAutoConfiguration.class,
		SecurityAutoConfiguration.class  // Añade esta línea
})
public class Glpdp1Application {

	public static void main(String[] args) {
		SpringApplication.run(Glpdp1Application.class, args);
	}

	// Registrar servicios como beans de Spring
	@Bean
	public PedidoService pedidoService() {
		return new PedidoService();
	}
	@Bean
	public AveriaService averiaService() {
		return new AveriaService();
	}

	@Bean
	public MapaService mapaService(PedidoService pedidoService, AveriaService averiaService) {
		return new MapaService(pedidoService, averiaService);
	}

	// Este bean es solo para pruebas, en una aplicación real
	// posiblemente sería gestionado por un repositorio
	@Bean
	public Mapa mapa() {
		return new Mapa(100, 100);
	}

	// Controlador REST integrado para simplificar
	@RestController
	@RequestMapping("/api")
	public static class PedidoController {

		private final MapaService mapaService;
		private final Mapa mapa;

		public PedidoController(MapaService mapaService, Mapa mapa) {
			this.mapaService = mapaService;
			this.mapa = mapa;
		}

		@PostMapping("/cargar-pedidos-archivo")
		public ResponseEntity<String> cargarPedidosDesdeArchivo(@RequestParam("ruta") String rutaArchivo) {
			try {
				mapaService.cargarPedidosEnMapa(mapa, rutaArchivo);
				return ResponseEntity.ok("Pedidos cargados exitosamente. Total: " + mapa.getPedidos().size());
			} catch (Exception e) {
				return ResponseEntity.badRequest().body("Error al cargar pedidos: " + e.getMessage());
			}
		}

		@PostMapping("/cargar-pedidos")
		public ResponseEntity<String> cargarPedidos(@RequestParam("archivo") MultipartFile archivo) {
			try {
				// Guardar el archivo temporalmente
				Path tempPath = Files.createTempFile("pedidos-", ".txt");
				archivo.transferTo(tempPath.toFile());

				// Cargar pedidos desde el archivo
				mapaService.cargarPedidosEnMapa(mapa, tempPath.toString());

				// Eliminar el archivo temporal
				Files.delete(tempPath);

				return ResponseEntity.ok("Pedidos cargados exitosamente. Total: " + mapa.getPedidos().size());
			} catch (IOException e) {
				return ResponseEntity.badRequest().body("Error al cargar pedidos: " + e.getMessage());
			}
		}

		@GetMapping("/pedidos")
		public ResponseEntity<?> obtenerPedidos() {
			return ResponseEntity.ok(mapa.getPedidos());
		}
	}
}