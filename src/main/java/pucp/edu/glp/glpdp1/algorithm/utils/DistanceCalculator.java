package pucp.edu.glp.glpdp1.algorithm.utils;

import pucp.edu.glp.glpdp1.algorithm.model.Nodo;
import pucp.edu.glp.glpdp1.domain.Ubicacion;

import java.util.List;

/**
 * Utilidades para el cálculo de distancias en el grafo de la ciudad.
 */
public class DistanceCalculator {

    /**
     * Calcula la distancia Manhattan entre dos ubicaciones
     * La distancia Manhattan es la suma de las diferencias absolutas de sus coordenadas
     * @param origen Ubicación de origen
     * @param destino Ubicación de destino
     * @return Distancia en unidades (kilómetros en el contexto del problema)
     */
    public static double calcularDistanciaManhattan(Ubicacion origen, Ubicacion destino) {
        return Math.abs(origen.getX() - destino.getX()) +
                Math.abs(origen.getY() - destino.getY());
    }

    /**
     * Calcula la distancia Manhattan entre dos nodos
     * @param origen Nodo de origen
     * @param destino Nodo de destino
     * @return Distancia en unidades (kilómetros)
     */
    public static double calcularDistanciaManhattan(Nodo origen, Nodo destino) {
        return calcularDistanciaManhattan(origen.getUbicacion(), destino.getUbicacion());
    }

    /**
     * Calcula la distancia total de una ruta compuesta por varios nodos
     * @param ruta Lista de nodos que forman la ruta
     * @return Distancia total en kilómetros
     */
    public static double calcularDistanciaRuta(List<Nodo> ruta) {
        if (ruta == null || ruta.size() < 2) {
            return 0;
        }

        double distanciaTotal = 0;

        for (int i = 0; i < ruta.size() - 1; i++) {
            distanciaTotal += calcularDistanciaManhattan(ruta.get(i), ruta.get(i + 1));
        }

        return distanciaTotal;
    }

    /**
     * Encuentra el punto más cercano a una ubicación dada dentro de un conjunto de puntos
     * @param ubicacion Ubicación de referencia
     * @param puntos Lista de ubicaciones a comparar
     * @return La ubicación más cercana, o null si la lista está vacía
     */
    public static Ubicacion encontrarPuntoMasCercano(Ubicacion ubicacion, List<Ubicacion> puntos) {
        if (puntos == null || puntos.isEmpty()) {
            return null;
        }

        Ubicacion masCercano = null;
        double distanciaMinima = Double.MAX_VALUE;

        for (Ubicacion punto : puntos) {
            double distancia = calcularDistanciaManhattan(ubicacion, punto);

            if (distancia < distanciaMinima) {
                distanciaMinima = distancia;
                masCercano = punto;
            }
        }

        return masCercano;
    }

    /**
     * Calcula el centro geométrico (centroide) de un conjunto de ubicaciones
     * @param ubicaciones Lista de ubicaciones
     * @return Ubicación del centro geométrico
     */
    public static Ubicacion calcularCentroGeometrico(List<Ubicacion> ubicaciones) {
        if (ubicaciones == null || ubicaciones.isEmpty()) {
            return null;
        }

        int sumaX = 0;
        int sumaY = 0;

        for (Ubicacion ubicacion : ubicaciones) {
            sumaX += ubicacion.getX();
            sumaY += ubicacion.getY();
        }

        int x = sumaX / ubicaciones.size();
        int y = sumaY / ubicaciones.size();

        return new Ubicacion(x, y);
    }

    /**
     * Verifica si dos ubicaciones están en la misma fila o columna
     * (pueden moverse una hacia la otra en línea recta sin cambiar dirección)
     */
    private boolean estanEnMismaLineaRecta(Ubicacion u1, Ubicacion u2) {
        return u1.getX() == u2.getX() || u1.getY() == u2.getY();
    }
}