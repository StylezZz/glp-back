package pucp.edu.glp.glpdp1.algorithm.model;

import lombok.Getter;
import lombok.Setter;
import pucp.edu.glp.glpdp1.domain.Pedido;
import pucp.edu.glp.glpdp1.domain.Ubicacion;

import java.util.Objects;

/**
 * Representa un tramo de ruta entre dos ubicaciones.
 * Puede ser un tramo de entrega, reabastecimiento o regreso.
 */
@Getter
@Setter
public class Ruta {

    private Ubicacion origen;
    private Ubicacion destino;
    private double distancia;

    // Propiedades especiales para diferentes tipos de tramos
    private boolean puntoEntrega;      // Indica si es un punto de entrega de pedido
    private boolean puntoReabastecimiento; // Indica si es un punto de reabastecimiento
    private boolean puntoRegreso;      // Indica si es un punto de regreso al almacén

    // Pedido que se entrega en este tramo (solo si es punto de entrega)
    private Pedido pedidoEntrega;

    /**
     * Constructor por defecto
     */
    public Ruta() {
        this.puntoEntrega = false;
        this.puntoReabastecimiento = false;
        this.puntoRegreso = false;
        this.pedidoEntrega = null;
    }

    /**
     * Constructor con parámetros básicos
     * @param origen Ubicación de origen
     * @param destino Ubicación de destino
     * @param distancia Distancia en kilómetros
     */
    public Ruta(Ubicacion origen, Ubicacion destino, double distancia) {
        this.origen = origen;
        this.destino = destino;
        this.distancia = distancia;
        this.puntoEntrega = false;
        this.puntoReabastecimiento = false;
        this.puntoRegreso = false;
        this.pedidoEntrega = null;
    }

    /**
     * Constructor completo
     * @param origen Ubicación de origen
     * @param destino Ubicación de destino
     * @param distancia Distancia en kilómetros
     * @param puntoEntrega Indica si es punto de entrega
     * @param puntoReabastecimiento Indica si es punto de reabastecimiento
     * @param puntoRegreso Indica si es punto de regreso
     * @param pedidoEntrega Pedido a entregar (solo si es punto de entrega)
     */
    public Ruta(Ubicacion origen, Ubicacion destino, double distancia,
                boolean puntoEntrega, boolean puntoReabastecimiento, boolean puntoRegreso,
                Pedido pedidoEntrega) {
        this.origen = origen;
        this.destino = destino;
        this.distancia = distancia;
        this.puntoEntrega = puntoEntrega;
        this.puntoReabastecimiento = puntoReabastecimiento;
        this.puntoRegreso = puntoRegreso;
        this.pedidoEntrega = pedidoEntrega;
    }

    /**
     * Verifica si un punto específico está contenido en esta ruta
     * @param punto Punto a verificar
     * @return true si el punto está en la ruta, false en caso contrario
     */
    public boolean contienePunto(Ubicacion punto) {
        // Un punto está en la ruta si es el origen o el destino
        // En una implementación más completa, se podría verificar si está en el camino entre origen y destino
        return (origen.getX() == punto.getX() && origen.getY() == punto.getY()) ||
                (destino.getX() == punto.getX() && destino.getY() == punto.getY());
    }

    /**
     * Crea una copia profunda del objeto
     * @return Nueva instancia con los mismos datos
     */
    @Override
    public Ruta clone() {
        return new Ruta(
                new Ubicacion(origen.getX(), origen.getY()),
                new Ubicacion(destino.getX(), destino.getY()),
                distancia,
                puntoEntrega,
                puntoReabastecimiento,
                puntoRegreso,
                pedidoEntrega // No necesita clonarse al ser una referencia a la entidad pedido
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Ruta ruta = (Ruta) o;
        return Double.compare(ruta.distancia, distancia) == 0 &&
                puntoEntrega == ruta.puntoEntrega &&
                puntoReabastecimiento == ruta.puntoReabastecimiento &&
                puntoRegreso == ruta.puntoRegreso &&
                Objects.equals(origen, ruta.origen) &&
                Objects.equals(destino, ruta.destino) &&
                Objects.equals(pedidoEntrega, ruta.pedidoEntrega);
    }

    @Override
    public int hashCode() {
        return Objects.hash(origen, destino, distancia, puntoEntrega, puntoReabastecimiento, puntoRegreso, pedidoEntrega);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Ruta de (").append(origen.getX()).append(",").append(origen.getY()).append(")");
        sb.append(" a (").append(destino.getX()).append(",").append(destino.getY()).append(")");
        sb.append(", distancia=").append(String.format("%.2f", distancia)).append("km");

        if (puntoEntrega) {
            sb.append(" [ENTREGA]");
            if (pedidoEntrega != null) {
                sb.append(" Pedido #").append(pedidoEntrega.getIdPedido());
            }
        }

        if (puntoReabastecimiento) {
            sb.append(" [REABASTECIMIENTO]");
        }

        if (puntoRegreso) {
            sb.append(" [REGRESO]");
        }

        return sb.toString();
    }
}