package pucp.edu.glp.glpdp1.algorithm.aco;

import lombok.Getter;
import lombok.Setter;
import pucp.edu.glp.glpdp1.algorithm.model.CamionAsignacion;
import pucp.edu.glp.glpdp1.algorithm.model.GrafoRutas;
import pucp.edu.glp.glpdp1.algorithm.model.Nodo;
import pucp.edu.glp.glpdp1.algorithm.model.Ruta;
import pucp.edu.glp.glpdp1.algorithm.utils.DistanceCalculator;
import pucp.edu.glp.glpdp1.algorithm.utils.UrgencyCalculator;
import pucp.edu.glp.glpdp1.domain.*;
import pucp.edu.glp.glpdp1.domain.enums.TipoAlmacen;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Representa una hormiga en el algoritmo ACO, encargada de construir una solución.
 * Implementa las reglas de decisión para la construcción de rutas y asignación de pedidos.
 */
@Getter
@Setter
public class Ant {

    private int id;
    private ACOParameters parameters;
    private Random random;

    // Posición actual de la hormiga en el grafo
    private Nodo nodoActual;

    // NUEVO: Solución guía para construcción
    private ACOSolution solucionGuia;

    private Map<String, Ubicacion> posicionesActuales;

    /**
     * Constructor
     *
     * @param id         Identificador único de la hormiga
     * @param parameters Parámetros del algoritmo
     */
    public Ant(int id, ACOParameters parameters) {
        this.id = id;
        this.parameters = parameters;
        this.random = new Random();
        this.random = new Random(System.nanoTime() + id * 1000);
    }

    /**
     * Construye una solución a partir del estado actual del sistema
     */
    public ACOSolution construirSolucionDesdeEstadoActual(
            List<Pedido> pedidos,
            List<Camion> camionesDisponibles,
            PheromoneMatrix feromonas,
            HeuristicCalculator heuristica,
            LocalDateTime tiempoActual,
            LocalDateTime horizonteFinal,
            GrafoRutas grafo,
            Map<TipoAlmacen, Double> capacidadTanques) {

        // Crear solución vacía
        ACOSolution solucion = new ACOSolution();

        Collections.shuffle(pedidos, random);
        Collections.shuffle(camionesDisponibles, random);

        // Hacer copias de trabajo
        List<Camion> camionesDisponiblesHormiga = new ArrayList<>(camionesDisponibles);
        List<Pedido> pedidosPendientes = new ArrayList<>(pedidos);
        Map<TipoAlmacen, Double> capacidadTanquesHormiga = new HashMap<>(capacidadTanques);

        // Agrupar pedidos por proximidad
        List<List<Pedido>> gruposPedidos = agruparPedidosPorProximidad(pedidosPendientes);

        // Ordenar grupos por urgencia (del más urgente al menos urgente)
        gruposPedidos.sort((g1, g2) -> {
            double urgenciaMaxG1 = g1.stream()
                    .mapToDouble(UrgencyCalculator::calcularUrgenciaNormalizada)
                    .max()
                    .orElse(0);
            double urgenciaMaxG2 = g2.stream()
                    .mapToDouble(UrgencyCalculator::calcularUrgenciaNormalizada)
                    .max()
                    .orElse(0);
            return Double.compare(urgenciaMaxG2, urgenciaMaxG1); // Orden descendente
        });

        // Asignar grupos a camiones
        for (List<Pedido> grupo : gruposPedidos) {
            // Definir ventana temporal para este grupo (limitada por el horizonte)
            LocalDateTime finVentanaTemporal = tiempoActual.plusMinutes(parameters.getVentanaTemporalMinutos());
            finVentanaTemporal = finVentanaTemporal.isBefore(horizonteFinal) ?
                    finVentanaTemporal : horizonteFinal;

            // Si no quedan camiones disponibles, los pedidos quedan sin asignar
            if (camionesDisponiblesHormiga.isEmpty()) {
                for (Pedido p : grupo) {
                    solucion.addPedidoNoAsignado(p);
                }
                continue;
            }

            // Construir rutas considerando posiciones actuales
            construirRutasDesdeEstadoActual(
                    solucion,
                    grupo,
                    camionesDisponiblesHormiga,
                    feromonas,
                    heuristica,
                    tiempoActual,
                    finVentanaTemporal,
                    grafo,
                    capacidadTanquesHormiga
            );
        }

        return solucion;
    }

