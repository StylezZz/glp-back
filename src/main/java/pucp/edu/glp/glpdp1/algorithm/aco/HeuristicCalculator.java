package pucp.edu.glp.glpdp1.algorithm.aco;

import lombok.Getter;
import lombok.Setter;
import pucp.edu.glp.glpdp1.algorithm.model.GrafoRutas;
import pucp.edu.glp.glpdp1.algorithm.model.Nodo;
import pucp.edu.glp.glpdp1.algorithm.utils.DistanceCalculator;
import pucp.edu.glp.glpdp1.algorithm.utils.UrgencyCalculator;
import pucp.edu.glp.glpdp1.domain.Bloqueo;
import pucp.edu.glp.glpdp1.domain.Pedido;
import pucp.edu.glp.glpdp1.domain.Ubicacion;
import pucp.edu.glp.glpdp1.domain.enums.TipoAlmacen;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Calcula y mantiene la matriz de heurística para el algoritmo ACO.
 * Incluye funcionalidad para actualizar la heurística dinámicamente según
 * bloqueos, pedidos urgentes y otros factores.
 */
@Getter
@Setter
public class HeuristicCalculator {

    private double[][] matrizHeuristicaBase;
    private double[][] matrizHeuristicaActual;
    private GrafoRutas grafo;
    private ACOParameters parameters;
    private int tamanio;

    /**
     * Constructor
     * @param grafo Grafo de la ciudad
     * @param parameters Parámetros del algoritmo
     */
    public HeuristicCalculator(GrafoRutas grafo, ACOParameters parameters) {
        this.grafo = grafo;
        this.parameters = parameters;
        this.tamanio = grafo.getTotalNodos();
        inicializarMatrizHeuristica();
    }

    /**
     * Inicializa la matriz de heurística base con el inverso de las distancias
     */
    private void inicializarMatrizHeuristica() {
        matrizHeuristicaBase = new double[tamanio][tamanio];
        matrizHeuristicaActual = new double[tamanio][tamanio];

        // Inicializar con heurística basada en distancia Manhattan (inverso)
        for (int i = 0; i < tamanio; i++) {
            Nodo origen = grafo.getNodoPorId(i);
            if (origen == null) continue;

            for (int j = 0; j < tamanio; j++) {
                Nodo destino = grafo.getNodoPorId(j);
                if (destino == null) continue;

                // La heurística base es el inverso de la distancia
                double distancia = DistanceCalculator.calcularDistanciaManhattan(
                        origen.getUbicacion(), destino.getUbicacion());

                // Evitar división por cero
                double heuristica = (distancia > 0) ? 1.0 / distancia : 1.0;

                matrizHeuristicaBase[i][j] = heuristica;
                matrizHeuristicaActual[i][j] = heuristica;
            }
        }
    }

    /**
     * Obtiene el valor de la heurística actual entre dos nodos
     * @param origen ID del nodo origen
     * @param destino ID del nodo destino
     * @return Valor de la heurística
     */
    public double getValorHeuristica(int origen, int destino) {
        if (origen >= 0 && origen < tamanio && destino >= 0 && destino < tamanio) {
            return matrizHeuristicaActual[origen][destino];
        }
        return 0.0;
    }

    /**
     * Actualiza la matriz de heurística según condiciones dinámicas
     * Implementa RF86, RF88, RF98
     */
    public void actualizarHeuristicaDinamica(
            List<Pedido> pedidos,
            List<Bloqueo> bloqueos,
            LocalDateTime tiempoActual,
            Map<TipoAlmacen, Double> capacidadTanques) {

        // Copiar la matriz base como punto de partida
        for (int i = 0; i < tamanio; i++) {
            System.arraycopy(matrizHeuristicaBase[i], 0, matrizHeuristicaActual[i], 0, tamanio);
        }

        // 1. Ajustar según bloqueos actuales
        actualizarHeuristicaPorBloqueos(bloqueos, tiempoActual);

        // 2. Ajustar según urgencia de pedidos
        actualizarHeuristicaPorUrgenciaPedidos(pedidos);

        // 3. RF86: Priorizar uso de tanques intermedios
        actualizarHeuristicaPorTanquesIntermedios(capacidadTanques);

        // 4. RF98: Optimización de secuencia para minimizar viajes en vacío
        actualizarHeuristicaParaMinimizarViajesVacios(pedidos);
    }

