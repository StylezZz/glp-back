package pucp.edu.glp.glpdp1.algorithm.utils;

import pucp.edu.glp.glpdp1.domain.Pedido;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Utilidades para calcular la urgencia de los pedidos.
 * La urgencia se basa en el tiempo restante hasta la fecha límite.
 */
public class UrgencyCalculator {

    /**
     * Calcula la urgencia normalizada de un pedido
     * @param pedido Pedido a evaluar
     * @return Valor de urgencia entre 0 y 1 (mayor valor = más urgente)
     */
    public static double calcularUrgenciaNormalizada(Pedido pedido) {
        return calcularUrgenciaNormalizada(pedido, LocalDateTime.now());
    }

    /**
     * Calcula la urgencia normalizada de un pedido en un momento específico
     * @param pedido Pedido a evaluar
     * @param tiempoActual Tiempo actual para el cálculo
     * @return Valor de urgencia entre 0 y 1 (mayor valor = más urgente)
     */
    public static double calcularUrgenciaNormalizada(Pedido pedido, LocalDateTime tiempoActual) {
        if (pedido.getFechaLimite() == null) {
            return 0.0; // Sin fecha límite, no hay urgencia
        }

        // Si ya pasó la fecha límite, máxima urgencia
        if (tiempoActual.isAfter(pedido.getFechaLimite())) {
            return 1.0;
        }

        // Tiempo total desde registro hasta límite (en minutos)
        long tiempoTotalMinutos = Duration.between(
                pedido.getFechaRegistro(),
                pedido.getFechaLimite()).toMinutes();

        if (tiempoTotalMinutos <= 0) {
            return 1.0; // Tiempo inválido, asumir máxima urgencia
        }

        // Tiempo transcurrido desde registro hasta ahora (en minutos)
        long tiempoTranscurridoMinutos = Duration.between(
                pedido.getFechaRegistro(),
                tiempoActual).toMinutes();

        // Porcentaje de tiempo transcurrido
        double porcentajeTranscurrido = (double) tiempoTranscurridoMinutos / tiempoTotalMinutos;

        // Función de urgencia: se incrementa exponencialmente a medida que se acerca el límite
        // Usando: urgencia = porcentajeTranscurrido^2
        return Math.min(1.0, Math.pow(porcentajeTranscurrido, 2));
    }

    /**
     * Calcula la urgencia con una función no lineal más agresiva
     * @param pedido Pedido a evaluar
     * @param tiempoActual Tiempo actual para el cálculo
     * @return Valor de urgencia entre 0 y 1
     */
    public static double calcularUrgenciaAgresiva(Pedido pedido, LocalDateTime tiempoActual) {
        double urgenciaNormal = calcularUrgenciaNormalizada(pedido, tiempoActual);

        // Función cúbica: se incrementa más rápidamente cerca del límite
        return Math.pow(urgenciaNormal, 3);
    }

    /**
     * Ordena una lista de pedidos por urgencia (del más urgente al menos urgente)
     * @param pedidos Lista de pedidos a ordenar
     * @param tiempoActual Tiempo actual para el cálculo
     */
    public static void ordenarPorUrgencia(List<Pedido> pedidos, LocalDateTime tiempoActual) {
        pedidos.sort((p1, p2) -> {
            double u1 = calcularUrgenciaNormalizada(p1, tiempoActual);
            double u2 = calcularUrgenciaNormalizada(p2, tiempoActual);
            return Double.compare(u2, u1); // Orden descendente (más urgente primero)
        });
    }

    /**
     * Verifica si un pedido está en estado crítico (a punto de vencer)
     * @param pedido Pedido a verificar
     * @param tiempoActual Tiempo actual
     * @param umbralCritico Umbral de tiempo en minutos para considerarse crítico
     * @return true si el pedido está en estado crítico
     */
    public static boolean esPedidoCritico(Pedido pedido, LocalDateTime tiempoActual, int umbralCritico) {
        if (pedido.getFechaLimite() == null) {
            return false;
        }

        // Calcular minutos restantes hasta la fecha límite
        long minutosRestantes = Duration.between(tiempoActual, pedido.getFechaLimite()).toMinutes();

        // Es crítico si quedan menos minutos que el umbral
        return minutosRestantes <= umbralCritico;
    }
}