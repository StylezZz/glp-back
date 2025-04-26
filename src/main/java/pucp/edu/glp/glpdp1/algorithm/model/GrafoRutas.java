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
    private List<Bloqueo> bloqueos;
    private Map<Integer, List<Bloqueo>> nodosAfectadosPorBloqueo;

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
        this.bloqueos = new ArrayList<>();
        this.nodosAfectadosPorBloqueo = new HashMap<>();

        inicializarGrafo();
    }

    /**
     * Actualiza la lista de bloqueos y recalcula las estructuras auxiliares
     * @param bloqueos Lista actualizada de bloqueos
     */
    public void actualizarBloqueos(List<Bloqueo> bloqueos) {
        this.bloqueos = new ArrayList<>(bloqueos);
        recalcularMapaBloqueos();
    }

    /**
     * Recalcula el mapa de nodos afectados por cada bloqueo
     * Esta es una optimización para hacer más eficiente la consulta de bloqueos
     */
    private void recalcularMapaBloqueos() {
        // Limpiar mapa existente
        nodosAfectadosPorBloqueo.clear();

        // Para cada bloqueo, registrar los nodos que afecta
        for (Bloqueo bloqueo : bloqueos) {
            List<Ubicacion> tramos = bloqueo.getTramos();

            for (int i = 0; i < tramos.size() - 1; i++) {
                Ubicacion u1 = tramos.get(i);
                Ubicacion u2 = tramos.get(i + 1);

                // Encontrar nodos afectados por este tramo
                Nodo n1 = obtenerNodo(u1);
                Nodo n2 = obtenerNodo(u2);

                if (n1 != null && n2 != null) {
                    // Registrar nodos afectados
                    registrarNodoAfectado(n1.getId(), bloqueo);
                    registrarNodoAfectado(n2.getId(), bloqueo);

                    // También registrar nodos intermedios en caso de tramos largos
                    List<Nodo> nodosIntermedios = encontrarNodosIntermedios(n1, n2);
                    for (Nodo nodo : nodosIntermedios) {
                        registrarNodoAfectado(nodo.getId(), bloqueo);
                    }
                }
            }
        }
    }

    /**
     * Registra un bloqueo que afecta a un nodo
     */
    private void registrarNodoAfectado(int idNodo, Bloqueo bloqueo) {
        if (!nodosAfectadosPorBloqueo.containsKey(idNodo)) {
            nodosAfectadosPorBloqueo.put(idNodo, new ArrayList<>());
        }
        nodosAfectadosPorBloqueo.get(idNodo).add(bloqueo);
    }

    /**
     * Encuentra nodos intermedios en un tramo largo
     */
    private List<Nodo> encontrarNodosIntermedios(Nodo inicio, Nodo fin) {
        List<Nodo> intermedios = new ArrayList<>();
        int x1 = inicio.getUbicacion().getX();
        int y1 = inicio.getUbicacion().getY();
        int x2 = fin.getUbicacion().getX();
        int y2 = fin.getUbicacion().getY();

        // Caso de línea horizontal
        if (y1 == y2) {
            int inicioX = Math.min(x1, x2);
            int finX = Math.max(x1, x2);

            for (int x = inicioX + 1; x < finX; x++) {
                Nodo nodo = nodos[x][y1];
                if (nodo != null) {
                    intermedios.add(nodo);
                }
            }
        }
        // Caso de línea vertical
        else if (x1 == x2) {
            int inicioY = Math.min(y1, y2);
            int finY = Math.max(y1, y2);

            for (int y = inicioY + 1; y < finY; y++) {
                Nodo nodo = nodos[x1][y];
                if (nodo != null) {
                    intermedios.add(nodo);
                }
            }
        }

        return intermedios;
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
     */
    public boolean estaBloqueo(Nodo nodo, LocalDateTime tiempo) {
        // Verificar si este nodo está afectado por algún bloqueo
        List<Bloqueo> bloqueosAfectan = nodosAfectadosPorBloqueo.get(nodo.getId());

        if (bloqueosAfectan == null || bloqueosAfectan.isEmpty()) {
            return false; // No hay bloqueos que afecten a este nodo
        }

        // Verificar si algún bloqueo está activo en este tiempo
        for (Bloqueo bloqueo : bloqueosAfectan) {
            if (estaActivoEnTiempo(bloqueo, tiempo)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Verifica si hay un bloqueo entre dos nodos adyacentes
     */
    public boolean estaBloqueo(Nodo nodo1, Nodo nodo2, LocalDateTime tiempo) {
        // Si alguno de los nodos está bloqueado, la arista también lo está
        if (estaBloqueo(nodo1, tiempo) || estaBloqueo(nodo2, tiempo)) {
            return true;
        }

        // También verificar si hay un bloqueo específico para esta arista
        Ubicacion u1 = nodo1.getUbicacion();
        Ubicacion u2 = nodo2.getUbicacion();

        for (Bloqueo bloqueo : bloqueos) {
            if (estaActivoEnTiempo(bloqueo, tiempo)) {
                List<Ubicacion> tramos = bloqueo.getTramos();

                for (int i = 0; i < tramos.size() - 1; i++) {
                    Ubicacion b1 = tramos.get(i);
                    Ubicacion b2 = tramos.get(i + 1);

                    // Verificar si este tramo de bloqueo afecta directamente la arista
                    if ((u1.equals(b1) && u2.equals(b2)) || (u1.equals(b2) && u2.equals(b1))) {
                        return true;
                    }

                    // O si intersecta la arista
                    if (intersectanSegmentos(u1, u2, b1, b2)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Verifica si un bloqueo está activo en un momento dado
     */
    public boolean estaActivoEnTiempo(Bloqueo bloqueo, LocalDateTime tiempo) {
        return !tiempo.isBefore(bloqueo.getFechaInicio()) && !tiempo.isAfter(bloqueo.getFechaFinal());
    }

    /**
     * Verifica si dos segmentos se intersectan
     */
    private boolean intersectanSegmentos(Ubicacion a1, Ubicacion a2, Ubicacion b1, Ubicacion b2) {
        // En una malla 2D, los segmentos se intersectan si:
        // 1. Ambos son horizontales y están en la misma fila, con rangos de X solapados
        // 2. Ambos son verticales y están en la misma columna, con rangos de Y solapados
        // 3. Uno es horizontal y otro vertical, y el punto de intersección está en ambos rangos

        boolean a_horizontal = a1.getY() == a2.getY();
        boolean a_vertical = a1.getX() == a2.getX();
        boolean b_horizontal = b1.getY() == b2.getY();
        boolean b_vertical = b1.getX() == b2.getX();

        // Caso 1: Ambos horizontales
        if (a_horizontal && b_horizontal && a1.getY() == b1.getY()) {
            int a_min_x = Math.min(a1.getX(), a2.getX());
            int a_max_x = Math.max(a1.getX(), a2.getX());
            int b_min_x = Math.min(b1.getX(), b2.getX());
            int b_max_x = Math.max(b1.getX(), b2.getX());

            return !(a_max_x < b_min_x || b_max_x < a_min_x);
        }

        // Caso 2: Ambos verticales
        if (a_vertical && b_vertical && a1.getX() == b1.getX()) {
            int a_min_y = Math.min(a1.getY(), a2.getY());
            int a_max_y = Math.max(a1.getY(), a2.getY());
            int b_min_y = Math.min(b1.getY(), b2.getY());
            int b_max_y = Math.max(b1.getY(), b2.getY());

            return !(a_max_y < b_min_y || b_max_y < a_min_y);
        }

        // Caso 3: Uno horizontal, otro vertical
        if (a_horizontal && b_vertical) {
            int a_y = a1.getY();
            int b_x = b1.getX();
            int a_min_x = Math.min(a1.getX(), a2.getX());
            int a_max_x = Math.max(a1.getX(), a2.getX());
            int b_min_y = Math.min(b1.getY(), b2.getY());
            int b_max_y = Math.max(b1.getY(), b2.getY());

            return (b_x >= a_min_x && b_x <= a_max_x) &&
                    (a_y >= b_min_y && a_y <= b_max_y);
        }

        if (a_vertical && b_horizontal) {
            int a_x = a1.getX();
            int b_y = b1.getY();
            int a_min_y = Math.min(a1.getY(), a2.getY());
            int a_max_y = Math.max(a1.getY(), a2.getY());
            int b_min_x = Math.min(b1.getX(), b2.getX());
            int b_max_x = Math.max(b1.getX(), b2.getX());

            return (a_x >= b_min_x && a_x <= b_max_x) &&
                    (b_y >= a_min_y && b_y <= a_max_y);
        }

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

    /**
     * Encuentra una ruta viable considerando bloqueos en una ventana temporal
     */
    public List<Nodo> encontrarRutaViableConBloqueos(
            Nodo origen,
            Nodo destino,
            LocalDateTime tiempoInicio,
            LocalDateTime tiempoFin) {

        // Si no hay bloqueos futuros, usar la función estándar para el tiempo actual
        if (!hayBloqueosFuturosEnRuta(origen, destino, tiempoInicio, tiempoFin)) {
            return encontrarRutaViable(origen, destino, tiempoInicio);
        }

        // Hay bloqueos futuros, necesitamos una búsqueda más sofisticada
        // Implementar A* modificado que considere bloqueos futuros

        // Estructuras para A*
        PriorityQueue<NodoAStar> listaAbierta = new PriorityQueue<>(
                Comparator.comparingDouble(NodoAStar::getF));
        Set<Integer> listaCerrada = new HashSet<>();
        Map<Integer, Integer> padres = new HashMap<>();
        Map<Integer, Double> costoG = new HashMap<>();

        // Inicializar con nodo origen
        listaAbierta.add(new NodoAStar(origen.getId(), 0,
                DistanceCalculator.calcularDistanciaManhattan(origen.getUbicacion(), destino.getUbicacion())));
        costoG.put(origen.getId(), 0.0);

        while (!listaAbierta.isEmpty()) {
            // Obtener nodo con menor f
            NodoAStar actual = listaAbierta.poll();
            int idActual = actual.getId();

            // Si llegamos al destino, reconstruir ruta
            if (idActual == destino.getId()) {
                return reconstruirRuta(padres, origen.getId(), destino.getId());
            }

            // Marcar como explorado
            listaCerrada.add(idActual);

            // Explorar vecinos
            Nodo nodoActual = getNodoPorId(idActual);
            for (Nodo vecino : nodoActual.getVecinos()) {
                int idVecino = vecino.getId();

                // Si ya exploramos este vecino, continuar
                if (listaCerrada.contains(idVecino)) {
                    continue;
                }

                // Estimar tiempo de llegada a este nodo
                double costoAcumulado = costoG.getOrDefault(idActual, 0.0);
                LocalDateTime tiempoEstimado = tiempoInicio.plusMinutes((long)(costoAcumulado * 2)); // 2 min por unidad

                // Verificar si habrá bloqueo en esta arista en el tiempo estimado
                boolean bloqueado = false;

                // Revisar varios puntos temporales
                for (int i = 0; i < 3; i++) {
                    LocalDateTime tiempo = tiempoEstimado.plusMinutes(i * 10); // Revisar actual + 10min + 20min
                    if (tiempo.isAfter(tiempoFin)) break;

                    if (estaBloqueo(nodoActual, vecino, tiempo)) {
                        bloqueado = true;
                        break;
                    }
                }

                if (bloqueado) {
                    continue; // Evitar este vecino
                }

                // Calcular costo acumulado
                double nuevoG = costoG.get(idActual) + 1;

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

        // No hay ruta viable
        return new ArrayList<>();
    }

    /**
     * Verifica si habrá bloqueos futuros en una ruta posible entre origen y destino
     */
    public boolean hayBloqueosFuturosEnRuta(
            Nodo origen,
            Nodo destino,
            LocalDateTime tiempoInicio,
            LocalDateTime tiempoFin) {

        // Implementar BFS para detectar bloqueos futuros
        Queue<Nodo> cola = new LinkedList<>();
        Set<Integer> visitados = new HashSet<>();

        cola.add(origen);
        visitados.add(origen.getId());

        // Duración de la ventana en minutos
        long minutosVentana = java.time.Duration.between(tiempoInicio, tiempoFin).toMinutes();
        int numPuntosTiempo = 3; // Verificar inicio, medio y fin

        while (!cola.isEmpty()) {
            Nodo actual = cola.poll();

            // Si llegamos al destino, terminamos
            if (actual.equals(destino)) {
                return false; // Hay al menos un camino sin bloqueos
            }

            // Explorar vecinos
            for (Nodo vecino : actual.getVecinos()) {
                if (!visitados.contains(vecino.getId())) {
                    // Verificar bloqueos en varios puntos de la ventana temporal
                    boolean bloqueado = false;

                    for (int i = 0; i < numPuntosTiempo; i++) {
                        long offsetMinutos = (i * minutosVentana) / (numPuntosTiempo - 1);
                        LocalDateTime tiempoVerificacion = tiempoInicio.plusMinutes(offsetMinutos);

                        if (estaBloqueo(actual, vecino, tiempoVerificacion)) {
                            bloqueado = true;
                            break;
                        }
                    }

                    if (!bloqueado) {
                        cola.add(vecino);
                        visitados.add(vecino.getId());
                    }
                }
            }
        }

        return true; // No se encontró un camino viable
    }
}