    /**
     * Actualiza la heurística reduciendo valores para tramos bloqueados
     */
    private void actualizarHeuristicaPorBloqueos(List<Bloqueo> bloqueos, LocalDateTime tiempoActual) {
        for (Bloqueo bloqueo : bloqueos) {
            // Verificar si el bloqueo está activo en este momento
            if (tiempoActual.isAfter(bloqueo.getFechaInicio()) &&
                    tiempoActual.isBefore(bloqueo.getFechaFinal())) {

                // Recorrer tramos bloqueados
                List<Ubicacion> tramos = bloqueo.getTramos();

                // Para cada par de ubicaciones adyacentes en el tramo
                for (int i = 0; i < tramos.size() - 1; i++) {
                    Ubicacion u1 = tramos.get(i);
                    Ubicacion u2 = tramos.get(i + 1);

                    Nodo n1 = grafo.obtenerNodo(u1);
                    Nodo n2 = grafo.obtenerNodo(u2);

                    if (n1 != null && n2 != null) {
                        // Reducir drásticamente la heurística para estos tramos
                        matrizHeuristicaActual[n1.getId()][n2.getId()] = 0.0001;
                        matrizHeuristicaActual[n2.getId()][n1.getId()] = 0.0001;
                    }
                }
            }
        }
    }