    /**
     * Construye rutas optimizadas desde el estado actual del sistema
     */
    private void construirRutasDesdeEstadoActual(
            ACOSolution solucion,
            List<Pedido> grupo,
            List<Camion> camionesDisponibles,
            PheromoneMatrix feromonas,
            HeuristicCalculator heuristica,
            LocalDateTime tiempoInicio,
            LocalDateTime tiempoFin,
            GrafoRutas grafo,
            Map<TipoAlmacen, Double> capacidadTanquesHormiga) {

        // Calcular volumen total del grupo
        double volumenTotal = grupo.stream().mapToDouble(Pedido::getVolumen).sum();

        // Encontrar mejor camión para este grupo
        Camion mejorCamion = seleccionarMejorCamion(camionesDisponibles, volumenTotal);

        if (mejorCamion == null) {
            // No se pudo encontrar un camión adecuado
            for (Pedido p : grupo) {
                solucion.addPedidoNoAsignado(p);
            }
            return;
        }

        // Obtener posición actual del camión (o almacén central si es nuevo)
        Ubicacion ubicacionInicial;
        if (posicionesActuales != null && posicionesActuales.containsKey(mejorCamion.getId())) {
            ubicacionInicial = posicionesActuales.get(mejorCamion.getId());
            // Añadir debugging para confirmar que se está usando la posición correcta
            // System.out.println("🚚 Camión " + mejorCamion.getId() + " planificando desde (" +
            //        ubicacionInicial.getX() + "," + ubicacionInicial.getY() + ")");
        } else {
            ubicacionInicial = obtenerUbicacionAlmacenCentral(grafo);
            System.out.println("⚠️ No hay posición actual para " + mejorCamion.getId() +
                    ", usando almacén central");
        }

        Nodo nodoActual = grafo.obtenerNodo(ubicacionInicial);

        // Crear asignación para este camión
        CamionAsignacion asignacion = new CamionAsignacion(mejorCamion, new ArrayList<>());
        List<Ruta> rutas = new ArrayList<>();

        // Lista de pedidos por procesar
        List<Pedido> pedidosRestantes = new ArrayList<>(grupo);

        // Variables para control de estado
        double combustibleActual = mejorCamion.getGalones();
        double pesoTotal = mejorCamion.getPesoBrutoTon();

        // Calcular capacidad máxima de distancia con el combustible actual
        double distanciaMaximaPosible = (combustibleActual * 180.0) / pesoTotal;

        LocalDateTime tiempoActual = tiempoInicio;

        // Generación de rutas con ventana temporal desde posición actual
        while (!pedidosRestantes.isEmpty() && tiempoActual.isBefore(tiempoFin)) {
            // Seleccionar siguiente pedido considerando bloqueos
            Pedido siguiente = seleccionarSiguientePedidoConVentana(
                    nodoActual,
                    pedidosRestantes,
                    feromonas,
                    heuristica,
                    grafo,
                    tiempoActual,
                    tiempoFin
            );

            if (siguiente == null) break;

            // Crear ruta hasta el siguiente pedido
            Nodo nodoSiguiente = grafo.obtenerNodo(siguiente.getDestino());
            List<Nodo> caminoHastaPedido = grafo.encontrarRutaViableConBloqueos(
                    nodoActual,
                    nodoSiguiente,
                    tiempoActual,
                    tiempoFin
            );

            if (caminoHastaPedido.isEmpty()) {
                // No hay ruta viable, intentar con otro pedido
                pedidosRestantes.remove(siguiente);
                solucion.addPedidoNoAsignado(siguiente);
                continue;
            }

            // Calcular tiempo y combustible de este tramo
            double distancia = calcularDistanciaRuta(caminoHastaPedido);

            // Calcula distancia al almacén más cercano desde el destino
            Ubicacion almacenMasCercano = encontrarAlmacenMasCercano(siguiente.getDestino(), grafo);
            double distanciaVuelta = DistanceCalculator.calcularDistanciaManhattan(
                    siguiente.getDestino(), almacenMasCercano);

            // Verificar si hay suficiente combustible para ir y volver al almacén
            if (distancia + distanciaVuelta > distanciaMaximaPosible) {
                // Necesitamos reabastecimiento antes de la entrega
                Ubicacion tanqueMasConveniente = encontrarTanqueMasConveniente(
                        nodoActual.getUbicacion(),
                        siguiente.getDestino(),
                        grafo,
                        capacidadTanquesHormiga
                );

                // Crear ruta hasta el punto de reabastecimiento
                Nodo nodoTanque = grafo.obtenerNodo(tanqueMasConveniente);
                List<Nodo> caminoHastaTanque = grafo.encontrarRutaViableConBloqueos(
                        nodoActual, nodoTanque, tiempoActual, tiempoFin);

                if (caminoHastaTanque.isEmpty()) {
                    // No podemos llegar al tanque, no podemos entregar este pedido
                    pedidosRestantes.remove(siguiente);
                    solucion.addPedidoNoAsignado(siguiente);
                    continue;
                }

                // Crear ruta de reabastecimiento
                double distanciaTanque = calcularDistanciaRuta(caminoHastaTanque);
                Ruta rutaReabastecimiento = new Ruta();
                rutaReabastecimiento.setOrigen(nodoActual.getUbicacion());
                rutaReabastecimiento.setDestino(tanqueMasConveniente);
                rutaReabastecimiento.setDistancia(distanciaTanque);
                rutaReabastecimiento.setPuntoReabastecimiento(true);

                // Consumo hasta el tanque
                double consumoTanque = calcularCombustibleConsumido(distanciaTanque, pesoTotal);
                combustibleActual -= consumoTanque;
                tiempoActual = avanzarTiempo(tiempoActual, distanciaTanque);

                // Reabastecimiento
                combustibleActual = 25.0; // Tanque lleno
                distanciaMaximaPosible = (combustibleActual * 180.0) / pesoTotal;

                rutas.add(rutaReabastecimiento);
                nodoActual = nodoTanque;

                // Ahora intentamos llegar al pedido desde el tanque
                caminoHastaPedido = grafo.encontrarRutaViableConBloqueos(
                        nodoActual, nodoSiguiente, tiempoActual, tiempoFin);

                if (caminoHastaPedido.isEmpty()) {
                    pedidosRestantes.remove(siguiente);
                    solucion.addPedidoNoAsignado(siguiente);
                    continue;
                }

                distancia = calcularDistanciaRuta(caminoHastaPedido);
            }

            double consumoTramo = calcularCombustibleConsumido(distancia, pesoTotal);
            combustibleActual -= consumoTramo;
            distanciaMaximaPosible = (combustibleActual * 180.0) / pesoTotal;
            tiempoActual = avanzarTiempo(tiempoActual, distancia);

            // Crear ruta
            Ruta rutaEntrega = new Ruta();
            rutaEntrega.setOrigen(nodoActual.getUbicacion());
            rutaEntrega.setDestino(siguiente.getDestino());
            rutaEntrega.setDistancia(distancia);
            rutaEntrega.setPuntoEntrega(true);
            rutaEntrega.setPedidoEntrega(siguiente);

            // Agregar a las estructuras
            rutas.add(rutaEntrega);
            asignacion.getPedidos().add(siguiente);
            pedidosRestantes.remove(siguiente);

            // Actualizar peso después de entregar el pedido
            pesoTotal = mejorCamion.getPesoBrutoTon();
            for (Pedido p : asignacion.getPedidos()) {
                if (p != siguiente) { // Ya entregamos este
                    pesoTotal += p.getVolumen() * 0.5; // 0.5 ton por m3
                }
            }

            // Actualizar distancia máxima posible con el nuevo peso
            distanciaMaximaPosible = (combustibleActual * 180.0) / pesoTotal;

            // Actualizar nodo actual
            nodoActual = nodoSiguiente;
        }

        // Agregar ruta de regreso si hubo alguna entrega
        if (!asignacion.getPedidos().isEmpty()) {
            // Verificar si necesitamos reabastecimiento para volver
            Ubicacion almacenRegreso = encontrarAlmacenMasCercano(nodoActual.getUbicacion(), grafo);
            double distanciaRegreso = DistanceCalculator.calcularDistanciaManhattan(
                    nodoActual.getUbicacion(), almacenRegreso);

            if (distanciaRegreso > distanciaMaximaPosible) {
                // Necesitamos reabastecimiento antes de volver
                Ubicacion tanqueMasConveniente = encontrarTanqueMasConveniente(
                        nodoActual.getUbicacion(),
                        almacenRegreso,
                        grafo,
                        capacidadTanquesHormiga
                );

                Nodo nodoTanque = grafo.obtenerNodo(tanqueMasConveniente);
                List<Nodo> caminoHastaTanque = grafo.encontrarRutaViableConBloqueos(
                        nodoActual, nodoTanque, tiempoActual, tiempoFin);

                if (!caminoHastaTanque.isEmpty()) {
                    double distanciaTanque = calcularDistanciaRuta(caminoHastaTanque);
                    Ruta rutaReabastecimiento = new Ruta();
                    rutaReabastecimiento.setOrigen(nodoActual.getUbicacion());
                    rutaReabastecimiento.setDestino(tanqueMasConveniente);
                    rutaReabastecimiento.setDistancia(distanciaTanque);
                    rutaReabastecimiento.setPuntoReabastecimiento(true);

                    rutas.add(rutaReabastecimiento);
                    nodoActual = nodoTanque;
                    combustibleActual = 25.0;
                }
            }

            agregarRutaRegreso(grafo, nodoActual, rutas, tiempoActual);
            asignacion.setRutas(rutas);

            // Registrar el combustible final
            asignacion.setConsumoTotal(25.0 - combustibleActual);

            solucion.addAsignacion(asignacion);
            camionesDisponibles.remove(mejorCamion);
        } else {
            // No se pudo entregar ningún pedido
            for (Pedido p : grupo) {
                solucion.addPedidoNoAsignado(p);
            }
        }
    }

