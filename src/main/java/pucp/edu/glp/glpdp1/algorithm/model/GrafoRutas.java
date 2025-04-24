package pucp.edu.glp.glpdp1.algorithm.model;

import lombok.Getter;
import lombok.Setter;
import pucp.edu.glp.glpdp1.algorithm.utils.DistanceCalculator;
import pucp.edu.glp.glpdp1.domain.Almacen;
import pucp.edu.glp.glpdp1.domain.Bloqueo;
import pucp.edu.glp.glpdp1.domain.Ubicacion;
import pucp.edu.glp.glpdp1.domain.enums.TipoAlmacen;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Representa el grafo de la ciudad para el algoritmo ACO.
 * Implementa una estructura de grafo basada en una rejilla 2D.
 */
@Getter
@Setter
public class GrafoRutas {

    private int ancho;
    private int alto;
    private Nodo[][] nodos;
    private List<Almacen> almacenes;
    private int totalNodos;
    private Map<Integer, Nodo> mapaNodos;

    /**
     * Constructor
     * @param ancho Ancho de la rejilla (dimensión X)
     * @param alto Alto de la rejilla (dimensión Y)
     * @param almacenes Lista de almacenes para marcar en el grafo
     */
    public GrafoRutas(int ancho, int alto, List<Almacen> almacenes) {
        this.ancho = ancho;
        this.alto = alto;
        this.almacenes = almacenes;
        this.nodos = new Nodo[ancho + 1][alto + 1]; // +1 porque las posiciones van de 0 a ancho/alto
        this.mapaNodos = new HashMap<>();

        inicializarGrafo();
    }

    /**
     * Inicializa el grafo creando los nodos y conexiones
     */
    private void inicializarGrafo() {
        int id = 0;

        // Crear nodos
        for (int x = 0; x <= ancho; x++) {
            for (int y = 0; y <= alto; y++) {
                Ubicacion ubicacion = new Ubicacion(x, y);
                Nodo nodo = new Nodo(id, ubicacion);

                // Marcar nodos especiales (almacenes)
                for (Almacen almacen : almacenes) {
                    if (almacen.getUbicacion().getX() == x && almacen.getUbicacion().getY() == y) {
                        nodo.setEsAlmacen(true);
                        nodo.setTipoAlmacen(almacen.getTipoAlmacen());
                        break;
                    }
                }

                nodos[x][y] = nodo;
                mapaNodos.put(id, nodo);
                id++;
            }
        }

        // Establecer conexiones (aristas)
        for (int x = 0; x <= ancho; x++) {
            for (int y = 0; y <= alto; y++) {
                Nodo nodo = nodos[x][y];
                List<Nodo> vecinos = new ArrayList<>();

                // Conexión horizontal derecha
                if (x < ancho) {
                    vecinos.add(nodos[x + 1][y]);
                }

                // Conexión horizontal izquierda
                if (x > 0) {
                    vecinos.add(nodos[x - 1][y]);
                }

                // Conexión vertical arriba
                if (y < alto) {
                    vecinos.add(nodos[x][y + 1]);
                }

                // Conexión vertical abajo
                if (y > 0) {
                    vecinos.add(nodos[x][y - 1]);
                }

                nodo.setVecinos(vecinos);
            }
        }

        this.totalNodos = id;
    }

    /**
     * Obtiene un nodo a partir de su ubicación
     * @param ubicacion Ubicación del nodo (coordenadas X,Y)
     * @return Nodo correspondiente o null si no existe
     */
    public Nodo obtenerNodo(Ubicacion ubicacion) {
        int x = ubicacion.getX();
        int y = ubicacion.getY();

        if (x >= 0 && x <= ancho && y >= 0 && y <= alto) {
            return nodos[x][y];
        }

        return null;
    }

    /**
     * Obtiene un nodo a partir de su ID
     * @param id Identificador único del nodo
     * @return Nodo correspondiente o null si no existe
     */
    public Nodo getNodoPorId(int id) {
        return mapaNodos.get(id);
    }

    /**
     * Obtiene la ubicación de un almacén según su tipo
     * @param tipo Tipo de almacén a buscar
     * @return Ubicación del almacén o null si no se encuentra
     */
    public Ubicacion obtenerUbicacionAlmacen(TipoAlmacen tipo) {
        for (Almacen almacen : almacenes) {
            if (almacen.getTipoAlmacen() == tipo) {
                return almacen.getUbicacion();
            }
        }
        return null;
    }

