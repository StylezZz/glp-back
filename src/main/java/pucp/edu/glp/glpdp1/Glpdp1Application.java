package pucp.edu.glp.glpdp1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import pucp.edu.glp.glpdp1.algorithm.aco.*;
import pucp.edu.glp.glpdp1.algorithm.util.GeneradorDatasetPrueba;
import pucp.edu.glp.glpdp1.models.*;

import java.time.LocalDateTime;
import java.util.*;

@SpringBootApplication
public class Glpdp1Application {

	public static void main(String[] args) {
		SpringApplication.run(Glpdp1Application.class, args);
	}

	@Component
	public class ACORunner implements CommandLineRunner {
		@Override
		public void run(String... args) {
			System.out.println("Iniciando aplicación de optimización de rutas con ACO híbrido");
			System.out.println("Versión con implementación de requisitos RF85-RF100");
			System.out.println("========================================================");

			// Generar datos de prueba usando el dataset específico
			GeneradorDatasetPrueba generadorPrueba = new GeneradorDatasetPrueba();
			generadorPrueba.generarDatasetCompleto();

			// Aquí puedes incluir el código de Main.java para ejecutar el algoritmo
			// También puedes crear un método separado o una clase de servicio para
			// organizar mejor la lógica de ejecución
			ejecutarAlgoritmoACO();
		}

		private void ejecutarAlgoritmoACO() {
			// Generar datos de prueba
			GeneradorDatos generador = new GeneradorDatos();

			System.out.println("Generando mapa...");
			Mapa mapa = generador.generarMapa();

			System.out.println("Generando flota de camiones...");
			Flota flota = generador.generarFlota(10);

			System.out.println("Generando pedidos...");
			List<Pedido> pedidos = generador.generarPedidos(20);

			// Resto del código de ejecución...
			// (Puedes copiar el resto del contenido del método main de Main.java)

			// Configurar y ejecutar algoritmo
			AlgoritmoACO algoritmo = new AlgoritmoACO();
			// Configurar algoritmo...
			Solucion mejorSolucion = algoritmo.ejecutar();

			// Mostrar resultados...
			System.out.println("\nOptimización completada exitosamente.");
		}
	}
}