    /**
     * Selecciona el mejor camión para un grupo de pedidos
     */
    private Camion seleccionarMejorCamion(List<Camion> camionesDisponibles, double volumenTotal) {
        Camion mejorCamion = null;
        double mejorRatio = Double.MAX_VALUE;

        for (Camion camion : camionesDisponibles) {
            // Si la capacidad del camión es suficiente y mejor ajustada
            if (camion.getCargaM3() >= volumenTotal) {
                // Calcular qué tan bien se ajusta (menor es mejor)
                double ratio = camion.getCargaM3() / volumenTotal;

                // Priorizar camiones con menor combustible (RF95)
                double factorCombustible = 1.0 + (25.0 - camion.getGalones()) / 25.0;

                double valorCombinado = ratio / factorCombustible;

                if (valorCombinado < mejorRatio) {
                    mejorRatio = valorCombinado;
                    mejorCamion = camion;
                }
            }
        }

        return mejorCamion;
    }

    /**
     * Calcula el consumo de combustible basado en distancia y peso
     */
    private double calcularCombustibleConsumido(double distancia, double pesoTotal) {
        // Fórmula de consumo: Consumo [Galones] = Distancia[Km] × Peso [Ton] / 180
        return (distancia * pesoTotal) / 180.0;
    }

    /**
     * Avanza el tiempo basado en la distancia recorrida
     */
    private LocalDateTime avanzarTiempo(LocalDateTime tiempo, double distancia) {
        // Velocidad promedio en km/h
        double velocidad = parameters.getVelocidadPromedio();

        // Tiempo en horas = distancia / velocidad
        double tiempoHoras = distancia / velocidad;

        // Convertir a minutos
        long minutos = (long) (tiempoHoras * 60);

        // Añadir tiempo de descarga si es una entrega (15 minutos)
        minutos += parameters.getTiempoDescargaCliente();

        // Avanzar el tiempo
        return tiempo.plusMinutes(minutos);
    }