    /**
     * Encuentra una ruta viable entre dos nodos considerando bloqueos
     * Implementa el algoritmo A* (A-Star)
     * @param origen Nodo de origen
     * @param destino Nodo de destino
     * @param tiempoActual Momento actual para evaluar bloqueos
     * @return Lista de nodos que forman la ruta, o lista vacía si no hay ruta viable
     */
    public List<Nodo> encontrarRutaViable(Nodo origen, Nodo destino, LocalDateTime tiempoActual) {
        // Si origen y destino son el mismo nodo
        if (origen.getId() == destino.getId()) {
            return Collections.singletonList(origen);
        }

        // Conjunto de nodos abiertos (por explorar)
        PriorityQueue<NodoAStar> listaAbierta = new PriorityQueue<>(
                Comparator.comparingDouble(NodoAStar::getF));

        // Conjunto de nodos ya explorados
        Set<Integer> listaCerrada = new HashSet<>();

        // Mapa de nodos a sus padres en la ruta
        Map<Integer, Integer> padres = new HashMap<>();

        // Costo acumulado desde el origen hasta cada nodo
        Map<Integer, Double> costoG = new HashMap<>();

        // Inicializar con el nodo de origen
        listaAbierta.add(new NodoAStar(origen.getId(), 0,
                DistanceCalculator.calcularDistanciaManhattan(origen.getUbicacion(), destino.getUbicacion())));
        costoG.put(origen.getId(), 0.0);

        while (!listaAbierta.isEmpty()) {
            // Obtener nodo con menor f
            NodoAStar actual = listaAbierta.poll();
            int idActual = actual.getId();

            // Si llegamos al destino, reconstruir y devolver la ruta
            if (idActual == destino.getId()) {
                return reconstruirRuta(padres, origen.getId(), destino.getId());
            }

            // Marcar nodo como explorado
            listaCerrada.add(idActual);

            // Explorar vecinos
            Nodo nodoActual = getNodoPorId(idActual);
            for (Nodo vecino : nodoActual.getVecinos()) {
                int idVecino = vecino.getId();

                // Si ya exploramos este vecino, continuar
                if (listaCerrada.contains(idVecino)) {
                    continue;
                }

                // Verificar si el vecino está bloqueado en este tiempo
                if (estaBloqueo(vecino, tiempoActual)) {
                    continue;
                }

                // Calcular costo acumulado hasta este vecino
                double nuevoG = costoG.get(idActual) + 1; // 1 es la distancia entre nodos adyacentes

                // Si ya está en la lista abierta pero con mayor costo, o no está en la lista
                if (!costoG.containsKey(idVecino) || nuevoG < costoG.get(idVecino)) {
                    // Actualizar padre
                    padres.put(idVecino, idActual);

                    // Actualizar costo
                    costoG.put(idVecino, nuevoG);

                    // Calcular f = g + h
                    double h = DistanceCalculator.calcularDistanciaManhattan(
                            vecino.getUbicacion(), destino.getUbicacion());
                    double f = nuevoG + h;

                    // Añadir a la lista abierta (o actualizar si ya estaba)
                    listaAbierta.removeIf(n -> n.getId() == idVecino);
                    listaAbierta.add(new NodoAStar(idVecino, nuevoG, f));
                }
            }
        }

        // Si llegamos aquí, no hay ruta viable
        return new ArrayList<>();
    }

    /**
     * Reconstruye la ruta a partir del mapa de padres
     */
    private List<Nodo> reconstruirRuta(Map<Integer, Integer> padres, int idOrigen, int idDestino) {
        List<Nodo> ruta = new ArrayList<>();
        int actual = idDestino;

        // Reconstruir desde el destino hasta el origen
        while (actual != idOrigen) {
            ruta.add(0, getNodoPorId(actual));
            actual = padres.get(actual);
        }

        // Añadir el origen al principio
        ruta.add(0, getNodoPorId(idOrigen));

        return ruta;
    }

    /**
     * Verifica si un nodo está bloqueado en un momento dado
     * @param nodo Nodo a verificar
     * @param tiempo Momento actual
     * @return true si el nodo está bloqueado, false en caso contrario
     */
    public boolean estaBloqueo(Nodo nodo, LocalDateTime tiempo) {
        // Esta implementación simplificada siempre devuelve false
        // En una implementación real, consultaría la lista de bloqueos activos
        return false;
    }

    /**
     * Clase auxiliar para el algoritmo A*
     */
    private static class NodoAStar {
        private int id;
        private double g; // Costo acumulado desde el origen
        private double f; // Función de evaluación f = g + h

        public NodoAStar(int id, double g, double f) {
            this.id = id;
            this.g = g;
            this.f = f;
        }

        public int getId() {
            return id;
        }

        public double getG() {
            return g;
        }

        public double getF() {
            return f;
        }
    }
}