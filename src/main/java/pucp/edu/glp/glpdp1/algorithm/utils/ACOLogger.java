package pucp.edu.glp.glpdp1.algorithm.utils;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import pucp.edu.glp.glpdp1.algorithm.aco.ACOSolution;
import pucp.edu.glp.glpdp1.algorithm.model.CamionAsignacion;
import pucp.edu.glp.glpdp1.domain.Camion;
import pucp.edu.glp.glpdp1.domain.Pedido;
import pucp.edu.glp.glpdp1.domain.enums.EstadoCamion;

/**
 * Sistema de logging y análisis para el algoritmo ACO.
 * Permite registrar detalles de ejecución y generar informes
 * para diagnosticar problemas y optimizar el rendimiento.
 */
public class ACOLogger {
    private static final Logger logger = Logger.getLogger(ACOLogger.class.getName());
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final String logFolder;
    private final String sessionId;
    private final List<String> iterationStats = new ArrayList<>();

    // Métricas globales
    private int mejorIteracion = 0;
    private double mejorCalidad = Double.NEGATIVE_INFINITY;
    private int maxPedidosAsignados = 0;

    /**
     * Constructor - crea una nueva sesión de logging
     */
    public ACOLogger() {
        this.sessionId = "aco_" + LocalDateTime.now().format(formatter);
        this.logFolder = "logs/aco";

        // Crear directorio de logs si no existe
        try {
            Path path = Paths.get(logFolder);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
        } catch (IOException e) {
            logger.severe("Error creando directorio de logs: " + e.getMessage());
        }

        // Mensaje inicial
        System.out.println("\n===================================");
        System.out.println("🚀 INICIANDO SISTEMA DE MONITOREO ACO");
        System.out.println("ID Sesión: " + sessionId);
        System.out.println("===================================\n");
    }

    /**
     * Registra estadísticas de una iteración
     */
    public void logIteracion(int iteracion, ACOSolution solucion, List<Camion> camionesDisponibles,
                             int totalPedidos, long tiempoEjecucionMs) {

        if (solucion == null) {
            System.out.println("[ITER " + iteracion + "] ⚠️ ERROR: Solución nula");
            return;
        }
        double tiempoSegundos = tiempoEjecucionMs / 1000.0;



        // Extraer métricas principales
        int pedidosAsignados = solucion.getNumeroPedidosAsignados();
        int pedidosNoAsignados = solucion.getPedidosNoAsignados().size();
        double calidad = solucion.getCalidad();
        double distanciaTotal = solucion.getDistanciaTotal();
        double consumoTotal = solucion.getConsumoTotal();
        int camionesUsados = solucion.getNumeroCamionesUtilizados();
        double ratioPedidosAsignados = (double) pedidosAsignados / totalPedidos;

        // Actualizar máximos globales
        if (calidad > mejorCalidad) {
            mejorCalidad = calidad;
            mejorIteracion = iteracion;
        }

        if (pedidosAsignados > maxPedidosAsignados) {
            maxPedidosAsignados = pedidosAsignados;
        }

        // Registrar estadísticas para archivo CSV
        String estadoIteracion = String.format(
                "Iteración %d | Tiempo: %.2f seg | Camiones: %d/%d | Pedidos: %d/%d",
                iteracion, tiempoSegundos, solucion.getNumeroCamionesUtilizados(), camionesDisponibles.size(),
                solucion.getNumeroPedidosAsignados(), totalPedidos
        );
        iterationStats.add(estadoIteracion);

        // Imprimir resumen de la iteración
        System.out.println("\n----- ITERACIÓN " + iteracion + " -----");
        System.out.println("🚚 Camiones: " + camionesUsados + "/" + camionesDisponibles.size());
        System.out.println("📦 Pedidos: " + pedidosAsignados + "/" + totalPedidos +
                " (" + String.format("%.2f%%", ratioPedidosAsignados * 100) + ")");
        System.out.println("🥇 Calidad: " + String.format("%.6f", calidad) +
                (calidad >= mejorCalidad ? " (MEJOR)" : ""));
        System.out.println("📏 Distancia: " + String.format("%.2f", distanciaTotal) + " km | " +
                "Consumo: " + String.format("%.2f", consumoTotal) + " gal");
        System.out.println("⏱️ Tiempo: " + String.format("%.2f", tiempoEjecucionMs / 1000.0) + " seg");
    }