    /**
     * Agrega una ruta de regreso al almacén más cercano
     */
    private void agregarRutaRegreso(GrafoRutas grafo, Nodo nodoActual, List<Ruta> rutas, LocalDateTime tiempoActual) {
        // Encontrar el almacén más cercano
        Ubicacion almacenMasCercano = encontrarAlmacenMasCercano(nodoActual.getUbicacion(), grafo);
        Nodo nodoAlmacen = grafo.obtenerNodo(almacenMasCercano);

        // Encontrar ruta viable hasta el almacén
        List<Nodo> caminoRegreso = grafo.encontrarRutaViable(nodoActual, nodoAlmacen, tiempoActual);

        // Si no hay ruta viable, intentar con otros almacenes
        if (caminoRegreso.isEmpty()) {
            List<Ubicacion> otrosAlmacenes = obtenerUbicacionesAlmacenes(grafo);
            otrosAlmacenes.remove(almacenMasCercano);

            for (Ubicacion otroAlmacen : otrosAlmacenes) {
                Nodo otroNodoAlmacen = grafo.obtenerNodo(otroAlmacen);
                caminoRegreso = grafo.encontrarRutaViable(nodoActual, otroNodoAlmacen, tiempoActual);

                if (!caminoRegreso.isEmpty()) {
                    almacenMasCercano = otroAlmacen;
                    break;
                }
            }
        }

        // Si aún no hay ruta viable, usar el almacén central como default
        if (caminoRegreso.isEmpty()) {
            almacenMasCercano = obtenerUbicacionAlmacenCentral(grafo);
            // No calculamos ruta - se asumirá distancia 0
        }

        // Crear la ruta de regreso
        Ruta rutaRegreso = new Ruta();
        rutaRegreso.setOrigen(nodoActual.getUbicacion());
        rutaRegreso.setDestino(almacenMasCercano);
        rutaRegreso.setDistancia(caminoRegreso.isEmpty() ? 0 : calcularDistanciaRuta(caminoRegreso));
        rutaRegreso.setPuntoRegreso(true);

        // Añadir a la lista de rutas
        rutas.add(rutaRegreso);
    }

    /**
     * Evalúa el mejor siguiente paso posible desde un nodo
     */
    private double evaluarMejorSiguientePaso(
            Nodo nodoActual,
            List<Pedido> pedidosRestantes,
            PheromoneMatrix feromonas,
            HeuristicCalculator heuristica) {

        if (pedidosRestantes.isEmpty()) {
            return 0.0; // No hay más pasos
        }

        double mejorValoracion = 0.0;

        for (Pedido pedido : pedidosRestantes) {
            Nodo nodoPedido = pedido.getDestino() != null ?
                    new Nodo(0, pedido.getDestino()) : null;

            if (nodoPedido == null) continue;

            int idNodoActual = nodoActual.getId();
            int idNodoPedido = nodoPedido.getId();

            double valorFeromona = feromonas.getValor(idNodoActual, idNodoPedido);
            double valorHeuristica = heuristica.getValorHeuristica(idNodoActual, idNodoPedido);

            double valoracion = Math.pow(valorFeromona, parameters.getAlfa()) *
                    Math.pow(valorHeuristica, parameters.getBeta());

            if (valoracion > mejorValoracion) {
                mejorValoracion = valoracion;
            }
        }

        return mejorValoracion;
    }