    /**
     * Actualiza la heurística aumentando valores hacia pedidos urgentes
     */
    private void actualizarHeuristicaPorUrgenciaPedidos(List<Pedido> pedidos) {
        for (Pedido pedido : pedidos) {
            // Calcular la urgencia normalizada (0-1)
            double urgencia = UrgencyCalculator.calcularUrgenciaNormalizada(pedido);

            // Solo priorizar pedidos con cierta urgencia
            if (urgencia > 0.3) {
                Nodo nodoPedido = grafo.obtenerNodo(pedido.getDestino());

                if (nodoPedido != null) {
                    int idPedido = nodoPedido.getId();

                    // Radio de influencia en nodos cercanos
                    for (int i = 0; i < tamanio; i++) {
                        Nodo nodoOrigen = grafo.getNodoPorId(i);
                        if (nodoOrigen == null) continue;

                        // Calcular distancia al pedido
                        double distancia = DistanceCalculator.calcularDistanciaManhattan(
                                nodoOrigen.getUbicacion(), pedido.getDestino());

                        // Sólo influir en nodos dentro de cierto radio (más cercanos)
                        if (distancia < 15) {
                            // Para cada posible nodo destino
                            for (int j = 0; j < tamanio; j++) {
                                Nodo nodoDestino = grafo.getNodoPorId(j);
                                if (nodoDestino == null) continue;

                                // Si el destino está más cerca del pedido que el origen
                                double distanciaDestinoPedido = DistanceCalculator.calcularDistanciaManhattan(
                                        nodoDestino.getUbicacion(), pedido.getDestino());

                                if (distanciaDestinoPedido < distancia) {
                                    // Aumentar heurística en dirección al pedido urgente
                                    double factor = 1.0 + urgencia * parameters.getFactorPriorizacionUrgencia();
                                    matrizHeuristicaActual[i][j] *= factor;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * RF86: Actualiza la heurística para priorizar uso de tanques intermedios
     */
    private void actualizarHeuristicaPorTanquesIntermedios(Map<TipoAlmacen, Double> capacidadTanques) {
        for (Map.Entry<TipoAlmacen, Double> entrada : capacidadTanques.entrySet()) {
            TipoAlmacen tipo = entrada.getKey();
            double capacidad = entrada.getValue();

            // Solo considerar tanques intermedios con capacidad disponible
            if (tipo != TipoAlmacen.CENTRAL && capacidad >= parameters.getCapacidadMinimaReabastecimiento()) {
                // Encontrar ubicación del tanque
                Ubicacion ubicacionTanque = grafo.obtenerUbicacionAlmacen(tipo);

                if (ubicacionTanque != null) {
                    Nodo nodoTanque = grafo.obtenerNodo(ubicacionTanque);

                    if (nodoTanque != null) {
                        int idTanque = nodoTanque.getId();

                        // Radio de influencia para priorización
                        for (int i = 0; i < tamanio; i++) {
                            Nodo nodoOrigen = grafo.getNodoPorId(i);
                            if (nodoOrigen == null) continue;

                            // Calcular distancia al tanque
                            double distancia = DistanceCalculator.calcularDistanciaManhattan(
                                    nodoOrigen.getUbicacion(), ubicacionTanque);

                            // Solo influir en nodos dentro de cierto radio
                            if (distancia < 15) {
                                // Para cada posible nodo destino
                                for (int j = 0; j < tamanio; j++) {
                                    Nodo nodoDestino = grafo.getNodoPorId(j);
                                    if (nodoDestino == null) continue;

                                    // Si el destino está más cerca del tanque que el origen
                                    double distanciaDestinoTanque = DistanceCalculator.calcularDistanciaManhattan(
                                            nodoDestino.getUbicacion(), ubicacionTanque);

                                    if (distanciaDestinoTanque < distancia) {
                                        // Factor de priorización según capacidad
                                        double factorCapacidad = capacidad / 160.0; // Normalizada
                                        double factor = 1.0 + factorCapacidad * parameters.getFactorPriorizacionTanques();

                                        matrizHeuristicaActual[i][j] *= factor;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * RF98: Actualiza la heurística para minimizar viajes en vacío
     */
    private void actualizarHeuristicaParaMinimizarViajesVacios(List<Pedido> pedidos) {
        // Identificar pares de pedidos que podrían encadenarse eficientemente
        for (int i = 0; i < pedidos.size(); i++) {
            Pedido p1 = pedidos.get(i);

            for (int j = i + 1; j < pedidos.size(); j++) {
                Pedido p2 = pedidos.get(j);

                // Calcular distancia entre pedidos
                double distancia = DistanceCalculator.calcularDistanciaManhattan(
                        p1.getDestino(), p2.getDestino());

                // Si están lo suficientemente cerca, reforzar camino entre ellos
                if (distancia < parameters.getUmbralDistanciaPedidosCercanos()) {
                    Nodo nodoP1 = grafo.obtenerNodo(p1.getDestino());
                    Nodo nodoP2 = grafo.obtenerNodo(p2.getDestino());

                    if (nodoP1 != null && nodoP2 != null) {
                        int idP1 = nodoP1.getId();
                        int idP2 = nodoP2.getId();

                        // Calcular factor basado en la cercanía y urgencia combinada
                        double urgenciaP1 = UrgencyCalculator.calcularUrgenciaNormalizada(p1);
                        double urgenciaP2 = UrgencyCalculator.calcularUrgenciaNormalizada(p2);
                        double urgenciaPromedio = (urgenciaP1 + urgenciaP2) / 2;

                        // Calcular factor de cercanía (más cerca = mayor factor)
                        double factorCercania = 1.0 + (parameters.getUmbralDistanciaPedidosCercanos() - distancia)
                                / parameters.getUmbralDistanciaPedidosCercanos();

                        // Combinar factores
                        double factorTotal = 1.0 + factorCercania * (1.0 + urgenciaPromedio);

                        // Aplicar al camino directo entre ambos pedidos
                        reforzarCaminoEntrePuntos(idP1, idP2, factorTotal);
                    }
                }
            }
        }
    }

    /**
     * Refuerza la heurística en el camino entre dos puntos para minimizar viajes en vacío
     */
    private void reforzarCaminoEntrePuntos(int origen, int destino, double factor) {
        // Obtener puntos intermedios en línea recta aproximada
        Nodo nodoOrigen = grafo.getNodoPorId(origen);
        Nodo nodoDestino = grafo.getNodoPorId(destino);

        if (nodoOrigen == null || nodoDestino == null) {
            return;
        }

        // Calcular distancia Manhattan entre origen y destino
        int dx = Math.abs(nodoDestino.getUbicacion().getX() - nodoOrigen.getUbicacion().getX());
        int dy = Math.abs(nodoDestino.getUbicacion().getY() - nodoOrigen.getUbicacion().getY());

        // Reforzar camino directo
        matrizHeuristicaActual[origen][destino] *= factor;
        matrizHeuristicaActual[destino][origen] *= factor;

        // Reforzar caminos intermedios en la ruta aproximada
        int xActual = nodoOrigen.getUbicacion().getX();
        int yActual = nodoOrigen.getUbicacion().getY();

        int xDestino = nodoDestino.getUbicacion().getX();
        int yDestino = nodoDestino.getUbicacion().getY();

        // Determinar dirección
        int xDir = Integer.compare(xDestino, xActual);
        int yDir = Integer.compare(yDestino, yActual);

        // Reforzar nodos en el camino recto aproximado
        while (xActual != xDestino || yActual != yDestino) {
            // Decidir si moverse en X o en Y
            if (Math.abs(xActual - xDestino) > Math.abs(yActual - yDestino)) {
                xActual += xDir;
            } else {
                yActual += yDir;
            }

            // Buscar el nodo correspondiente a esta ubicación
            Ubicacion ubicacion = new Ubicacion(xActual, yActual);
            Nodo nodoIntermedio = grafo.obtenerNodo(ubicacion);

            if (nodoIntermedio != null) {
                int idIntermedio = nodoIntermedio.getId();

                // Reforzar conexiones con nodos anterior y siguiente en la ruta
                if (xActual != xDestino || yActual != yDestino) {
                    int xSiguiente, ySiguiente;

                    // Decidir el siguiente paso
                    if (Math.abs(xActual - xDestino) > Math.abs(yActual - yDestino)) {
                        xSiguiente = xActual + xDir;
                        ySiguiente = yActual;
                    } else {
                        xSiguiente = xActual;
                        ySiguiente = yActual + yDir;
                    }

                    Ubicacion ubicacionSiguiente = new Ubicacion(xSiguiente, ySiguiente);
                    Nodo nodoSiguiente = grafo.obtenerNodo(ubicacionSiguiente);

                    if (nodoSiguiente != null) {
                        int idSiguiente = nodoSiguiente.getId();
                        matrizHeuristicaActual[idIntermedio][idSiguiente] *= factor;
                        matrizHeuristicaActual[idSiguiente][idIntermedio] *= factor;
                    }
                }
            }
        }
    }
}