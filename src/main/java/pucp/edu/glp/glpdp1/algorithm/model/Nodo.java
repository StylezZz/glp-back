package pucp.edu.glp.glpdp1.algorithm.model;

import lombok.Getter;
import lombok.Setter;
import pucp.edu.glp.glpdp1.domain.Ubicacion;
import pucp.edu.glp.glpdp1.domain.enums.TipoAlmacen;

import java.util.ArrayList;
import java.util.List;

/**
 * Representa un nodo en el grafo que modela la ciudad.
 * Cada nodo corresponde a una intersección en la rejilla de la ciudad.
 */
@Getter
@Setter
public class Nodo {

    private int id;
    private Ubicacion ubicacion;
    private List<Nodo> vecinos;
    private boolean esAlmacen;
    private TipoAlmacen tipoAlmacen;

    /**
     * Constructor
     * @param id Identificador único del nodo
     * @param ubicacion Ubicación en el mapa (coordenadas X,Y)
     */
    public Nodo(int id, Ubicacion ubicacion) {
        this.id = id;
        this.ubicacion = ubicacion;
        this.vecinos = new ArrayList<>();
        this.esAlmacen = false;
        this.tipoAlmacen = null;
    }

    /**
     * Constructor con todos los parámetros
     * @param id Identificador único del nodo
     * @param ubicacion Ubicación en el mapa
     * @param esAlmacen Indica si es un almacén
     * @param tipoAlmacen Tipo de almacén
     */
    public Nodo(int id, Ubicacion ubicacion, boolean esAlmacen, TipoAlmacen tipoAlmacen) {
        this.id = id;
        this.ubicacion = ubicacion;
        this.vecinos = new ArrayList<>();
        this.esAlmacen = esAlmacen;
        this.tipoAlmacen = tipoAlmacen;
    }

    /**
     * Añade un vecino (nodo adyacente) a este nodo
     * @param vecino Nodo vecino a añadir
     */
    public void addVecino(Nodo vecino) {
        if (!vecinos.contains(vecino)) {
            vecinos.add(vecino);
        }
    }

    /**
     * Verifica si este nodo tiene un vecino específico
     * @param vecino Nodo a verificar
     * @return true si es vecino, false en caso contrario
     */
    public boolean esVecino(Nodo vecino) {
        return vecinos.contains(vecino);
    }

    /**
     * Obtiene la coordenada X de la ubicación
     * @return Coordenada X
     */
    public int getX() {
        return ubicacion.getX();
    }

    /**
     * Obtiene la coordenada Y de la ubicación
     * @return Coordenada Y
     */
    public int getY() {
        return ubicacion.getY();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Nodo nodo = (Nodo) o;
        return id == nodo.id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public String toString() {
        return "Nodo{" +
                "id=" + id +
                ", ubicacion=(" + ubicacion.getX() + "," + ubicacion.getY() + ")" +
                (esAlmacen ? ", almacen=" + tipoAlmacen : "") +
                '}';
    }
}