    /**
     * Selecciona el siguiente pedido considerando bloqueos futuros en la ventana temporal
     */
    private Pedido seleccionarSiguientePedidoConVentana(
            Nodo nodoActual,
            List<Pedido> pedidosRestantes,
            PheromoneMatrix feromonas,
            HeuristicCalculator heuristica,
            GrafoRutas grafo,
            LocalDateTime tiempoActual,
            LocalDateTime tiempoFin) {

        if (pedidosRestantes.isEmpty()) return null;

        // 1) Identificamos candidatos viables (top 5 según heurística y feromonas)
        List<PedidoEvaluado> candidatos = new ArrayList<>();

        for (Pedido pedido : pedidosRestantes) {
            Nodo nodoPedido = grafo.obtenerNodo(pedido.getDestino());
            int idActual = nodoActual.getId();
            int idPedido = nodoPedido.getId();

            // Calcular puntuación inicial
            double valorFeromona = feromonas.getValor(idActual, idPedido);
            double valorHeuristica = heuristica.getValorHeuristica(idActual, idPedido);
            double urgencia = UrgencyCalculator.calcularUrgenciaNormalizada(pedido);

            double puntuacionBase = Math.pow(valorFeromona, parameters.getAlfa()) *
                    Math.pow(valorHeuristica, parameters.getBeta()) *
                    (1.0 + urgencia * parameters.getFactorPriorizacionUrgencia());

            // 2) Detectar si habrá bloqueos en la ventana temporal
            boolean hayBloqueosFuturos = detectarBloqueosFuturos(grafo, nodoActual, nodoPedido, tiempoActual, tiempoFin);

            // Penalizar fuertemente si hay bloqueos futuros
            if (hayBloqueosFuturos) {
                puntuacionBase *= 0.1; // Reducción del 90%
            }

            // 3) Simular el siguiente paso (lookahead de un nivel)
            double puntuacionConLookahead = puntuacionBase;
            if (pedidosRestantes.size() > 1 && !hayBloqueosFuturos) {
                // Simular la entrega de este pedido y evaluar el siguiente mejor paso
                List<Pedido> pedidosRestantesSim = new ArrayList<>(pedidosRestantes);
                pedidosRestantesSim.remove(pedido);

                double mejorSiguientePuntuacion = evaluarMejorSiguientePaso(nodoPedido, pedidosRestantesSim, feromonas, heuristica);

                // Combinar puntuación actual con lookahead (70% acutal, 30% futuro)
                puntuacionConLookahead = 0.7 * puntuacionBase + 0.3 * mejorSiguientePuntuacion;
            }

            candidatos.add(new PedidoEvaluado(pedido, puntuacionConLookahead));
        }

        // Ordenar candidatos por puntuación (mayor a menor)
        candidatos.sort((c1, c2) -> Double.compare(c2.getPuntuacion(), c1.getPuntuacion()));

        // 4) Seleccionar probabilísticamente entre los mejores candidatos
        double q = random.nextDouble();
        if (q < parameters.getQ0()) {
            // Explotación: elegir el mejor
            return candidatos.get(0).getPedido();
        } else {
            // Exploración: selección proporcional
            double total = candidatos.stream().mapToDouble(PedidoEvaluado::getPuntuacion).sum();
            double seleccion = random.nextDouble() * total;
            double acumulado = 0;

            for (PedidoEvaluado candidato : candidatos) {
                acumulado += candidato.getPuntuacion();
                if (acumulado >= seleccion) {
                    return candidato.getPedido();
                }
            }

            // Si por algún error numérico no se seleccionó ninguno
            return candidatos.get(0).getPedido();
        }
    }

    /**
     * Detecta si habrá bloqueos entre dos nodos en una ventana temporal futura
     */
    private boolean detectarBloqueosFuturos(
            GrafoRutas grafo,
            Nodo origen,
            Nodo destino,
            LocalDateTime tiempoInicio,
            LocalDateTime tiempoFin) {

        // Encontrar la ruta más probable entre origen y destino
        List<Nodo> rutaEstimada = encontrarRutaEstimada(grafo, origen, destino);

        if (rutaEstimada.isEmpty()) {
            return true; // No hay ruta viable
        }

        // Dividir la ventana temporal en intervalos
        long minutosVentana = Math.max(1, Duration.between(tiempoInicio, tiempoFin).toMinutes());
        int numIntervalos = Math.max(2, Math.min(5, (int)minutosVentana));

        LocalDateTime[] tiemposVerificacion = new LocalDateTime[numIntervalos];
        for (int i = 0; i < numIntervalos; i++) {
            long offset = (minutosVentana * i) / (numIntervalos - 1);
            tiemposVerificacion[i] = tiempoInicio.plusMinutes(offset);
        }

        // Verificar bloqueos en cada intervalo de tiempo
        for (LocalDateTime tiempo : tiemposVerificacion) {
            // Verificar cada tramo de la ruta estimada
            for (int i = 0; i < rutaEstimada.size() - 1; i++) {
                Nodo n1 = rutaEstimada.get(i);
                Nodo n2 = rutaEstimada.get(i + 1);

                if (hayBloqueoEntre(grafo, n1, n2, tiempo)) {
                    return true; // Hay bloqueo en este intervalo
                }
            }
        }

        return false; // No se detectaron bloqueos
    }

