package pucp.edu.glp.glpdp1.algorithm.utils;

import pucp.edu.glp.glpdp1.algorithm.aco.ACOSolution;
import pucp.edu.glp.glpdp1.algorithm.model.CamionAsignacion;
import pucp.edu.glp.glpdp1.domain.Camion;
import pucp.edu.glp.glpdp1.domain.Pedido;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Monitor de rendimiento para el algoritmo ACO.
 * Proporciona análisis detallado de rendimiento y tendencias.
 */
public class ACOMonitor {

    // Registro de métricas por iteración
    private List<MetricasIteracion> historicoIteraciones = new ArrayList<>();

    // Métricas globales para análisis
    private double mejorCalidad = Double.NEGATIVE_INFINITY;
    private int mejorIteracion = 0;
    private int maxPedidosAsignados = 0;
    private double mejorRatioAsignacion = 0.0;

    /**
     * Registra métricas de una iteración
     */
    public void registrarIteracion(
            int iteracion,
            ACOSolution solucion,
            int totalPedidos,
            List<Camion> camionesDisponibles) {

        if (solucion == null) return;

        MetricasIteracion metricas = new MetricasIteracion();
        metricas.iteracion = iteracion;
        metricas.calidad = solucion.getCalidad();
        metricas.pedidosAsignados = solucion.getNumeroPedidosAsignados();
        metricas.pedidosNoAsignados = solucion.getPedidosNoAsignados().size();
        metricas.camionesUsados = solucion.getNumeroCamionesUtilizados();
        metricas.camionesDisponibles = camionesDisponibles.size();
        metricas.distanciaTotal = solucion.getDistanciaTotal();
        metricas.consumoTotal = solucion.getConsumoTotal();

        // Calcular ratio de asignación
        metricas.ratioAsignacion = (double) metricas.pedidosAsignados / totalPedidos;

        // Calcular utilización de flota
        double volumenTotal = solucion.getAsignaciones().stream()
                .flatMap(a -> a.getPedidos().stream())
                .mapToDouble(Pedido::getVolumen)
                .sum();

        double capacidadTotal = solucion.getAsignaciones().stream()
                .mapToDouble(a -> a.getCamion().getCargaM3())
                .sum();

        metricas.utilizacionFlota = capacidadTotal > 0 ? volumenTotal / capacidadTotal : 0;

        // Actualizar métricas globales
        if (metricas.calidad > mejorCalidad) {
            mejorCalidad = metricas.calidad;
            mejorIteracion = iteracion;
        }

        if (metricas.pedidosAsignados > maxPedidosAsignados) {
            maxPedidosAsignados = metricas.pedidosAsignados;
        }

        if (metricas.ratioAsignacion > mejorRatioAsignacion) {
            mejorRatioAsignacion = metricas.ratioAsignacion;
        }

        // Añadir a histórico
        historicoIteraciones.add(metricas);
    }