    /**
     * Registra detalle de la flota actual
     */
    public void logFlota(List<Camion> flota) {
        int disponibles = 0, enRuta = 0, mantenimiento = 0, averiados = 0;

        for (Camion camion : flota) {
            switch (camion.getEstado()) {
                case DISPONIBLE: disponibles++; break;
                case EN_RUTA: enRuta++; break;
                case MANTENIMIENTO: mantenimiento++; break;
                case AVERIADO: averiados++; break;
            }
        }

        System.out.println("\n----- ESTADO DE FLOTA -----");
        System.out.println("🚚 Total camiones: " + flota.size());
        System.out.println("✅ Disponibles: " + disponibles);
        System.out.println("🛣️ En ruta: " + enRuta);
        System.out.println("🔧 En mantenimiento: " + mantenimiento);
        System.out.println("🚫 Averiados: " + averiados);

        // Detallar camiones por tipo
        System.out.println("\nDETALLE POR TIPO:");
        contarCamionesPorTipo(flota);
    }

    /**
     * Cuenta y muestra camiones por tipo
     */
    private void contarCamionesPorTipo(List<Camion> flota) {
        int[] conteo = new int[4]; // TA, TB, TC, TD
        int[] disponibles = new int[4];

        for (Camion camion : flota) {
            String tipo = camion.getIdC().substring(0, 2);
            int idx;

            switch (tipo) {
                case "TA": idx = 0; break;
                case "TB": idx = 1; break;
                case "TC": idx = 2; break;
                case "TD": idx = 3; break;
                default: continue;
            }

            conteo[idx]++;
            if (camion.getEstado() == EstadoCamion.DISPONIBLE) {
                disponibles[idx]++;
            }
        }

        String[] tipos = {"TA", "TB", "TC", "TD"};
        double[] capacidades = {25.0, 15.0, 10.0, 5.0};

        for (int i = 0; i < 4; i++) {
            System.out.println(tipos[i] + " (" + capacidades[i] + "m³): " +
                    disponibles[i] + "/" + conteo[i] + " disponibles");
        }
    }

    /**
     * Genera un informe detallado de asignaciones
     */
    public void logAsignacionDetallada(ACOSolution solucion) {
        if (solucion == null || solucion.getAsignaciones().isEmpty()) {
            System.out.println("\n❌ No hay asignaciones para mostrar");
            return;
        }

        System.out.println("\n----- DETALLE DE ASIGNACIONES -----");

        // Mostrar asignaciones
        for (CamionAsignacion asignacion : solucion.getAsignaciones()) {
            Camion camion = asignacion.getCamion();
            List<Pedido> pedidos = asignacion.getPedidos();

            // Calcular utilización
            double volumenTotal = 0;
            for (Pedido p : pedidos) {
                volumenTotal += p.getVolumen();
            }
            double utilizacion = (volumenTotal / camion.getCargaM3()) * 100;

            System.out.println("\n🚚 [" + camion.getIdC() + "] Cap: " + camion.getCargaM3() +
                    "m³ | Asignado: " + pedidos.size() + " pedidos | " +
                    "Utilización: " + String.format("%.2f%%", utilizacion));

            for (Pedido p : pedidos) {
                System.out.println("   📦 #" + p.getIdPedido() +
                        " - Vol: " + p.getVolumen() + "m³" +
                        " - Dest: (" + p.getDestino().getX() + "," + p.getDestino().getY() + ")");
            }

            System.out.println("   📊 Dist: " + String.format("%.2f", asignacion.getDistanciaTotal()) +
                    " km | Consumo: " + String.format("%.2f", asignacion.getConsumoTotal()) + " gal");
        }

        // Mostrar pedidos no asignados
        if (!solucion.getPedidosNoAsignados().isEmpty()) {
            System.out.println("\n❌ PEDIDOS NO ASIGNADOS: " + solucion.getPedidosNoAsignados().size());
            // Mostrar los primeros 5 como ejemplo
            int count = 0;
            for (Pedido p : solucion.getPedidosNoAsignados()) {
                if (count++ < 5) {
                    System.out.println("   • #" + p.getIdPedido() +
                            " - Vol: " + p.getVolumen() + "m³" +
                            " - Dest: (" + p.getDestino().getX() + "," + p.getDestino().getY() + ")");
                }
            }
            if (count < solucion.getPedidosNoAsignados().size()) {
                System.out.println("   • ... y " + (solucion.getPedidosNoAsignados().size() - count) + " más");
            }
        }
    }