    /**
     * Encuentra una ruta estimada entre dos nodos (puede ser subóptima)
     */
    private List<Nodo> encontrarRutaEstimada(GrafoRutas grafo, Nodo origen, Nodo destino) {
        // Implementar una búsqueda de camino simplificada
        return grafo.encontrarRutaViable(origen, destino, LocalDateTime.now());
    }

    /**
     * Verifica si hay un bloqueo entre dos nodos adyacentes en un momento dado
     */
    private boolean hayBloqueoEntre(GrafoRutas grafo, Nodo n1, Nodo n2, LocalDateTime tiempo) {
        return grafo.estaBloqueo(n1, n2, tiempo);
    }

    /**
     * RF85: Implementa agrupamiento inteligente de pedidos por proximidad
     */
    protected List<List<Pedido>> agruparPedidosPorProximidad(List<Pedido> pedidos) {
        List<List<Pedido>> grupos = new ArrayList<>();
        Set<Pedido> pedidosAgrupados = new HashSet<>();

        double umbralAjustado = parameters.getUmbralDistanciaPedidosCercanos() * (0.9 + 0.2 * random.nextDouble());

        for (Pedido semilla : pedidos) {
            if (pedidosAgrupados.contains(semilla)) {
                continue;
            }

            // Crear nuevo grupo con la semilla
            List<Pedido> grupo = new ArrayList<>();
            grupo.add(semilla);
            pedidosAgrupados.add(semilla);

            double volumenAcumulado = semilla.getVolumen();

            // Buscar pedidos cercanos
            List<Pedido> pedidosCercanos = new ArrayList<>();
            for (Pedido p : pedidos) {
                if (!pedidosAgrupados.contains(p) && p != semilla) {
                    double distancia = DistanceCalculator.calcularDistanciaManhattan(
                            semilla.getDestino(), p.getDestino());

                    if (distancia < umbralAjustado) {
                        pedidosCercanos.add(p);
                    }
                }
            }

            // Ordenar pedidos cercanos por distancia
            pedidosCercanos.sort((p1, p2) -> {
                double d1 = DistanceCalculator.calcularDistanciaManhattan(semilla.getDestino(), p1.getDestino());
                double d2 = DistanceCalculator.calcularDistanciaManhattan(semilla.getDestino(), p2.getDestino());
                return Double.compare(d1, d2);
            });

            double capacidadMaxima = 15.0;

            // Añadir hasta N pedidos más cercanos (o todos si hay menos)
            int maxAdicionales = Math.min(parameters.getMaxPedidosPorGrupo() - 1, pedidosCercanos.size());
            for (int i = 0; i < maxAdicionales; i++) {
                Pedido pedidoCercano = pedidosCercanos.get(i);
                if (volumenAcumulado + pedidoCercano.getVolumen() <= capacidadMaxima) {
                    grupo.add(pedidoCercano);
                    pedidosAgrupados.add(pedidoCercano);
                    volumenAcumulado += pedidoCercano.getVolumen();
                }
            }
            grupos.add(grupo);
        }

        return grupos;
    }

    /**
     * Encuentra el almacén más cercano a una ubicación
     */
    private Ubicacion encontrarAlmacenMasCercano(Ubicacion ubicacion, GrafoRutas grafo) {
        List<Ubicacion> ubicacionesAlmacenes = obtenerUbicacionesAlmacenes(grafo);

        Ubicacion masCercano = null;
        double distanciaMinima = Double.MAX_VALUE;

        for (Ubicacion ubicacionAlmacen : ubicacionesAlmacenes) {
            double distancia = DistanceCalculator.calcularDistanciaManhattan(
                    ubicacion, ubicacionAlmacen);

            if (distancia < distanciaMinima) {
                distanciaMinima = distancia;
                masCercano = ubicacionAlmacen;
            }
        }

        return masCercano;
    }