    /**
     * Analiza problemas en la última solución
     */
    public void analizarProblemas(ACOSolution solucion, int totalPedidos, List<Camion> flota) {
        if (solucion == null || historicoIteraciones.isEmpty()) {
            System.out.println("\n⚠️ No hay suficientes datos para análisis de problemas");
            return;
        }

        System.out.println("\n===================================");
        System.out.println("🔍 ANÁLISIS DE PROBLEMAS");
        System.out.println("===================================");

        // 1. Análisis de capacidad vs. demanda
        double volumenTotalPedidos = solucion.getPedidosNoAsignados().stream()
                .mapToDouble(Pedido::getVolumen)
                .sum();

        volumenTotalPedidos += solucion.getAsignaciones().stream()
                .flatMap(a -> a.getPedidos().stream())
                .mapToDouble(Pedido::getVolumen)
                .sum();

        double capacidadTotalFlota = flota.stream()
                .mapToDouble(Camion::getCargaM3)
                .sum();

        System.out.println("\n🚚 ANÁLISIS DE CAPACIDAD:");
        System.out.println("• Volumen total de pedidos: " + String.format("%.2f", volumenTotalPedidos) + "m³");
        System.out.println("• Capacidad total de flota: " + String.format("%.2f", capacidadTotalFlota) + "m³");

        if (volumenTotalPedidos > capacidadTotalFlota) {
            System.out.println("❌ PROBLEMA CRÍTICO: Demanda excede capacidad total de flota");
            System.out.println("  Déficit de capacidad: " +
                    String.format("%.2f", volumenTotalPedidos - capacidadTotalFlota) + "m³");
        } else {
            double ratioUtilizacion = volumenTotalPedidos / capacidadTotalFlota;
            System.out.println("• Utilización teórica máxima: " +
                    String.format("%.2f%%", ratioUtilizacion * 100));

            if (ratioUtilizacion > 0.9) {
                System.out.println("⚠️ ALERTA: Alta demanda relativa a capacidad");
            }
        }

        // 2. Análisis de pedidos no asignados
        if (!solucion.getPedidosNoAsignados().isEmpty()) {
            System.out.println("\n📦 ANÁLISIS DE PEDIDOS NO ASIGNADOS:");

            // Agrupar por tamaño
            int pequeños = 0, medianos = 0, grandes = 0;
            for (Pedido p : solucion.getPedidosNoAsignados()) {
                if (p.getVolumen() <= 5) pequeños++;
                else if (p.getVolumen() <= 15) medianos++;
                else grandes++;
            }

            System.out.println("• Distribución por tamaño:");
            System.out.println("  - Pequeños (≤5m³): " + pequeños + " pedidos");
            System.out.println("  - Medianos (5-15m³): " + medianos + " pedidos");
            System.out.println("  - Grandes (>15m³): " + grandes + " pedidos");

            if (grandes > 0 && grandes * 25 > capacidadTotalFlota * 0.2) {
                System.out.println("⚠️ PROBLEMA: Muchos pedidos grandes que ocupan capacidad significativa");
            }
        }

        // 3. Análisis de convergencia
        if (historicoIteraciones.size() >= 5) {
            System.out.println("\n📈 ANÁLISIS DE CONVERGENCIA:");

            boolean estancamiento = true;
            for (int i = historicoIteraciones.size() - 5; i < historicoIteraciones.size(); i++) {
                if (Math.abs(historicoIteraciones.get(i).calidad - mejorCalidad) > 0.000001) {
                    estancamiento = false;
                    break;
                }
            }

            if (estancamiento) {
                System.out.println("⚠️ ESTANCAMIENTO DETECTADO: Algoritmo estancado en óptimo local");
                System.out.println("• Recomendar: Aumentar perturbación en matriz de feromonas");
            } else {
                // Calcular tasa de mejora
                MetricasIteracion primera = historicoIteraciones.get(0);
                MetricasIteracion ultima = historicoIteraciones.get(historicoIteraciones.size() - 1);

                double mejoraCalidad = ultima.calidad - primera.calidad;
                double mejoraAsignacion = ultima.ratioAsignacion - primera.ratioAsignacion;

                System.out.println("• Mejora de calidad: " + String.format("%.6f", mejoraCalidad));
                System.out.println("• Mejora de asignación: " +
                        String.format("%.2f%%", mejoraAsignacion * 100));

                if (mejoraCalidad < 0.001 && mejoraAsignacion < 0.05) {
                    System.out.println("⚠️ ALERTA: Baja tasa de mejora");
                }
            }
        }

        // 4. Recomendaciones específicas
        System.out.println("\n🔧 RECOMENDACIONES ESPECÍFICAS:");

        double ratioAsignacion = (double) solucion.getNumeroPedidosAsignados() / totalPedidos;

        if (ratioAsignacion < 0.1) {
            System.out.println("1. CRÍTICO: Revisar parámetros fundamentales del algoritmo");
            System.out.println("   - Reducir factorEvaporacion a 0.1-0.2");
            System.out.println("   - Aumentar numeroHormigas a 100+");
            System.out.println("   - Aumentar iteraciones a 1000+");
            System.out.println("2. Verificar restricciones de tiempo y capacidad");
            System.out.println("3. Revisar código de evaluación de soluciones");
        } else if (ratioAsignacion < 0.5) {
            System.out.println("1. Aumentar umbral de distancia para agrupamiento de pedidos");
            System.out.println("2. Ajustar balance α/β para favorecer explotación");
            System.out.println("3. Mejorar estrategia de búsqueda local");
        } else {
            System.out.println("1. Refinar criterios de intercambio en búsqueda local");
            System.out.println("2. Optimizar utilización balanceada de camiones");
        }
    }

    /**
     * Clase interna para almacenar métricas de una iteración
     */
    private static class MetricasIteracion {
        int iteracion;
        double calidad;
        int pedidosAsignados;
        int pedidosNoAsignados;
        int camionesUsados;
        int camionesDisponibles;
        double distanciaTotal;
        double consumoTotal;
        double ratioAsignacion;
        double utilizacionFlota;
    }
}