    /**
     * Guarda estadísticas de ejecución en archivos
     */
    public void guardarEstadisticas(ACOSolution mejorSolucion, int totalPedidos, long tiempoTotal) {
        // Archivo de estadísticas
        String statsFile = logFolder + "/" + sessionId + "_stats.csv";

        try (PrintWriter writer = new PrintWriter(new FileWriter(statsFile))) {
            // Encabezado
            writer.println("iteracion,pedidos_asignados,pedidos_no_asignados,calidad," +
                    "distancia_total,consumo_total,camiones_usados,camiones_disponibles,tiempo_ms");

            // Datos de cada iteración
            for (String stat : iterationStats) {
                writer.println(stat);
            }

            System.out.println("\n✅ Estadísticas guardadas en: " + statsFile);
        } catch (IOException e) {
            logger.severe("Error guardando estadísticas: " + e.getMessage());
        }

        // Archivo de resultados detallados
        String resultFile = logFolder + "/" + sessionId + "_resultado.txt";

        try (PrintWriter writer = new PrintWriter(new FileWriter(resultFile))) {
            writer.println("========================================");
            writer.println("RESULTADO FINAL - ALGORITMO ACO");
            writer.println("Sesión: " + sessionId);
            writer.println("Fecha: " + LocalDateTime.now());
            writer.println("========================================\n");

            writer.println("RESUMEN:");
            writer.println("- Total pedidos: " + totalPedidos);

            if (mejorSolucion != null) {
                int pedidosAsignados = mejorSolucion.getNumeroPedidosAsignados();
                double ratio = (double)pedidosAsignados / totalPedidos * 100;

                writer.println("- Pedidos asignados: " + pedidosAsignados +
                        " (" + String.format("%.2f%%", ratio) + ")");
                writer.println("- Mejor iteración: " + mejorIteracion);
                writer.println("- Mejor calidad: " + String.format("%.6f", mejorCalidad));
                writer.println("- Tiempo total: " + String.format("%.2f", tiempoTotal / 1000.0) + " segundos\n");

                // Escribir detalles de las asignaciones
                writer.println("DETALLE DE ASIGNACIONES:");
                for (CamionAsignacion asignacion : mejorSolucion.getAsignaciones()) {
                    Camion camion = asignacion.getCamion();
                    writer.println("\nCamión: " + camion.getIdC() + " (Cap: " + camion.getCargaM3() + "m³)");
                    writer.println("Pedidos asignados: " + asignacion.getPedidos().size());

                    for (Pedido p : asignacion.getPedidos()) {
                        writer.println("  - #" + p.getIdPedido() +
                                " | Vol: " + p.getVolumen() + "m³" +
                                " | Dest: (" + p.getDestino().getX() + "," + p.getDestino().getY() + ")");
                    }

                    writer.println("  - Distancia: " + String.format("%.2f", asignacion.getDistanciaTotal()) + " km");
                    writer.println("  - Consumo: " + String.format("%.2f", asignacion.getConsumoTotal()) + " gal");
                }

                // Estadísticas de pedidos no asignados
                writer.println("\nPEDIDOS NO ASIGNADOS: " + mejorSolucion.getPedidosNoAsignados().size());

                // Agrupar pedidos no asignados por volumen para análisis
                int pequeños = 0, medianos = 0, grandes = 0;
                double volumenTotal = 0;

                for (Pedido p : mejorSolucion.getPedidosNoAsignados()) {
                    volumenTotal += p.getVolumen();
                    if (p.getVolumen() <= 5) pequeños++;
                    else if (p.getVolumen() <= 15) medianos++;
                    else grandes++;
                }

                writer.println("- Volumen total no asignado: " + String.format("%.2f", volumenTotal) + "m³");
                writer.println("- Distribución por tamaño:");
                writer.println("  • Pequeños (≤5m³): " + pequeños);
                writer.println("  • Medianos (5-15m³): " + medianos);
                writer.println("  • Grandes (>15m³): " + grandes);
            } else {
                writer.println("No se encontró una solución válida.");
            }

            System.out.println("✅ Resultado detallado guardado en: " + resultFile);
        } catch (IOException e) {
            logger.severe("Error guardando resultado: " + e.getMessage());
        }
    }

