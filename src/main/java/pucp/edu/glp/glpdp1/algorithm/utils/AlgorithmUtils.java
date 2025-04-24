package pucp.edu.glp.glpdp1.algorithm.utils;

import pucp.edu.glp.glpdp1.domain.Pedido;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Clase de utilidades para el algoritmo ACO.
 * Proporciona métodos auxiliares para cálculos comunes.
 */
public class AlgorithmUtils {

    /**
     * Calcula el peso de la carga de un pedido en toneladas
     * @param pedido Pedido a evaluar
     * @return Peso en toneladas
     */
    public static double calcularPesoCarga(Pedido pedido) {
        // En el modelo de negocio, cada m3 de GLP pesa 0.5 toneladas
        return pedido.getVolumen() * 0.5;
    }

    /**
     * Calcula el peso total de la carga de una lista de pedidos
     * @param pedidos Lista de pedidos
     * @return Peso total en toneladas
     */
    public static double calcularPesoCargaTotal(List<Pedido> pedidos) {
        return pedidos.stream()
                .mapToDouble(AlgorithmUtils::calcularPesoCarga)
                .sum();
    }

    /**
     * Calcula el tiempo estimado de llegada considerando velocidad promedio
     * @param distancia Distancia en kilómetros
     * @param velocidadPromedio Velocidad promedio en km/h
     * @param tiempoInicio Tiempo de inicio
     * @return Tiempo estimado de llegada
     */
    public static LocalDateTime calcularTiempoEstimadoLlegada(
            double distancia, double velocidadPromedio, LocalDateTime tiempoInicio) {

        // Calcular tiempo en horas
        double tiempoHoras = distancia / velocidadPromedio;

        // Convertir a minutos
        long minutosEstimados = (long) (tiempoHoras * 60);

        // Añadir al tiempo de inicio
        return tiempoInicio.plusMinutes(minutosEstimados);
    }

    /**
     * Calcula el consumo de combustible según la fórmula del modelo
     * Consumo [Galones] = Distancia[Km] × Peso [Ton] / 180
     * @param distancia Distancia en kilómetros
     * @param pesoTotal Peso total en toneladas
     * @return Consumo en galones
     */
    public static double calcularConsumo(double distancia, double pesoTotal) {
        return (distancia * pesoTotal) / 180.0;
    }

    /**
     * Calcula el tiempo disponible antes de un plazo límite
     * @param tiempoActual Tiempo actual
     * @param tiempoLimite Tiempo límite
     * @return Tiempo disponible en minutos
     */
    public static long calcularTiempoDisponible(LocalDateTime tiempoActual, LocalDateTime tiempoLimite) {
        return ChronoUnit.MINUTES.between(tiempoActual, tiempoLimite);
    }

    /**
     * Determina el turno actual según la hora del día
     * @param tiempo Tiempo a evaluar
     * @return Turno ("T1", "T2", "T3")
     */
    public static String determinarTurno(LocalDateTime tiempo) {
        int hora = tiempo.getHour();

        if (hora >= 0 && hora < 8) {
            return "T1";
        } else if (hora >= 8 && hora < 16) {
            return "T2";
        } else {
            return "T3";
        }
    }

    /**
     * Calcula el tiempo hasta el inicio del próximo turno
     * @param tiempo Tiempo actual
     * @param turnoObjetivo Turno objetivo
     * @return Tiempo en minutos hasta el turno objetivo
     */
    public static long calcularMinutosHastaTurno(LocalDateTime tiempo, String turnoObjetivo) {
        LocalDateTime tiempoInicio;

        switch (turnoObjetivo) {
            case "T1":
                // T1 inicia a las 00:00
                tiempoInicio = tiempo.toLocalDate().atTime(0, 0);
                if (tiempo.toLocalTime().isAfter(tiempoInicio.toLocalTime())) {
                    tiempoInicio = tiempoInicio.plusDays(1);
                }
                break;
            case "T2":
                // T2 inicia a las 08:00
                tiempoInicio = tiempo.toLocalDate().atTime(8, 0);
                if (tiempo.toLocalTime().isAfter(tiempoInicio.toLocalTime())) {
                    tiempoInicio = tiempoInicio.plusDays(1);
                }
                break;
            case "T3":
                // T3 inicia a las 16:00
                tiempoInicio = tiempo.toLocalDate().atTime(16, 0);
                if (tiempo.toLocalTime().isAfter(tiempoInicio.toLocalTime())) {
                    tiempoInicio = tiempoInicio.plusDays(1);
                }
                break;
            default:
                throw new IllegalArgumentException("Turno no válido: " + turnoObjetivo);
        }

        return ChronoUnit.MINUTES.between(tiempo, tiempoInicio);
    }

    /**
     * Calcula la distancia máxima que puede recorrer un camión con el combustible actual
     * @param galones Galones de combustible disponibles
     * @param pesoTotal Peso total del camión con carga en toneladas
     * @return Distancia máxima en kilómetros
     */
    public static double calcularDistanciaMaxima(int galones, double pesoTotal) {
        // Despejando la fórmula: Consumo [Galones] = Distancia[Km] × Peso [Ton] / 180
        return (galones * 180.0) / pesoTotal;
    }
}