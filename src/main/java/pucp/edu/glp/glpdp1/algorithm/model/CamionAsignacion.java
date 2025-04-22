package pucp.edu.glp.glpdp1.algorithm.model;

import lombok.Getter;
import lombok.Setter;
import pucp.edu.glp.glpdp1.algorithm.utils.AlgorithmUtils;
import pucp.edu.glp.glpdp1.domain.Camion;
import pucp.edu.glp.glpdp1.domain.Pedido;

import java.util.ArrayList;
import java.util.List;

/**
 * Representa la asignación de un camión a un conjunto de pedidos y rutas.
 * Esta clase es parte del modelo interno del algoritmo ACO.
 */
@Getter
@Setter
public class CamionAsignacion {

    private Camion camion;
    private List<Pedido> pedidos;
    private List<Ruta> rutas;
    private double consumoTotal;

    /**
     * Constructor
     * @param camion Camión asignado
     * @param pedidos Lista de pedidos asignados
     */
    public CamionAsignacion(Camion camion, List<Pedido> pedidos) {
        this.camion = camion;
        this.pedidos = new ArrayList<>(pedidos);
        this.rutas = new ArrayList<>();
        this.consumoTotal = 0.0;
    }

    /**
     * Constructor
     * @param camion Camión asignado
     * @param pedidos Lista de pedidos asignados
     * @param rutas Lista de rutas a seguir
     */
    public CamionAsignacion(Camion camion, List<Pedido> pedidos, List<Ruta> rutas) {
        this.camion = camion;
        this.pedidos = new ArrayList<>(pedidos);
        this.rutas = new ArrayList<>(rutas);
        this.consumoTotal = calcularConsumoTotal();
    }

    /**
     * Calcula el consumo total de combustible para esta asignación
     * @return Consumo total en galones
     */
    private double calcularConsumoTotal() {
        double consumo = 0.0;
        double pesoActual = camion.getPesoBrutoTon() + AlgorithmUtils.calcularPesoCargaTotal(pedidos);

        for (Ruta ruta : rutas) {
            // RF87: Cálculo dinámico de consumo considerando la carga
            double consumoTramo = (ruta.getDistancia() * pesoActual) / 180.0;
            consumo += consumoTramo;

            // Si es punto de entrega, reducir el peso para el siguiente tramo
            if (ruta.isPuntoEntrega() && ruta.getPedidoEntrega() != null) {
                double pesoCarga = AlgorithmUtils.calcularPesoCarga(ruta.getPedidoEntrega());
                pesoActual -= pesoCarga;
            }
        }

        return consumo;
    }

    /**
     * Calcula la distancia total de todas las rutas en la asignación
     * @return Distancia total en kilómetros
     */
    public double getDistanciaTotal() {
        return rutas.stream()
                .mapToDouble(Ruta::getDistancia)
                .sum();
    }

    /**
     * Crea una copia profunda del objeto
     * @return Una nueva instancia con los mismos datos
     */
    public CamionAsignacion clone() {
        List<Pedido> pedidosClonados = new ArrayList<>(this.pedidos);
        List<Ruta> rutasClonadas = new ArrayList<>();

        for (Ruta ruta : this.rutas) {
            rutasClonadas.add(ruta.clone());
        }

        CamionAsignacion clon = new CamionAsignacion(this.camion, pedidosClonados, rutasClonadas);
        clon.setConsumoTotal(this.consumoTotal);

        return clon;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Asignación de camión ").append(camion.getIdC()).append("\n");
        sb.append("Pedidos: ").append(pedidos.size()).append("\n");
        sb.append("Rutas: ").append(rutas.size()).append("\n");
        sb.append("Distancia total: ").append(String.format("%.2f", getDistanciaTotal())).append(" km\n");
        sb.append("Consumo estimado: ").append(String.format("%.2f", consumoTotal)).append(" galones\n");

        return sb.toString();
    }
}