    /**
     * Genera un diagnóstico de problemas potenciales
     */
    public void generarDiagnostico(ACOSolution solucion, int totalPedidos) {
        if (solucion == null) {
            System.out.println("\n⚠️ DIAGNÓSTICO: No se generó una solución válida");
            return;
        }

        System.out.println("\n===================================");
        System.out.println("🔍 DIAGNÓSTICO DE RENDIMIENTO ACO");
        System.out.println("===================================");

        int pedidosAsignados = solucion.getNumeroPedidosAsignados();
        double ratio = (double)pedidosAsignados / totalPedidos;

        System.out.println("📊 Asignación: " + pedidosAsignados + "/" + totalPedidos +
                " (" + String.format("%.2f%%", ratio * 100) + ")");

        // Análisis de problema según el ratio de asignación
        if (ratio < 0.1) {
            System.out.println("\n❌ PROBLEMA CRÍTICO: Asignación extremadamente baja");
            System.out.println("Posibles causas:");
            System.out.println("1️⃣ Capacidad de flota insuficiente para la demanda total");
            System.out.println("2️⃣ Restricciones de tiempo demasiado estrictas");
            System.out.println("3️⃣ Parámetros ACO mal ajustados (exploración vs. explotación)");
            System.out.println("4️⃣ Problemas de conectividad por bloqueos extensos");

            // Calcular déficit de capacidad
            double volumenNoAsignado = solucion.getPedidosNoAsignados().stream()
                    .mapToDouble(Pedido::getVolumen)
                    .sum();

            System.out.println("\nAnálisis de capacidad:");
            System.out.println("• Volumen no asignado: " + String.format("%.2f", volumenNoAsignado) + "m³");

            // Recomendar ajustes específicos
            System.out.println("\n🔧 Recomendaciones:");
            System.out.println("1. Aumentar número de hormigas (parámetro numeroHormigas)");
            System.out.println("2. Reducir factor de evaporación (factorEvaporacion)");
            System.out.println("3. Revisar función de agrupamiento de pedidos");
            System.out.println("4. Verificar capacidad total de flota vs. demanda");

        } else if (ratio < 0.5) {
            System.out.println("\n⚠️ PROBLEMA SIGNIFICATIVO: Baja tasa de asignación");
            System.out.println("🔧 Recomendaciones:");
            System.out.println("1. Mejorar agrupamiento de pedidos por proximidad");
            System.out.println("2. Aumentar umbral de distancia para pedidos cercanos");
            System.out.println("3. Refinar parámetros α y β del algoritmo");

        } else if (ratio < 0.9) {
            System.out.println("\n⚠️ OPORTUNIDAD DE MEJORA: Asignación moderada");
            System.out.println("🔧 Recomendaciones:");
            System.out.println("1. Mejorar búsqueda local");
            System.out.println("2. Optimizar balance entre intensificación y diversificación");

        } else {
            System.out.println("\n✅ RENDIMIENTO BUENO: Alta tasa de asignación");
            System.out.println("🔧 Oportunidades de optimización:");
            System.out.println("1. Refinar rutas para reducir distancia total");
            System.out.println("2. Optimizar utilización de camiones");
        }
    }
}