    /**
     * Obtiene las ubicaciones de todos los almacenes
     */
    private List<Ubicacion> obtenerUbicacionesAlmacenes(GrafoRutas grafo) {
        return grafo.getAlmacenes().stream()
                .map(Almacen::getUbicacion)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene la ubicación del almacén central
     */
    private Ubicacion obtenerUbicacionAlmacenCentral(GrafoRutas grafo) {
        for (Almacen almacen : grafo.getAlmacenes()) {
            if (almacen.getTipoAlmacen() == TipoAlmacen.CENTRAL) {
                return almacen.getUbicacion();
            }
        }
        return null;
    }

    /**
     * Obtiene el tipo de almacén según ubicación
     */
    private TipoAlmacen obtenerTipoAlmacen(Ubicacion ubicacion, GrafoRutas grafo) {
        for (Almacen almacen : grafo.getAlmacenes()) {
            if (ubicacion.getX() == almacen.getUbicacion().getX() &&
                    ubicacion.getY() == almacen.getUbicacion().getY()) {
                return almacen.getTipoAlmacen();
            }
        }
        return null;
    }

    /**
     * Calcula la distancia total de una ruta (lista de nodos)
     */
    private double calcularDistanciaRuta(List<Nodo> nodos) {
        double distancia = 0;
        for (int i = 0; i < nodos.size() - 1; i++) {
            distancia += DistanceCalculator.calcularDistanciaManhattan(
                    nodos.get(i).getUbicacion(),
                    nodos.get(i + 1).getUbicacion());
        }
        return distancia;
    }


    /**
     * RF86: Encuentra el tanque más conveniente para reabastecimiento
     * Considera la desviación de ruta, capacidad disponible y conveniencia
     * @param ubicacionActual Posición actual del camión
     * @param ubicacionDestino Destino final hacia donde se dirige
     * @param grafo Grafo con información de la ciudad
     * @param capacidadTanquesHormiga Mapa con capacidades actuales de tanques
     * @return Ubicación del punto de reabastecimiento más conveniente
     */
    private Ubicacion encontrarTanqueMasConveniente(
            Ubicacion ubicacionActual,
            Ubicacion ubicacionDestino,
            GrafoRutas grafo,
            Map<TipoAlmacen, Double> capacidadTanquesHormiga) {

        // Obtener ubicaciones de todos los almacenes
        List<Ubicacion> ubicacionesAlmacenes = obtenerUbicacionesAlmacenes(grafo);

        // Referencia al almacén central para usar como respaldo
        Ubicacion almacenCentral = null;

        // Mapa para guardar puntuaciones de cada tanque (menor es mejor)
        Map<Ubicacion, Double> puntuaciones = new HashMap<>();

        // Evaluar cada posible punto de reabastecimiento
        for (Ubicacion ubicacionAlmacen : ubicacionesAlmacenes) {
            TipoAlmacen tipoAlmacen = obtenerTipoAlmacen(ubicacionAlmacen, grafo);

            if (tipoAlmacen == TipoAlmacen.CENTRAL) {
                almacenCentral = ubicacionAlmacen;
                continue; // Evaluar el almacén central al final si es necesario
            }

            // RF88: Verificar si el tanque intermedio tiene capacidad suficiente
            Double capacidadDisponible = capacidadTanquesHormiga.get(tipoAlmacen);
            if (capacidadDisponible == null || capacidadDisponible < parameters.getCapacidadMinimaReabastecimiento()) {
                // Almacén sin capacidad disponible o suficiente, saltar
                System.out.println("⚠️ Tanque " + tipoAlmacen + " sin capacidad suficiente: " +
                        (capacidadDisponible != null ? capacidadDisponible : 0) + "m³");
                continue;
            }

            // Calcular desviación que implica ir al tanque respecto a ruta directa
            double distanciaDirecta = DistanceCalculator.calcularDistanciaManhattan(
                    ubicacionActual, ubicacionDestino);

            double distanciaConTanque = DistanceCalculator.calcularDistanciaManhattan(
                    ubicacionActual, ubicacionAlmacen) +
                    DistanceCalculator.calcularDistanciaManhattan(
                            ubicacionAlmacen, ubicacionDestino);

            double desviacion = distanciaConTanque - distanciaDirecta;

            // Considerar distancia al tanque (priorizar tanques cercanos)
            double distanciaAlTanque = DistanceCalculator.calcularDistanciaManhattan(
                    ubicacionActual, ubicacionAlmacen);

            // RF86: Factor de priorización de tanques intermedios
            // Calcular puntuación (menor es mejor)
            double factorCapacidad = capacidadDisponible / 160.0; // Normalizar entre 0-1
            double factorDistancia = 1.0 / (1.0 + distanciaAlTanque / 20.0); // Favorece tanques cercanos

            // Fórmula de puntuación: menor desviación y mayor capacidad es mejor
            double puntuacion = desviacion / (factorCapacidad * parameters.getFactorPriorizacionTanques() * factorDistancia);

            // Añadir bono si el tanque está en la dirección del destino
            if (estaEnDireccionDestino(ubicacionActual, ubicacionAlmacen, ubicacionDestino)) {
                puntuacion *= 0.8; // 20% de mejora en puntuación
            }

            puntuaciones.put(ubicacionAlmacen, puntuacion);
            System.out.println("🔍 Evaluando tanque en (" + ubicacionAlmacen.getX() + "," +
                    ubicacionAlmacen.getY() + ") - Puntuación: " + String.format("%.2f", puntuacion));
        }

        // Si no hay tanques intermedios disponibles o son muy inconvenientes, evaluar almacén central
        if (almacenCentral != null) {
            double distanciaDirecta = DistanceCalculator.calcularDistanciaManhattan(
                    ubicacionActual, ubicacionDestino);

            double distanciaConCentral = DistanceCalculator.calcularDistanciaManhattan(
                    ubicacionActual, almacenCentral) +
                    DistanceCalculator.calcularDistanciaManhattan(
                            almacenCentral, ubicacionDestino);

            double desviacion = distanciaConCentral - distanciaDirecta;
            double distanciaAlCentral = DistanceCalculator.calcularDistanciaManhattan(
                    ubicacionActual, almacenCentral);

            // El almacén central siempre tiene capacidad, pero puede estar más lejos
            double puntuacion = desviacion / (1.0 / (1.0 + distanciaAlCentral / 20.0));

            // Añadimos el almacén central a las opciones
            puntuaciones.put(almacenCentral, puntuacion);
            System.out.println("🔍 Evaluando almacén central - Puntuación: " + String.format("%.2f", puntuacion));
        }

        // Encontrar el tanque con mejor puntuación (menor valor)
        Ubicacion mejorTanque = null;
        double mejorPuntuacion = Double.MAX_VALUE;

        for (Map.Entry<Ubicacion, Double> entrada : puntuaciones.entrySet()) {
            if (entrada.getValue() < mejorPuntuacion) {
                mejorPuntuacion = entrada.getValue();
                mejorTanque = entrada.getKey();
            }
        }

        // Verificación final
        if (mejorTanque == null && almacenCentral != null) {
            System.out.println("ℹ️ No se encontró tanque óptimo, usando almacén central por defecto");
            return almacenCentral;
        } else if (mejorTanque != null) {
            TipoAlmacen tipo = obtenerTipoAlmacen(mejorTanque, grafo);
            System.out.println("✅ Tanque seleccionado: " + tipo + " en (" +
                    mejorTanque.getX() + "," + mejorTanque.getY() + ")");
            return mejorTanque;
        }

        // No deberíamos llegar aquí pero por seguridad:
        System.out.println("⚠️ ERROR: No se encontró ningún tanque, usando posición (0,0)");
        return new Ubicacion(0, 0);
    }

    /**
     * Determina si un punto intermedio está en la dirección general del destino
     * @param origen Punto de origen
     * @param punto Punto intermedio a evaluar
     * @param destino Punto de destino final
     * @return true si el punto está en dirección al destino
     */
    private boolean estaEnDireccionDestino(Ubicacion origen, Ubicacion punto, Ubicacion destino) {
        // Vector de origen a destino
        int vx = destino.getX() - origen.getX();
        int vy = destino.getY() - origen.getY();

        // Vector de origen a punto
        int px = punto.getX() - origen.getX();
        int py = punto.getY() - origen.getY();

        // Si los componentes tienen el mismo signo, están en la misma dirección
        boolean mismaX = (vx >= 0 && px >= 0) || (vx <= 0 && px <= 0);
        boolean mismaY = (vy >= 0 && py >= 0) || (vy <= 0 && py <= 0);

        // Si al menos una componente está en la misma dirección y
        // el punto no está más lejos que el destino, considerarlo en dirección
        boolean dentroDeDistancia = Math.abs(px) <= Math.abs(vx) && Math.abs(py) <= Math.abs(vy);

        return (mismaX || mismaY) && dentroDeDistancia;
    }

    /**
     * Verifica si un camión necesita reabastecimiento para un viaje
     * @param camion El camión a evaluar
     * @param distancia Distancia total del viaje en km
     * @param pesoTotal Peso total incluyendo carga en toneladas
     * @return true si necesita reabastecimiento
     */
    private boolean necesitaReabastecimiento(Camion camion, double distancia, double pesoTotal) {
        // Calcular consumo estimado para la distancia
        double consumoEstimado = (distancia * pesoTotal) / 180.0;

        // Calcular distancia máxima posible con el combustible actual
        double distanciaMaxima = (camion.getGalones() * 180.0) / pesoTotal;

        // Verificar si el combustible es suficiente para recorrer la distancia
        // con un margen de seguridad de 10%
        return consumoEstimado > (camion.getGalones() * 0.9);
    }


    /**
     * Clase auxiliar para evaluar pedidos
     */
    @Getter
    @Setter
    private static class PedidoEvaluado {
        private Pedido pedido;
        private double puntuacion;

        PedidoEvaluado(Pedido pedido, double puntuacion) {
            this.pedido = pedido;
            this.puntuacion = puntuacion;
        }
    }
}