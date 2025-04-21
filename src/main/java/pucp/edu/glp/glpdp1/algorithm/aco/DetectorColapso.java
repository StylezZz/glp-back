package pucp.edu.glp.glpdp1.aco;

import pucp.edu.glp.glpdp1.models.*;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;

/**
 * Clase que detecta situaciones de colapso del sistema (RF94)
 */
public class DetectorColapso {
    private List<Pedido> pedidos;
    private Flota flota;
    private LocalDateTime momentoActual;

    // Umbrales para la detección de colapso
    private final double UMBRAL_CAMIONES_INACTIVOS = 0.7; // 70% de camiones inactivos
    private final double UMBRAL_PEDIDOS_VENCIDOS = 0.3;   // 30% de pedidos vencidos
    private final double UMBRAL_TIEMPO_ENTREGA = 2.0;     // Tiempo de entrega duplicado

    public DetectorColapso(List<Pedido> pedidos, Flota flota, LocalDateTime momentoActual) {
        this.pedidos = pedidos;
        this.flota = flota;
        this.momentoActual = momentoActual;
    }

    /**
     * Verifica si el sistema está en colapso irreversible
     * @return Indicador de colapso (0-1, donde 1 es colapso total)
     */
    public double verificarColapsoSistema() {
        double indicadorColapso = 0.0;

        // 1. Verificar disponibilidad de camiones
        double ratioInactividad = calcularRatioInactividad();
        if (ratioInactividad > UMBRAL_CAMIONES_INACTIVOS) {
            indicadorColapso += 0.4; // Peso alto para falta de camiones
            System.out.println("ALERTA: " + String.format("%.1f", ratioInactividad * 100) +
                    "% de camiones fuera de servicio.");
        }

        // 2. Verificar pedidos vencidos
        double ratioPedidosVencidos = calcularRatioPedidosVencidos();
        if (ratioPedidosVencidos > UMBRAL_PEDIDOS_VENCIDOS) {
            indicadorColapso += 0.4; // Peso alto para pedidos sin atender
            System.out.println("ALERTA: " + String.format("%.1f", ratioPedidosVencidos * 100) +
                    "% de pedidos vencidos o críticos.");
        }

        // 3. Verificar tiempos de entrega
        double ratioTiempoEntrega = calcularRatioTiempoEntrega();
        if (ratioTiempoEntrega > UMBRAL_TIEMPO_ENTREGA) {
            indicadorColapso += 0.2; // Peso menor para tiempos largos
            System.out.println("ALERTA: Tiempo promedio de entrega " +
                    String.format("%.1f", ratioTiempoEntrega) +
                    " veces mayor al objetivo.");
        }

        // Normalizar a máximo 1.0
        return Math.min(1.0, indicadorColapso);
    }

    /**
     * Calcula el ratio de camiones inactivos
     * @return Ratio de inactividad (0-1)
     */
    private double calcularRatioInactividad() {
        int totalCamiones = 0;
        int camionesInactivos = 0;

        // Aquí necesitaríamos acceso a todos los camiones, no solo los disponibles
        // Como simplificación, usamos la información disponible en la flota
        List<Camion> camionesDisponibles = flota.obtenerDisponibles();
        totalCamiones = camionesDisponibles.size();

        for (Camion camion : camionesDisponibles) {
            if (camion.getEstado() != Camion.EstadoCamion.DISPONIBLE &&
                    camion.getEstado() != Camion.EstadoCamion.EN_RUTA) {
                camionesInactivos++;
            }
        }

        // Si no hay información de camiones, asumir situación crítica
        if (totalCamiones == 0) {
            return 1.0;
        }

        return (double) camionesInactivos / totalCamiones;
    }

    /**
     * Calcula el ratio de pedidos vencidos o críticos
     * @return Ratio de pedidos vencidos (0-1)
     */
    private double calcularRatioPedidosVencidos() {
        if (pedidos.isEmpty()) {
            return 0.0;
        }

        int pedidosVencidos = 0;

        for (Pedido pedido : pedidos) {
            if (pedido.getEstado() == Pedido.EstadoPedido.PENDIENTE) {
                LocalDateTime limite = pedido.getFechaPedido().plus(pedido.getTiempoLimite());

                // Pedido ya vencido o crítico (menos de 2 horas)
                if (limite.isBefore(momentoActual) ||
                        Duration.between(momentoActual, limite).toHours() < 2) {
                    pedidosVencidos++;
                }
            }
        }

        return (double) pedidosVencidos / pedidos.size();
    }

    /**
     * Calcula el ratio de tiempo de entrega promedio respecto al objetivo
     * @return Ratio de tiempo (>1 indica demoras)
     */
    private double calcularRatioTiempoEntrega() {
        // Este cálculo requeriría datos históricos de entregas
        // Como simplificación, usamos una estimación basada en pedidos pendientes

        long tiempoPromedioEstimado = 0;
        long tiempoObjetivo = 0;
        int contadorPedidos = 0;

        for (Pedido pedido : pedidos) {
            if (pedido.getEstado() == Pedido.EstadoPedido.PENDIENTE) {
                // Tiempo objetivo sería el tiempo límite establecido
                long tiempoLimiteMinutos = pedido.getTiempoLimite().toMinutes();
                tiempoObjetivo += tiempoLimiteMinutos;

                // Tiempo estimado sería el tiempo límite más un factor de retraso
                // basado en la congestión actual (pedidos pendientes)
                long factorRetraso = pedidos.size() / 10; // Simple heurística
                tiempoPromedioEstimado += tiempoLimiteMinutos * (1 + factorRetraso * 0.1);

                contadorPedidos++;
            }
        }

        if (contadorPedidos == 0 || tiempoObjetivo == 0) {
            return 1.0; // Valor neutral
        }

        return (double) tiempoPromedioEstimado / tiempoObjetivo;
    }

    /**
     * Verifica si el sistema ha colapsado
     * @param umbralColapso Umbral para determinar colapso (0-1)
     * @return true si el sistema está en colapso, false en caso contrario
     */
    public boolean haColapsado(double umbralColapso) {
        double indicador = verificarColapsoSistema();

        if (indicador >= umbralColapso) {
            System.out.println("ALERTA CRÍTICA: Sistema en estado de colapso (Indicador: " +
                    String.format("%.2f", indicador) +
                    ", Umbral: " + String.format("%.2f", umbralColapso) + ")");
            return true;
        }

        return false;
    }
}