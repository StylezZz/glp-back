package pucp.edu.glp.glpdp1.algorithm.aco;

import lombok.Getter;
import lombok.Setter;
import pucp.edu.glp.glpdp1.algorithm.model.CamionAsignacion;
import pucp.edu.glp.glpdp1.domain.Pedido;

import java.util.ArrayList;
import java.util.List;

/**
 * Representa una solución completa al problema de optimización de rutas.
 * Contiene la asignación de pedidos a camiones, las rutas planificadas,
 * y la calidad (fitness) de la solución.
 */
@Getter
@Setter
public class ACOSolution {

    // Lista de asignaciones de camiones (cada asignación incluye camión, pedidos y rutas)
    private List<CamionAsignacion> asignaciones;

    // Pedidos que no pudieron ser asignados
    private List<Pedido> pedidosNoAsignados;

    // Calidad (fitness) de la solución - mayor es mejor
    private double calidad;

    /**
     * Constructor por defecto
     */
    public ACOSolution() {
        this.asignaciones = new ArrayList<>();
        this.pedidosNoAsignados = new ArrayList<>();
        this.calidad = 0.0;
    }

    /**
     * Constructor con parámetros
     * @param asignaciones Lista de asignaciones camión-pedidos-rutas
     * @param pedidosNoAsignados Lista de pedidos no asignados
     */
    public ACOSolution(List<CamionAsignacion> asignaciones, List<Pedido> pedidosNoAsignados) {
        this.asignaciones = asignaciones;
        this.pedidosNoAsignados = pedidosNoAsignados;
        this.calidad = 0.0;
    }

    /**
     * Añade una asignación a la solución
     * @param asignacion Asignación camión-pedidos-rutas
     */
    public void addAsignacion(CamionAsignacion asignacion) {
        this.asignaciones.add(asignacion);
    }

    /**
     * Añade un pedido no asignado
     * @param pedido Pedido que no pudo ser asignado
     */
    public void addPedidoNoAsignado(Pedido pedido) {
        this.pedidosNoAsignados.add(pedido);
    }

    /**
     * Obtiene el número total de pedidos asignados en esta solución
     * @return Número de pedidos asignados
     */
    public int getNumeroPedidosAsignados() {
        return asignaciones.stream()
                .mapToInt(a -> a.getPedidos().size())
                .sum();
    }

    /**
     * Obtiene el número total de camiones utilizados en esta solución
     * @return Número de camiones utilizados
     */
    public int getNumeroCamionesUtilizados() {
        return asignaciones.size();
    }

    /**
     * Verifica si un pedido está asignado en esta solución
     * @param pedido Pedido a verificar
     * @return true si el pedido está asignado, false en caso contrario
     */
    public boolean isPedidoAsignado(Pedido pedido) {
        for (CamionAsignacion asignacion : asignaciones) {
            if (asignacion.getPedidos().contains(pedido)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Crea una copia profunda de la solución actual
     * @return Una nueva instancia con los mismos datos
     */
    public ACOSolution clone() {
        ACOSolution clonada = new ACOSolution();
        clonada.setCalidad(this.calidad);

        // Clonar asignaciones
        List<CamionAsignacion> asignacionesClonadas = new ArrayList<>();
        for (CamionAsignacion asignacion : this.asignaciones) {
            asignacionesClonadas.add(asignacion.clone());
        }
        clonada.setAsignaciones(asignacionesClonadas);

        // Clonar pedidos no asignados (referencia, no es necesario clonar objetos Pedido)
        List<Pedido> pedidosNoAsignadosClonados = new ArrayList<>(this.pedidosNoAsignados);
        clonada.setPedidosNoAsignados(pedidosNoAsignadosClonados);

        return clonada;
    }

    /**
     * Calcula la distancia total de todas las rutas en la solución
     * @return Distancia total en kilómetros
     */
    public double getDistanciaTotal() {
        return asignaciones.stream()
                .mapToDouble(CamionAsignacion::getDistanciaTotal)
                .sum();
    }

    /**
     * Calcula el consumo total de combustible en galones
     * @return Consumo total en galones
     */
    public double getConsumoTotal() {
        return asignaciones.stream()
                .mapToDouble(CamionAsignacion::getConsumoTotal)
                .sum();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Solución ACO (Calidad: ").append(String.format("%.6f", calidad)).append(")\n");
        sb.append("Camiones utilizados: ").append(getNumeroCamionesUtilizados()).append("\n");
        sb.append("Pedidos asignados: ").append(getNumeroPedidosAsignados()).append("\n");
        sb.append("Pedidos no asignados: ").append(pedidosNoAsignados.size()).append("\n");
        sb.append("Distancia total: ").append(String.format("%.2f", getDistanciaTotal())).append(" km\n");
        sb.append("Consumo total: ").append(String.format("%.2f", getConsumoTotal())).append(" galones\n");

        return sb.toString();
    }
}