package pucp.edu.glp.glpdp1.algorithm.aco;

import lombok.Getter;
import lombok.Setter;
import pucp.edu.glp.glpdp1.algorithm.model.CamionAsignacion;
import pucp.edu.glp.glpdp1.algorithm.model.GrafoRutas;
import pucp.edu.glp.glpdp1.algorithm.model.Ruta;
import pucp.edu.glp.glpdp1.algorithm.utils.ACOLogger;
import pucp.edu.glp.glpdp1.algorithm.utils.ACOMonitor;
import pucp.edu.glp.glpdp1.algorithm.utils.AlgorithmUtils;
import pucp.edu.glp.glpdp1.domain.Almacen;
import pucp.edu.glp.glpdp1.domain.Averia;
import pucp.edu.glp.glpdp1.domain.Bloqueo;
import pucp.edu.glp.glpdp1.domain.Camion;
import pucp.edu.glp.glpdp1.domain.Mapa;
import pucp.edu.glp.glpdp1.domain.Pedido;
import pucp.edu.glp.glpdp1.domain.Rutas;
import pucp.edu.glp.glpdp1.domain.Ubicacion;
import pucp.edu.glp.glpdp1.domain.enums.EstadoCamion;
import pucp.edu.glp.glpdp1.domain.enums.TipoAlmacen;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Implementación del algoritmo de Colonia de Hormigas (ACO) para optimización de rutas
 * de distribución de GLP.
 */
@Getter
@Setter
public class ACOAlgorithm {
    private static final Logger logger = Logger.getLogger(ACOAlgorithm.class.getName());

    // Parámetros
    private ACOParameters parameters;

    // Estado del sistema
    private Mapa mapa;
    private GrafoRutas grafo;
    private PheromoneMatrix pheromonesMatrix;
    private HeuristicCalculator heuristicCalculator;
    private AntColony colony;

    // Control de ejecución
    private int iteracion;
    private int iterSinMejora;
    private ACOSolution mejorSolucionGlobal;
    private double mejorCalidadGlobal;
    private double mejorCalidadAnterior;
    private LocalDateTime ultimaReplanificacion;
    private int frecuenciaReplanificacion;
    private boolean estadoColapso;
    private ACOLogger loggerACO;
    private ACOMonitor monitor;
    private long tiempoInicioEjecucion;

    // Estructuras para el control de tanques intermedios
    private Map<TipoAlmacen, Double> capacidadActualTanques;

    // Búsqueda local
    private boolean busquedaLocalActiva = true;
    // Historial de mejores soluciones por iteración
    private List<ACOSolution> historicoSoluciones = new ArrayList<>();
    // Matriz para almacenar frecuencia de uso de aristas en buenas soluciones
    private int[][] matrizFrecuenciaAristas;
    // Factor para controlar influencia del aprendizaje entre iteraciones
    private double factorAprendizaje = 0.3;
    // Contador de iteraciones sin mejora global (más estricto que iterSinMejora)
    private int iteracionesSinMejoraGlobal;

    // Factor de evaporación entre planificaciones
    private double factorEvaporacionEntrePlanificaciones = 0.5;

    private Set<Integer> pedidosProcesados = new HashSet<>();

    /**
     * Constructor principal del algoritmo
     *
     * @param mapa Mapa con los datos de la ciudad, flota, pedidos, etc.
     */
    public ACOAlgorithm(Mapa mapa) {
        this.mapa = mapa;
        this.parameters = new ACOParameters();
        inicializarAlgoritmo();
    }

    /**
     * Constructor con parámetros personalizados
     *
     * @param mapa       Mapa con los datos de la ciudad, flota, pedidos, etc.
     * @param parameters Parámetros personalizados del algoritmo
     */
    public ACOAlgorithm(Mapa mapa, ACOParameters parameters) {
        this.mapa = mapa;
        this.parameters = parameters;
        inicializarAlgoritmo();
    }

    private void debug(String mensaje) {
        System.out.println("[ACO] " + mensaje);
    }

    /**
     * Inicializa las estructuras de datos necesarias para el algoritmo
     */
    private void inicializarAlgoritmo() {
        // Inicializar el grafo
        this.grafo = new GrafoRutas(mapa.getAncho(), mapa.getAlto(), mapa.getAlmacenes());

        // Inicializar matriz de feromonas y calculador de heurística
        this.pheromonesMatrix = new PheromoneMatrix(grafo.getTotalNodos(), parameters.getFeromonaInicial());
        this.heuristicCalculator = new HeuristicCalculator(grafo, parameters);

        // Inicializar la colonia de hormigas
        this.colony = new AntColony(parameters.getNumeroHormigas(), parameters, grafo);

        // Inicializar variables de control
        this.iteracion = 0;
        this.iterSinMejora = 0;
        this.mejorSolucionGlobal = null;
        this.mejorCalidadGlobal = Double.NEGATIVE_INFINITY;
        this.mejorCalidadAnterior = Double.NEGATIVE_INFINITY;
        this.ultimaReplanificacion = LocalDateTime.now();
        this.frecuenciaReplanificacion = parameters.getFrecuenciaReplanificacionBase();
        this.estadoColapso = false;

        // Inicializar capacidad actual de tanques
        inicializarEstadoTanques();

        // Inicializar matriz de frecuencia de aristas
        this.matrizFrecuenciaAristas = new int[grafo.getTotalNodos()][grafo.getTotalNodos()];

        // Inicializar la lista histórica de soluciones
        this.historicoSoluciones = new ArrayList<>();

        // Inicializar contador de iteraciones sin mejora global
        this.iteracionesSinMejoraGlobal = 0;
    }

    /**
     * Inicializa el estado de los tanques intermedios
     */
    private void inicializarEstadoTanques() {
        capacidadActualTanques = new HashMap<>();

        for (Almacen almacen : mapa.getAlmacenes()) {
            // Al inicio todos los tanques están llenos
            capacidadActualTanques.put(almacen.getTipoAlmacen(), almacen.getCapacidadEfectivaM3());
        }
    }

    /**
     * Inicializa estados de camiones si no están ya inicializados
     */
    private void inicializarEstadosCamiones() {
        for (Camion camion : mapa.getFlota()) {
            if (camion.getEstado() == null) {
                camion.setEstado(EstadoCamion.DISPONIBLE);
            }
        }
    }

    private void debugAsignacion(Camion camion, List<Pedido> pedidos) {
        System.out.println("🛻 [" + camion.getId() + "] Asignado con " + pedidos.size() + " pedidos:");
        for (Pedido p : pedidos) {
            System.out.println("   📦 Pedido #" + p.getIdPedido() +
                    " - Volumen: " + p.getVolumen() + "m³" +
                    " - Destino: (" + p.getDestino().getX() + "," + p.getDestino().getY() + ")");
        }
    }

    /**
     * Método principal que ejecuta el algoritmo ACO
     *
     * @return La mejor solución encontrada
     */
    public List<Rutas> ejecutar() {
        logger.info("Iniciando algoritmo ACO con " + parameters.getNumeroIteraciones() + " iteraciones");

        this.loggerACO = new ACOLogger();
        this.monitor = new ACOMonitor();
        this.tiempoInicioEjecucion = System.currentTimeMillis();

        inicializarEstadosCamiones();

        // Mostar estado inicial
        this.loggerACO.logFlota(mapa.getFlota());

        // TO-DO: Detección de inconsistencias en datos
        /*if (detectarInconsistencias()) {
            logger.warning("Se detectaron inconsistencias en los datos de entrada");
        }*/

        /* === Estados Iniciales === */

        // Tiempo de simulación
        LocalDateTime tiempoSimulacion = mapa.getFechaInicio() != null ? mapa.getFechaInicio() : LocalDateTime.now();
        LocalDateTime proximaReplanificacion = tiempoSimulacion.plusMinutes(parameters.getIntervaloReplanificacionMinutos());

        // Flota, Pedidos y Rutas
        Map<String, Ubicacion> posicionesCamiones = inicializarPosicionesCamiones();
        List<Pedido> pedidosPendientes = new ArrayList<>(mapa.getPedidos());
        Map<String, List<Ruta>> rutasPlanificadas = new HashMap<>();

        // Lista histórica de todas las rutas para resultado final
        List<Rutas> rutasCompletas = new ArrayList<>();

        // TO-DO: Ajuste dinámico de frecuencia de replanificación por densidad de pedidos
        /*ajustarFrecuenciaReplanificacion();*/

        // Bucle principal de ventanas de simulación
        while (tiempoSimulacion.isBefore(mapa.getFechaFin()) && !estadoColapso) {
            System.out.println("\n=== TIEMPO DE SIMULACIÓN: " + tiempoSimulacion + " ===");

            System.out.println("🔍 PEDIDOS INICIALES: " + mapa.getPedidos().size());
            for (Pedido p : mapa.getPedidos()) {
                System.out.println("  - ID: " + p.getIdPedido() +
                        " | Destino: (" + p.getDestino().getX() + "," + p.getDestino().getY() + ")" +
                        " | Registro: " + p.getFechaRegistro());
            }

            // Procesar averias en camiones | TO-DO: Usarlas bien
            List<String> camionesAveriadosNuevos = procesarAverias(tiempoSimulacion);
            boolean averiasNuevas = !camionesAveriadosNuevos.isEmpty();

            // 1. Determinar si es momento de replanificar
            boolean necesitaReplanificar = tiempoSimulacion.isAfter(proximaReplanificacion) ||
                    (averiasNuevas && parameters.isReplanificacionEmergencia());

            // 2. Si es momento de replanificar, ejecutar múltiples iteraciones de ACO
            if (necesitaReplanificar) {
                System.out.println(">>> REPLANIFICACIÓN EN: " + tiempoSimulacion);

                // Actualizar próxima replanificación
                proximaReplanificacion = tiempoSimulacion.plusMinutes(parameters.getIntervaloReplanificacionMinutos());

                // Crear estado actual para el ACO
                EstadoActual estadoActual = new EstadoActual(
                        posicionesCamiones,
                        filtrarCamionesDisponibles(mapa.getFlota()),
                        pedidosPendientes,
                        tiempoSimulacion
                );

                // Horizonte de planificación
                LocalDateTime horizonteFinal = tiempoSimulacion.plusMinutes(parameters.getHorizontePlanificacionMinutos());

                // Ejecutar múltiples iteraciones de ACO para este horizonte
                ACOSolution nuevaSolucion = ejecutarACODinamicoConMultiplesIteraciones(estadoActual, horizonteFinal);

                // Extraer nuevas rutas planificadas
                rutasPlanificadas = extraerRutasPlanificadas(nuevaSolucion);

                // Guardar estado para resultado final
                guardarEstadoParaResultado(rutasCompletas, nuevaSolucion, tiempoSimulacion);

                iteracion++;
            }

            // Actualizar posiciones según rutas planificadas
            actualizarPosicionesCamiones(posicionesCamiones, rutasPlanificadas, parameters.getTiempoAvanceSimulacion());

            System.out.println("\n🔄 POSICIONES ACTUALIZADAS:");
            for (Map.Entry<String, Ubicacion> entry : posicionesCamiones.entrySet()) {
                Ubicacion pos = entry.getValue();
                System.out.println("  - " + entry.getKey() + ": (" + pos.getX() + "," + pos.getY() + ")");
            }

            // Detectar entregas completadas
            List<Pedido> pedidosEntregados = detectarEntregasCompletadas(posicionesCamiones, rutasPlanificadas, tiempoSimulacion);
            pedidosPendientes.removeAll(pedidosEntregados);

            // Añadir nuevos pedidos si existen para este momento
            List<Pedido> nuevosPedidos = obtenerNuevosPedidos(tiempoSimulacion, pedidosPendientes);
            System.out.println("🔍 TODOS LOS PEDIDOS DESPUÉS DE AÑADIR NUEVOS: " + pedidosPendientes.size());
            if (!nuevosPedidos.isEmpty()) {
                System.out.println("🔢 Pendientes ANTES de añadir: " + pedidosPendientes.size());
                pedidosPendientes.addAll(nuevosPedidos);
                System.out.println("📦 " + nuevosPedidos.size() + " NUEVOS PEDIDOS RECIBIDOS");
                System.out.println("🔢 Pendientes DESPUÉS de añadir: " + pedidosPendientes.size());
            }

            // 4. Visualizar estado actual
            visualizarEstadoActual(tiempoSimulacion, posicionesCamiones, rutasPlanificadas, pedidosPendientes);

            // 5. Verificar estado de colapso
            if (detectarEstadoColapso(pedidosPendientes, tiempoSimulacion)) {
                logger.warning("ALERTA: Sistema en estado de colapso irreversible detectado");
                estadoColapso = true;
                break;
            }

            // Verificar estado de combustible
            verificarEstadoCombustibleFlota();

            // Avanzar tiempo de simulación
            tiempoSimulacion = tiempoSimulacion.plusMinutes(parameters.getTiempoAvanceSimulacion());
        }

        logger.info("Algoritmo ACO con horizonte móvil finalizado");

        // Generar informes finales
        long tiempoTotalMs = System.currentTimeMillis() - tiempoInicioEjecucion;
        loggerACO.logAsignacionDetallada(mejorSolucionGlobal);
        loggerACO.generarDiagnostico(mejorSolucionGlobal, mapa.getPedidos().size());

        return rutasCompletas;
    }

    /**
     * RF97: Detección de inconsistencias en los datos de entrada
     *
     * @return true si se detectaron inconsistencias, false en caso contrario
     */
    private boolean detectarInconsistencias() {
        boolean inconsistenciasDetectadas = false;
        List<String> errores = new ArrayList<>();

        // Verificar pedidos
        for (Pedido pedido : mapa.getPedidos()) {
            // Verificar coordenadas válidas
            if (!coordenadaValida(pedido.getDestino())) {
                errores.add("Pedido " + pedido.getIdPedido() + ": Coordenadas fuera de rango");
                inconsistenciasDetectadas = true;
            }

            // Verificar volumen positivo y no excesivo
            if (pedido.getVolumen() <= 0 || pedido.getVolumen() > 200) {
                errores.add("Pedido " + pedido.getIdPedido() + ": Volumen inválido: " + pedido.getVolumen());
                inconsistenciasDetectadas = true;
            }

            // Verificar plazo mínimo (4 horas mínimo)
            if (pedido.getHorasLimite() < parameters.getPlazoMinimoEntrega()) {
                errores.add("Pedido " + pedido.getIdPedido() + ": Plazo menor al mínimo permitido");
                inconsistenciasDetectadas = true;
            }
        }

        // Verificar bloqueos
        for (Bloqueo bloqueo : mapa.getBloqueos()) {
            // Verificar fechas coherentes
            if (bloqueo.getFechaInicio().isAfter(bloqueo.getFechaFinal())) {
                errores.add("Bloqueo en " + bloqueo.getFechaInicio() + ": Fecha inicio posterior a fecha fin");
                inconsistenciasDetectadas = true;
            }

            // Verificar que los tramos sean adyacentes
            for (int i = 0; i < bloqueo.getTramos().size() - 1; i++) {
                if (!sonAdyacentes(bloqueo.getTramos().get(i), bloqueo.getTramos().get(i + 1))) {
                    errores.add("Bloqueo en " + bloqueo.getFechaInicio());
                    for (Ubicacion tramo : bloqueo.getTramos()) {
                        errores.add("Tramo: " + tramo.getX() + "," + tramo.getY());
                    }
                    errores.add("Bloqueo en " + bloqueo.getFechaInicio() + ": Tramos no adyacentes");
                    inconsistenciasDetectadas = true;
                }
            }
        }

        // Verificar camiones
        for (Camion camion : mapa.getFlota()) {
            // Verificar coherencia de pesos
            if (camion.getPesoCargaTon() > camion.getCargaM3() * 0.5 ||
                    Math.abs(camion.getPesoCombinadoTon() - (camion.getPesoBrutoTon() + camion.getPesoCargaTon())) > 0.01) {
                errores.add("Camión " + camion.getId() + ": Inconsistencia en pesos o capacidades");
                inconsistenciasDetectadas = true;
            }

            // Verificar galones no negativos
            if (camion.getGalones() < 0) {
                errores.add("Camión " + camion.getId() + ": Galones negativos");
                inconsistenciasDetectadas = true;
            }
        }

        // Registrar errores
        if (inconsistenciasDetectadas) {
            logger.warning("Se detectaron " + errores.size() + " inconsistencias en los datos");
            for (String error : errores) {
                logger.warning(error);
            }
        }

        return inconsistenciasDetectadas;
    }

    /**
     * Verifica si dos ubicaciones son adyacentes (están a distancia 1)
     */
    private boolean sonAdyacentes(Ubicacion u1, Ubicacion u2) {
        return Math.abs(u1.getX() - u2.getX()) + Math.abs(u1.getY() - u2.getY()) == 1;
    }

    /**
     * Verifica si una coordenada está dentro del rango válido del mapa
     */
    private boolean coordenadaValida(Ubicacion ubicacion) {
        return ubicacion.getX() >= 0 && ubicacion.getX() <= mapa.getAncho() &&
                ubicacion.getY() >= 0 && ubicacion.getY() <= mapa.getAlto();
    }

    /**
     * RF90/RF91: Actualiza el estado de los camiones según mantenimiento
     */
    private void actualizarEstadoCamionesMantenimiento(LocalDateTime tiempoActual) {
        // Implementación simplificada - en un entorno real se consultaría una DB de mantenimientos
        for (Camion camion : mapa.getFlota()) {
            // Para este ejemplo, asumimos que cada 60 días hay un mantenimiento preventivo
            if (estaEnMantenimientoPreventivo(camion, tiempoActual)) {
                camion.setAveriado(true); // Usar el flag de averiado para indicar no disponibilidad
                logger.info("Camión " + camion.getId() + " no disponible por mantenimiento preventivo");
            }
            // Si está en mantenimiento correctivo por avería previa
            else if (estaEnMantenimientoCorrectivo(camion, tiempoActual)) {
                camion.setAveriado(true);
                logger.info("Camión " + camion.getId() + " no disponible por mantenimiento correctivo");
            }
        }
    }

    /**
     * Comprueba si un camión está en mantenimiento preventivo
     */
    private boolean estaEnMantenimientoPreventivo(Camion camion, LocalDateTime tiempo) {
        // Aquí se consultaría el plan de mantenimiento preventivo
        // Simulación básica: cada 60 días, según ID del camión
        int dia = tiempo.getDayOfYear();
        String idNum = camion.getId().substring(2); // Extraer número del ID (ej: "TA01" -> "01")
        int idNumeric = Integer.parseInt(idNum);

        // Suponemos que el mantenimiento dura 1 día completo
        return (dia % 60) == idNumeric;
    }

    /**
     * Comprueba si un camión está en mantenimiento correctivo
     */
    private boolean estaEnMantenimientoCorrectivo(Camion camion, LocalDateTime tiempo) {
        // En un entorno real, se consultaría un registro de mantenimientos correctivos
        // Simulación básica: asumimos que un camión averiado ya está marcado
        return camion.isAveriado();
    }

    /**
     * RF88: Verificación de disponibilidad de combustible en tanques
     */
    private void verificarDisponibilidadCombustible(LocalDateTime tiempoActual) {
        for (Map.Entry<TipoAlmacen, Double> entrada : capacidadActualTanques.entrySet()) {
            TipoAlmacen tipo = entrada.getKey();
            double capacidad = entrada.getValue();

            // El almacén central siempre tiene combustible
            if (tipo == TipoAlmacen.CENTRAL) {
                continue;
            }

            // Verificar si tiene capacidad suficiente para asignaciones
            if (capacidad < 20.0) {  // Umbral crítico
                logger.warning("Tanque " + tipo + " en nivel crítico: " + capacidad + "m3");
            }
        }
    }

    /**
     * RF95: Priorización de camiones por nivel de combustible
     */
    private List<Camion> priorizarCamionesPorCombustible() {
        // Filtrar camiones disponibles (no averiados)
        List<Camion> camionesDisponibles = mapa.getFlota().stream()
                .filter(c -> !c.isAveriado())
                .collect(Collectors.toList());

        // Ordenar por nivel de combustible (menor a mayor)
        camionesDisponibles.sort(Comparator.comparingInt(Camion::getGalones));

        return camionesDisponibles;
    }

    private List<Camion> filtrarCamionesDisponibles(List<Camion> camiones) {
        return camiones.stream()
                .filter(camion -> {
                    // Si el estado es null, considerarlo como no disponible
                    if (camion.getEstado() == null) {
                        // Arreglo en tiempo de ejecución: inicializar estado
                        camion.setEstado(EstadoCamion.DISPONIBLE);
                        System.out.println("⚠️ Camión " + camion.getId() +
                                " tenía estado null, inicializado a DISPONIBLE");
                    }
                    return camion.getEstado() == EstadoCamion.DISPONIBLE;
                })
                .collect(Collectors.toList());
    }


    /**
     * Verifica si un bloqueo está activo en un momento determinado
     */
    private boolean estaActivoEnTiempo(Bloqueo bloqueo, LocalDateTime tiempo) {
        return !tiempo.isBefore(bloqueo.getFechaInicio()) && !tiempo.isAfter(bloqueo.getFechaFinal());
    }

    /**
     * RF94: Detección de estado de colapso del sistema
     */
    private boolean detectarEstadoColapso(List<ACOSolution> soluciones) {
        if (soluciones.isEmpty()) {
            return false;
        }

        int totalPedidos = mapa.getPedidos().size();
        int pedidosNoEntregables = 0;

        // Contar pedidos no asignados o no entregables a tiempo
        for (ACOSolution solucion : soluciones) {
            pedidosNoEntregables += solucion.getPedidosNoAsignados().size();
        }

        double promedioPedidosNoEntregables = (double) pedidosNoEntregables / soluciones.size();
        double porcentajeNoEntregables = totalPedidos > 0 ? promedioPedidosNoEntregables / totalPedidos : 0;

        // Si más del umbral de pedidos no se pueden entregar, considerar colapso
        if (porcentajeNoEntregables > parameters.getUmbralColapso()) {
            logger.warning("Detectado posible colapso: " +
                    String.format("%.2f", porcentajeNoEntregables * 100) +
                    "% de pedidos no entregables");

            // Verificamos si esto persiste por varias iteraciones antes de declarar colapso
            return iterSinMejora >= 5;
        }

        return false;
    }

    /**
     * Ajuste dinámico de frecuencia de replanificación según densidad de pedidos
     */
    private void ajustarFrecuenciaReplanificacion() {
        // Calcular densidad de pedidos (pedidos por hora)
        double densidadPedidos = calcularDensidadPedidos();

        // Ajustar frecuencia según densidad
        if (densidadPedidos > 15) { // Muy alta densidad
            frecuenciaReplanificacion = 15; // Replanificar cada 15 minutos
        } else if (densidadPedidos > 10) {
            frecuenciaReplanificacion = 30; // Replanificar cada 30 minutos
        } else if (densidadPedidos > 5) {
            frecuenciaReplanificacion = 45; // Replanificar cada 45 minutos
        } else {
            frecuenciaReplanificacion = parameters.getFrecuenciaReplanificacionBase(); // Default (60 minutos)
        }

        logger.info("Frecuencia de replanificación ajustada a " + frecuenciaReplanificacion + " minutos");
    }

    /**
     * Calcula la densidad de pedidos (pedidos por hora)
     */
    private double calcularDensidadPedidos() {
        if (mapa.getPedidos().isEmpty()) {
            return 0;
        }

        // Contar pedidos en ventana de tiempo reciente (últimas 4 horas)
        LocalDateTime ahora = mapa.getFechaInicio() != null ? mapa.getFechaInicio() : LocalDateTime.now();
        LocalDateTime cuatroHorasAtras = ahora.minusHours(4);

        long pedidosRecientes = mapa.getPedidos().stream()
                .filter(p -> p.getFechaRegistro().isAfter(cuatroHorasAtras))
                .count();

        return (double) pedidosRecientes / 4.0; // Pedidos por hora
    }

    /**
     * Evalúa la calidad de una solución considerando múltiples factores
     */
    private double evaluarSolucion(ACOSolution solucion, LocalDateTime tiempoActual) {
        double consumoTotal = 0;
        double penalizacionTiempo = 0;
        double penalizacionRestricciones = 0;
        double penalizacionCombustible = 0;

        // Evaluar cada asignación camión-pedidos-ruta
        for (CamionAsignacion asignacion : solucion.getAsignaciones()) {
            Camion camion = asignacion.getCamion();
            List<Pedido> pedidos = asignacion.getPedidos();
            List<Ruta> rutas = asignacion.getRutas();

            // Calcular consumo para esta asignación
            double pesoInicial = camion.getPesoBrutoTon() + AlgorithmUtils.calcularPesoCargaTotal(pedidos);
            double pesoActual = pesoInicial;
            LocalDateTime tiempoEstimado = tiempoActual;
            double combustibleActual = camion.getGalones(); // Usar el combustible real del camión

            for (Ruta ruta : rutas) {
                double distancia = ruta.getDistancia();

                // RF87: Cálculo dinámico de consumo de combustible
                double consumoRuta = (distancia * pesoActual) / 180.0;
                consumoTotal += consumoRuta;

                // Restar combustible y verificar si es suficiente
                combustibleActual -= consumoRuta;
                if (combustibleActual < 0 && !ruta.isPuntoReabastecimiento()) {
                    // Penalizar rutas donde se agota el combustible sin reabastecimiento
                    penalizacionCombustible += 5000;
                }

                // Si es punto de reabastecimiento, rellenar combustible
                if (ruta.isPuntoReabastecimiento()) {
                    combustibleActual = 25.0;
                }

                // Actualizar tiempo estimado
                tiempoEstimado = tiempoEstimado.plusMinutes((long) (distancia / parameters.getVelocidadPromedio() * 60));

                // Si es una entrega, actualizar peso
                if (ruta.isPuntoEntrega()) {
                    Pedido pedidoEntregado = ruta.getPedidoEntrega();
                    if (pedidoEntregado != null) {
                        // Verificar tiempo límite de entrega
                        if (tiempoEstimado.isAfter(pedidoEntregado.getFechaLimite())) {
                            // Penalización por entrega tardía
                            long minutosRetraso = ChronoUnit.MINUTES.between(pedidoEntregado.getFechaLimite(), tiempoEstimado);
                            penalizacionTiempo += 1000 * minutosRetraso;
                        }

                        // Actualizar peso del camión (se quita la carga entregada)
                        double pesoCarga = AlgorithmUtils.calcularPesoCarga(pedidoEntregado);
                        pesoActual -= pesoCarga;

                        // Añadir tiempo de descarga (15 minutos)
                        tiempoEstimado = tiempoEstimado.plusMinutes(15);
                    }
                }

                // Verificar si hay bloqueos en la ruta
                if (hayBloqueoEnRuta(ruta, tiempoEstimado)) {
                    penalizacionRestricciones += 5000;
                }
            }

            // Verificar si el camión tiene mantenimiento programado durante la ruta
            if (camionTieneMantenimientoProgramado(camion, tiempoActual, tiempoEstimado)) {
                penalizacionRestricciones += 10000;
            }
        }

        // Penalizar pedidos no asignados
        penalizacionRestricciones += solucion.getPedidosNoAsignados().size() * 20000;

        // Calcular calidad total (inversamente proporcional a costos y penalizaciones)
        double totalPenalizaciones = consumoTotal + penalizacionTiempo +
                penalizacionRestricciones + penalizacionCombustible;
        return 1.0 / (1.0 + totalPenalizaciones);
    }

    /**
     * Verifica si hay un bloqueo que afecte a una ruta en un momento dado
     */
    private boolean hayBloqueoEnRuta(Ruta ruta, LocalDateTime tiempo) {
        for (Bloqueo bloqueo : mapa.getBloqueos()) {
            if (estaActivoEnTiempo(bloqueo, tiempo)) {
                // Verificar si algún punto de la ruta intersecta con el bloqueo
                for (Ubicacion puntoBloqueo : bloqueo.getTramos()) {
                    if (ruta.contienePunto(puntoBloqueo)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Verifica si un camión tiene mantenimiento programado en un período
     */
    private boolean camionTieneMantenimientoProgramado(Camion camion, LocalDateTime inicio, LocalDateTime fin) {
        // En una implementación real, consultaría el plan de mantenimiento
        // Simplificación: verificamos si el mantenimiento preventivo cae en ese periodo
        for (LocalDateTime tiempo = inicio; !tiempo.isAfter(fin); tiempo = tiempo.plusDays(1)) {
            if (estaEnMantenimientoPreventivo(camion, tiempo)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Convierte una solución ACO al formato de rutas del dominio
     */
    private List<Rutas> convertirSolucionARutas(ACOSolution solucion) {
        if (solucion == null) {
            return new ArrayList<>();
        }

        List<Rutas> listaRutas = new ArrayList<>();
        int idRuta = 1;

        for (CamionAsignacion asignacion : solucion.getAsignaciones()) {
            Rutas rutaEntity = new Rutas();
            rutaEntity.setId(idRuta++);
            rutaEntity.setCamion(asignacion.getCamion());

            // Utilizar directamente la ruta completa con todos los nodos intermedios
            List<Ubicacion> ubicacionesCompletas = asignacion.getRutaCompleta();

            // Verificar que la ruta completa no sea null
            if (ubicacionesCompletas == null) {
                // Si es null, generarla a partir de las rutas
                ubicacionesCompletas = new ArrayList<>();
                for (Ruta ruta : asignacion.getRutas()) {
                    ubicacionesCompletas.add(ruta.getOrigen());
                    // La última ruta añade también el destino
                    if (asignacion.getRutas().indexOf(ruta) == asignacion.getRutas().size() - 1) {
                        ubicacionesCompletas.add(ruta.getDestino());
                    }
                }
            }

            rutaEntity.setUbicaciones(ubicacionesCompletas);

            // El resto del código sigue igual
            double distanciaTotal = asignacion.getRutas().stream()
                    .mapToDouble(Ruta::getDistancia)
                    .sum();
            rutaEntity.setDistanciaTotal(distanciaTotal);

            double tiempoHoras = distanciaTotal / parameters.getVelocidadPromedio();
            tiempoHoras += asignacion.getPedidos().size() * (15.0 / 60.0);
            tiempoHoras += 15.0 / 60.0;
            rutaEntity.setTiempoTotal(tiempoHoras);

            double pesoPromedio = (asignacion.getCamion().getPesoBrutoTon() +
                    AlgorithmUtils.calcularPesoCargaTotal(asignacion.getPedidos()) / 2);
            double consumoTotal = (distanciaTotal * pesoPromedio) / 180.0;
            rutaEntity.setConsumoTotal(consumoTotal);

            listaRutas.add(rutaEntity);
        }

        return listaRutas;
    }

    // ==== INICIO DE NUEVOS MÉTODOS PARA BÚSQUEDA LOCAL Y TRANSFERENCIA DE ESTADO ====

    /**
     * Aplica técnicas de búsqueda ogi para mejorar una solución
     */
    private void aplicarBusquedaLocal(ACOSolution solucion) {
        // 1. Optimización por intercambio de pedidos entre camiones
        optimizarIntercambioPedidos(solucion);

        // 2. Optimización 2-opt para rutas de cada camión
        for (CamionAsignacion asignacion : solucion.getAsignaciones()) {
            optimizarRutas2Opt(asignacion);
        }

        // 3. Optimización por reubicación de pedidos
        optimizarReubicacionPedidos(solucion);
    }

    /**
     * Optimiza rutas aplicando el algoritmo 2-opt
     */
    private void optimizarRutas2Opt(CamionAsignacion asignacion) {
        List<Ruta> rutas = asignacion.getRutas();

        // Solo aplicar si hay suficientes rutas
        if (rutas.size() <= 3) {
            return;
        }

        // Extraer rutas de entrega (no incluir idas a almacén o reabastecimiento)
        List<Integer> indicesEntrega = new ArrayList<>();
        for (int i = 0; i < rutas.size(); i++) {
            if (rutas.get(i).isPuntoEntrega()) {
                indicesEntrega.add(i);
            }
        }

        if (indicesEntrega.size() <= 2) {
            return; // No hay suficientes puntos de entrega para optimizar
        }

        // Aplicar 2-opt
        boolean mejora = true;
        int intentos = 0;

        while (mejora && intentos < 5) {
            mejora = false;
            intentos++;

            for (int i = 0; i < indicesEntrega.size() - 1; i++) {
                for (int j = i + 1; j < indicesEntrega.size() - 1; j++) {
                    int idx1 = indicesEntrega.get(i);
                    int idx2 = indicesEntrega.get(j);

                    // Calcular distancia actual entre estos puntos y los siguientes
                    double distanciaActual =
                            calcularDistanciaConsecutiva(rutas, idx1, idx1 + 1) +
                                    calcularDistanciaConsecutiva(rutas, idx2, idx2 + 1);

                    // Calcular distancia con intercambio
                    double distanciaIntercambio =
                            calcularDistancia(rutas.get(idx1).getOrigen(), rutas.get(idx2).getDestino()) +
                                    calcularDistancia(rutas.get(idx2).getOrigen(), rutas.get(idx1).getDestino());

                    if (distanciaIntercambio < distanciaActual * 0.90) { // Al menos 10% de mejora
                        // Intercambiar destinos
                        Ubicacion tempDestino = rutas.get(idx1).getDestino();
                        rutas.get(idx1).setDestino(rutas.get(idx2).getDestino());
                        rutas.get(idx2).setDestino(tempDestino);

                        // Intercambiar pedidos
                        Pedido tempPedido = rutas.get(idx1).getPedidoEntrega();
                        rutas.get(idx1).setPedidoEntrega(rutas.get(idx2).getPedidoEntrega());
                        rutas.get(idx2).setPedidoEntrega(tempPedido);

                        // Recalcular distancias
                        recalcularDistanciasRutas(rutas);

                        mejora = true;
                        break;
                    }
                }
                if (mejora) break;
            }
        }
    }

    /**
     * Optimiza asignaciones intercambiando pedidos entre camiones
     */
    private void optimizarIntercambioPedidos(ACOSolution solucion) {
        List<CamionAsignacion> asignaciones = solucion.getAsignaciones();

        // Solo aplicar si hay al menos dos camiones asignados
        if (asignaciones.size() < 2) {
            return;
        }

        boolean mejora = true;
        int intentos = 0;

        while (mejora && intentos < 3) {
            mejora = false;
            intentos++;

            // Para cada par de camiones
            for (int i = 0; i < asignaciones.size() - 1 && !mejora; i++) {
                for (int j = i + 1; j < asignaciones.size() && !mejora; j++) {
                    CamionAsignacion asig1 = asignaciones.get(i);
                    CamionAsignacion asig2 = asignaciones.get(j);

                    // Para cada pedido del primer camión
                    for (int pi = 0; pi < asig1.getPedidos().size() && !mejora; pi++) {
                        Pedido pedido1 = asig1.getPedidos().get(pi);

                        // Para cada pedido del segundo camión
                        for (int pj = 0; pj < asig2.getPedidos().size() && !mejora; pj++) {
                            Pedido pedido2 = asig2.getPedidos().get(pj);

                            // Verificar si el intercambio es factible (capacidad)
                            if (!verificarFactibilidadIntercambio(asig1.getCamion(), asig2.getCamion(),
                                    pedido1, pedido2)) {
                                continue;
                            }

                            // Calcular beneficio potencial del intercambio
                            double beneficio = calcularBeneficioIntercambio(asig1, asig2, pedido1, pedido2);

                            if (beneficio > 0) {
                                // Realizar el intercambio
                                intercambiarPedidos(asig1, asig2, pedido1, pedido2);
                                mejora = true;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Optimiza reubicando pedidos de un camión a otro
     */
    private void optimizarReubicacionPedidos(ACOSolution solucion) {
        List<CamionAsignacion> asignaciones = solucion.getAsignaciones();

        if (asignaciones.size() < 2) {
            return;
        }

        boolean mejora = true;
        int intentos = 0;

        while (mejora && intentos < 3) {
            mejora = false;
            intentos++;

            // Para cada camión origen
            for (int i = 0; i < asignaciones.size() && !mejora; i++) {
                CamionAsignacion asigOrigen = asignaciones.get(i);

                // Para cada pedido del camión origen
                for (int p = 0; p < asigOrigen.getPedidos().size() && !mejora; p++) {
                    Pedido pedido = asigOrigen.getPedidos().get(p);

                    // Para cada camión destino
                    for (int j = 0; j < asignaciones.size() && !mejora; j++) {
                        if (i == j) continue; // Ignorar el mismo camión

                        CamionAsignacion asigDestino = asignaciones.get(j);

                        // Verificar si la reubicación es factible
                        if (!verificarFactibilidadReubicacion(asigDestino.getCamion(), pedido)) {
                            continue;
                        }

                        // Calcular beneficio potencial de la reubicación
                        double beneficio = calcularBeneficioReubicacion(asigOrigen, asigDestino, pedido);

                        if (beneficio > 0) {
                            // Realizar la reubicación
                            reubicarPedido(asigOrigen, asigDestino, pedido);
                            mejora = true;
                        }
                    }
                }
            }
        }
    }

    /**
     * Calcula la distancia entre dos ubicaciones
     */
    private double calcularDistancia(Ubicacion u1, Ubicacion u2) {
        return Math.abs(u1.getX() - u2.getX()) + Math.abs(u1.getY() - u2.getY());
    }

    /**
     * Calcula la distancia entre rutas consecutivas
     */
    private double calcularDistanciaConsecutiva(List<Ruta> rutas, int i, int j) {
        if (i < 0 || j < 0 || i >= rutas.size() || j >= rutas.size()) {
            return Double.MAX_VALUE;
        }
        return calcularDistancia(rutas.get(i).getDestino(), rutas.get(j).getOrigen());
    }

    /**
     * Recalcula todas las distancias en una lista de rutas
     */
    private void recalcularDistanciasRutas(List<Ruta> rutas) {
        for (Ruta ruta : rutas) {
            double distancia = calcularDistancia(ruta.getOrigen(), ruta.getDestino());
            ruta.setDistancia(distancia);
        }
    }

    /**
     * Verifica si es factible intercambiar pedidos entre camiones
     */
    private boolean verificarFactibilidadIntercambio(Camion c1, Camion c2, Pedido p1, Pedido p2) {
        // Verificar capacidad después del intercambio
        double volumenP1 = p1.getVolumen();
        double volumenP2 = p2.getVolumen();

        return c1.getCargaM3() - volumenP1 + volumenP2 <= c1.getCargaM3() &&
                c2.getCargaM3() - volumenP2 + volumenP1 <= c2.getCargaM3();
    }

    /**
     * Calcula el beneficio potencial de intercambiar pedidos
     */
    private double calcularBeneficioIntercambio(CamionAsignacion a1, CamionAsignacion a2, Pedido p1, Pedido p2) {
        // Calcular costo actual
        double costoActual = calcularCostoPedidoEnAsignacion(a1, p1) + calcularCostoPedidoEnAsignacion(a2, p2);

        // Calcular costo después del intercambio
        double costoNuevo = calcularCostoPedidoEnAsignacion(a1, p2) + calcularCostoPedidoEnAsignacion(a2, p1);

        return costoActual - costoNuevo;
    }

    /**
     * Realiza el intercambio de pedidos entre asignaciones
     */
    private void intercambiarPedidos(CamionAsignacion a1, CamionAsignacion a2, Pedido p1, Pedido p2) {
        // Intercambiar pedidos en las listas
        a1.getPedidos().remove(p1);
        a1.getPedidos().add(p2);

        a2.getPedidos().remove(p2);
        a2.getPedidos().add(p1);

        // Actualizar rutas (simplificado, en realidad debería reconstruirse)
        for (Ruta r : a1.getRutas()) {
            if (r.getPedidoEntrega() == p1) {
                r.setPedidoEntrega(p2);
            }
        }

        for (Ruta r : a2.getRutas()) {
            if (r.getPedidoEntrega() == p2) {
                r.setPedidoEntrega(p1);
            }
        }
    }

    /**
     * Verifica si es factible reubicar un pedido a otro camión
     */
    private boolean verificarFactibilidadReubicacion(Camion camionDestino, Pedido pedido) {
        // En una implementación real, necesitaríamos acceder a la capacidad disponible actual
        // Esta es una simplificación
        return pedido.getVolumen() <= camionDestino.getCargaM3();
    }

    /**
     * Calcula el beneficio de reubicar un pedido
     */
    private double calcularBeneficioReubicacion(CamionAsignacion aOrigen, CamionAsignacion aDestino, Pedido pedido) {
        // Calcular costo actual
        double costoActual = calcularCostoPedidoEnAsignacion(aOrigen, pedido);

        // Calcular costo después de la reubicación
        double costoNuevo = calcularCostoPedidoEnAsignacionHipotetica(aDestino, pedido);

        return costoActual - costoNuevo;
    }

    /**
     * Reubica un pedido de una asignación a otra
     */
    private void reubicarPedido(CamionAsignacion aOrigen, CamionAsignacion aDestino, Pedido pedido) {
        // Mover pedido entre listas
        aOrigen.getPedidos().remove(pedido);
        aDestino.getPedidos().add(pedido);

        // Actualizar rutas (simplificado)
        // En una implementación real, debería reconstruirse la ruta completa
        // o hacer un ajuste más sofisticado
    }

    /**
     * Calcula el costo aproximado de un pedido en una asignación
     */
    private double calcularCostoPedidoEnAsignacion(CamionAsignacion asignacion, Pedido pedido) {
        // Implementación simplificada
        // En una versión real, calcularía el costo exacto considerando la secuencia completa
        Ubicacion ubicacionPedido = pedido.getDestino();

        // Buscar la ruta que entrega este pedido
        for (Ruta r : asignacion.getRutas()) {
            if (r.getPedidoEntrega() == pedido) {
                return r.getDistancia() * (asignacion.getCamion().getPesoBrutoTon() + pedido.getVolumen() * 0.5) / 180.0;
            }
        }

        return 0; // No encontrado
    }

    /**
     * Calcula el costo hipotético de incluir un pedido en una asignación
     */
    private double calcularCostoPedidoEnAsignacionHipotetica(CamionAsignacion asignacion, Pedido pedido) {
        // Implementación simplificada
        // Estima el costo de añadir este pedido a la ruta

        // Encontrar el punto más cercano en la ruta actual
        double distanciaMinima = Double.MAX_VALUE;
        Ubicacion puntoInsercion = null;

        for (Ruta r : asignacion.getRutas()) {
            double d = calcularDistancia(r.getDestino(), pedido.getDestino());
            if (d < distanciaMinima) {
                distanciaMinima = d;
                puntoInsercion = r.getDestino();
            }
        }

        if (puntoInsercion == null) {
            // Si no hay rutas, calcular desde el almacén
            puntoInsercion = obtenerUbicacionAlmacenCentral();
            distanciaMinima = calcularDistancia(puntoInsercion, pedido.getDestino());
        }

        // Calcular costo aproximado de inserción
        return distanciaMinima * (asignacion.getCamion().getPesoBrutoTon() + pedido.getVolumen() * 0.5) / 180.0;
    }

    /**
     * Obtiene la ubicación del almacén central
     */
    private Ubicacion obtenerUbicacionAlmacenCentral() {
        return new Ubicacion(12, 8); // Valor por defecto
    }

    /**
     * Actualiza la matriz de frecuencia con información de las mejores rutas
     */
    private void actualizarMatrizFrecuencia(ACOSolution solucion) {
        // Para cada asignación
        for (CamionAsignacion asignacion : solucion.getAsignaciones()) {
            // Para cada ruta
            for (Ruta ruta : asignacion.getRutas()) {
                // Obtener IDs de origen y destino
                int origen = calcularIdNodo(ruta.getOrigen());
                int destino = calcularIdNodo(ruta.getDestino());

                // Incrementar contador de frecuencia
                if (origen >= 0 && origen < matrizFrecuenciaAristas.length &&
                        destino >= 0 && destino < matrizFrecuenciaAristas[0].length) {
                    matrizFrecuenciaAristas[origen][destino]++;
                    matrizFrecuenciaAristas[destino][origen]++; // Grafo no dirigido
                }
            }
        }
    }

    /**
     * Calcula un ID único para un nodo a partir de su ubicación
     */
    private int calcularIdNodo(Ubicacion ubicacion) {
        // Este cálculo debe ser coherente con la forma en que se asignan IDs en el grafo
        int ancho = mapa.getAncho() + 1;
        return ubicacion.getY() * ancho + ubicacion.getX();
    }

    /**
     * Selecciona una solución histórica aleatoria dando más peso a las mejores
     */
    private ACOSolution seleccionarSolucionHistoricaAleatoria() {
        if (historicoSoluciones.isEmpty()) {
            return null;
        }

        // Ordenar por calidad (mejor primero)
        List<ACOSolution> ordenadas = new ArrayList<>(historicoSoluciones);
        ordenadas.sort((s1, s2) -> Double.compare(s2.getCalidad(), s1.getCalidad()));

        // Selección ponderada (más probabilidad para las mejores)
        double total = 0;
        double[] pesos = new double[ordenadas.size()];

        for (int i = 0; i < ordenadas.size(); i++) {
            // Peso inversamente proporcional a la posición
            pesos[i] = 1.0 / (i + 1);
            total += pesos[i];
        }

        // Selección por ruleta
        double seleccion = Math.random() * total;
        double acumulado = 0;

        for (int i = 0; i < ordenadas.size(); i++) {
            acumulado += pesos[i];
            if (acumulado >= seleccion) {
                return ordenadas.get(i);
            }
        }

        return ordenadas.get(0); // Por defecto, la mejor
    }

    // Para compatibilidad con algunas funciones de la clase Ant
    private Random random = new Random();

    /**
     * Inicializa posiciones de camiones (todos en almacén central al inicio)
     */
    private Map<String, Ubicacion> inicializarPosicionesCamiones() {
        Map<String, Ubicacion> posiciones = new HashMap<>();
        Ubicacion almacenCentral = obtenerUbicacionAlmacenCentral();

        for (Camion camion : mapa.getFlota()) {
            posiciones.put(camion.getId(), almacenCentral);
        }

        return posiciones;
    }

    /**
     * Procesa averías que ocurren en un momento dado
     */
    private List<String> procesarAverias(LocalDateTime tiempoActual) {
        List<String> nuevasAverias = new ArrayList<>();

        if (mapa.getAverias() != null) {
            for (Averia averia : mapa.getAverias()) {
                if (averia.getFechaIncidente() != null &&
                        Math.abs(ChronoUnit.MINUTES.between(averia.getFechaIncidente(), tiempoActual)) < parameters.getTiempoAvanceSimulacion()) {

                    String codigoCamion = averia.getCodigo();

                    // Buscar el camión y marcarlo como averiado
                    for (Camion camion : mapa.getFlota()) {
                        if (camion.getId().equals(codigoCamion) && !camion.isAveriado()) {
                            camion.setAveriado(true);
                            camion.setEstado(EstadoCamion.MANTENIMIENTO);
                            nuevasAverias.add(codigoCamion);
                            logger.info("Camión " + codigoCamion + " ha sufrido una avería tipo " + averia.getIncidente());
                            System.out.println("❌ AVERÍA: Camión " + codigoCamion + " - " + averia.getIncidente());
                            break;
                        }
                    }
                }
            }
        }

        return nuevasAverias;
    }

    /**
     * Actualiza posiciones de camiones según rutas planificadas
     */
    private void actualizarPosicionesCamiones(Map<String, Ubicacion> posiciones,
                                              Map<String, List<Ruta>> rutasPlanificadas,
                                              int minutosAvance) {
        // Para cada camión con rutas planificadas
        for (String idCamion : new ArrayList<>(rutasPlanificadas.keySet())) {
            List<Ruta> rutas = rutasPlanificadas.get(idCamion);
            if (rutas == null || rutas.isEmpty()) continue;

            // Buscar el camión correspondiente y su posición actual
            Camion camion = null;
            for (Camion c : mapa.getFlota()) {
                if (c.getId().equals(idCamion)) {
                    camion = c;
                    break;
                }
            }
            if (camion == null) continue;

            Ubicacion posicionActual = posiciones.get(idCamion);
            if (posicionActual == null) continue;

            // Encontrar la ruta actual y punto de progreso
            double distanciaRestante = minutosAvance * (parameters.getVelocidadPromedio() / 60.0); // km
            int rutaActual = 0;
            double distanciaRecorrida = 0;

            while (distanciaRestante > 0 && rutaActual < rutas.size()) {
                Ruta ruta = rutas.get(rutaActual);

                // Calcular peso actual para determinar consumo
                double pesoActual = camion.getPesoBrutoTon();
                for (int i = rutaActual; i < rutas.size(); i++) {
                    if (rutas.get(i).isPuntoEntrega() && rutas.get(i).getPedidoEntrega() != null) {
                        pesoActual += rutas.get(i).getPedidoEntrega().getVolumen() * 0.5; // 0.5 ton por m3
                    }
                }

                // Si la distancia restante cubre toda la ruta
                if (distanciaRestante >= ruta.getDistancia()) {
                    // Moverse al final de esta ruta
                    posicionActual = ruta.getDestino();
                    distanciaRecorrida += ruta.getDistancia();
                    distanciaRestante -= ruta.getDistancia();

                    // Actualizar combustible consumido
                    double consumo = (ruta.getDistancia() * pesoActual) / 180.0;
                    int galonesActuales = camion.getGalones();
                    camion.setGalones(Math.max(0, galonesActuales - (int)Math.ceil(consumo)));

                    // Si es punto de entrega o reabastecimiento, detenerse por tiempo de operación
                    if (ruta.isPuntoEntrega() || ruta.isPuntoReabastecimiento()) {
                        int tiempoOperacion = ruta.isPuntoEntrega() ?
                                parameters.getTiempoDescargaCliente() : parameters.getTiempoMantenimientoRutina();

                        double distanciaEquivalente = tiempoOperacion * (parameters.getVelocidadPromedio() / 60.0);
                        distanciaRestante -= distanciaEquivalente;
                    }

                    // Pasar a la siguiente ruta
                    rutaActual++;
                }
                // Si solo cubre parte de la ruta, calcular posición intermedia
                else {
                    double proporcion = distanciaRestante / ruta.getDistancia();
                    posicionActual = calcularPosicionIntermedia(ruta.getOrigen(), ruta.getDestino(), proporcion);

                    // Actualizar combustible por distancia parcial
                    double consumoParcial = (distanciaRestante * pesoActual) / 180.0;
                    int galonesActuales = camion.getGalones();
                    camion.setGalones(Math.max(0, galonesActuales - (int)Math.ceil(consumoParcial)));

                    distanciaRecorrida += distanciaRestante;
                    distanciaRestante = 0; // Ya no queda distancia por recorrer
                }
            }

            // Actualizar posición y eliminar rutas completadas
            posiciones.put(idCamion, posicionActual);
            if (rutaActual > 0) {
                rutasPlanificadas.put(idCamion, rutas.subList(rutaActual, rutas.size()));
            }

            // Registrar si el combustible es crítico
            if (camion.getGalones() < parameters.getUmbralCombustibleCritico()) {
                System.out.println("⚠️ ALERTA: Camión " + idCamion + " con combustible crítico: " +
                        camion.getGalones() + " galones");
            }
        }
    }

    /**
     * Calcula posición intermedia entre dos puntos
     */
    private Ubicacion calcularPosicionIntermedia(Ubicacion origen, Ubicacion destino, double proporcion) {
        int x = origen.getX() + (int) Math.round((destino.getX() - origen.getX()) * proporcion);
        int y = origen.getY() + (int) Math.round((destino.getY() - origen.getY()) * proporcion);

        return new Ubicacion(x, y);
    }

    /**
     * Detecta entregas completadas en el periodo actual
     */
    private List<Pedido> detectarEntregasCompletadas(Map<String, Ubicacion> posiciones,
                                                     Map<String, List<Ruta>> rutasPlanificadas,
                                                     LocalDateTime tiempoActual) {
        List<Pedido> pedidosEntregados = new ArrayList<>();

        // Para cada camión
        for (String idCamion : new ArrayList<>(rutasPlanificadas.keySet())) {
            List<Ruta> rutas = rutasPlanificadas.get(idCamion);

            if (rutas == null || rutas.isEmpty()) continue;

            // Verificar si la primera ruta es una entrega y si el camión está en esa posición
            Ruta primeraRuta = rutas.get(0);
            if (primeraRuta.isPuntoEntrega() &&
                    posiciones.get(idCamion).equals(primeraRuta.getDestino()) &&
                    primeraRuta.getPedidoEntrega() != null) {

                // Marcar pedido como entregado
                pedidosEntregados.add(primeraRuta.getPedidoEntrega());
                System.out.println("✅ ENTREGA: Pedido #" + primeraRuta.getPedidoEntrega().getIdPedido() +
                        " por camión " + idCamion);
            }
        }

        return pedidosEntregados;
    }

    /**
     * Obtiene los nuevos pedidos que llegan para la ventana de simulación actual
     */
    private List<Pedido> obtenerNuevosPedidos(LocalDateTime tiempoActual, List<Pedido> pedidosPendientes) {
        List<Pedido> nuevosPedidos = new ArrayList<>();

        // Calcular la ventana de tiempo para este intervalo
        LocalDateTime tiempoAnterior = tiempoActual.minusMinutes(parameters.getTiempoAvanceSimulacion());

        for (Pedido pedido : mapa.getPedidos()) {
            // Un pedido es nuevo si su fecha de registro está en este intervalo de tiempo
            if (pedido.getFechaRegistro().isAfter(tiempoAnterior) &&
                    !pedido.getFechaRegistro().isAfter(tiempoActual)) {

                // Verificar que no esté ya en la lista de pendientes
                boolean yaExiste = pedidosPendientes.stream()
                        .anyMatch(p -> p.getIdPedido() == pedido.getIdPedido());

                if (!yaExiste) {
                    nuevosPedidos.add(pedido);
                    System.out.println("📦 NUEVO PEDIDO: #" + pedido.getIdPedido() +
                            " en (" + pedido.getDestino().getX() + "," + pedido.getDestino().getY() + ")");
                }
            }
        }

        return nuevosPedidos;
    }

    /**
     * Extrae rutas planificadas desde una solución ACO
     */
    private Map<String, List<Ruta>> extraerRutasPlanificadas(ACOSolution solucion) {
        Map<String, List<Ruta>> rutasPorCamion = new HashMap<>();

        for (CamionAsignacion asignacion : solucion.getAsignaciones()) {
            Camion camion = asignacion.getCamion();
            rutasPorCamion.put(camion.getId(), new ArrayList<>(asignacion.getRutas()));
        }

        return rutasPorCamion;
    }

    /**
     * Visualiza el estado actual (consola)
     */
    private void visualizarEstadoActual(LocalDateTime tiempo,
                                        Map<String, Ubicacion> posiciones,
                                        Map<String, List<Ruta>> rutas,
                                        List<Pedido> pendientes) {
        System.out.println("\n--- ESTADO ACTUAL: " + tiempo + " ---");
        System.out.println("Camiones en ruta: " + posiciones.size());
        System.out.println("Pedidos pendientes: " + pendientes.size());

        // Detalles de posición de cada camión
        for (String idCamion : posiciones.keySet()) {
            Ubicacion pos = posiciones.get(idCamion);
            System.out.println("🚚 " + idCamion + " en (" + pos.getX() + "," + pos.getY() + ")");

            // Siguientes destinos planificados
            List<Ruta> rutasCamion = rutas.get(idCamion);
            if (rutasCamion != null && !rutasCamion.isEmpty()) {
                System.out.println("   Próximo destino: (" +
                        rutasCamion.get(0).getDestino().getX() + "," +
                        rutasCamion.get(0).getDestino().getY() + ") - " +
                        (rutasCamion.get(0).isPuntoEntrega() ? "ENTREGA" :
                                rutasCamion.get(0).isPuntoReabastecimiento() ? "REABASTECIMIENTO" : "REGRESO"));
            }
        }
    }

    /**
     * Ejecuta algoritmo ACO con estado actual específico
     */
    private ACOSolution ejecutarACOConEstadoActual(EstadoActual estado, LocalDateTime horizonteFinal) {
        // Persistir matriz de feromonas o crear una nueva
        if (this.pheromonesMatrix != null) {
            // Aplicar evaporación entre planificaciones para mantener
            // aprendizaje pero evitar estancamiento
            this.pheromonesMatrix.aplicarEvaporacionEntrePlanificaciones(factorEvaporacionEntrePlanificaciones);
            System.out.println("🧠 Utilizando matriz de feromonas persistida de replanificación anterior");
        } else {
            // Primera ejecución, inicializar nueva matriz
            this.pheromonesMatrix = new PheromoneMatrix(grafo.getTotalNodos(), parameters.getFeromonaInicial());
            System.out.println("🆕 Inicializando nueva matriz de feromonas");
        }

        // Siempre recalcular heurística (esta depende de estado actual)
        this.heuristicCalculator = new HeuristicCalculator(grafo, parameters);

        // Actualizar heurística según estado actual
        heuristicCalculator.actualizarHeuristicaDinamica(
                estado.getPedidosPendientes(),
                mapa.getBloqueos(),
                estado.getTiempoActual(),
                capacidadActualTanques
        );

        for (Ant hormiga : colony.getHormigas()) {
            hormiga.setPosicionesActuales(estado.getPosicionesCamiones());
            // Añadir debugging para confirmar que las posiciones se están pasando
            System.out.println("💡 Hormiga #" + hormiga.getId() + " configurada con " +
                    estado.getPosicionesCamiones().size() + " posiciones de camiones");
        }

        // Construcción de soluciones por cada hormiga
        List<ACOSolution> soluciones = new ArrayList<>();
        ACOSolution mejorSolucionIteracion = null;
        double mejorCalidadIteracion = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < parameters.getNumeroHormigas(); i++) {
            // Construir solución con estado actual
            Ant hormiga = colony.getHormigas().get(i);

            // Configurar hormiga con posiciones actuales
            hormiga.setPosicionesActuales(estado.getPosicionesCamiones());

            // Si es una de las primeras hormigas, usar solución histórica
            if (i < 2 && !historicoSoluciones.isEmpty() && random.nextDouble() < factorAprendizaje) {
                ACOSolution solucionGuia = seleccionarSolucionHistoricaAleatoria();
                hormiga.setSolucionGuia(solucionGuia);
            }

            // Construir solución desde estado actual
            ACOSolution solucion = hormiga.construirSolucionDesdeEstadoActual(
                    estado.getPedidosPendientes(),
                    estado.getCamionesDisponibles(),
                    pheromonesMatrix,
                    heuristicCalculator,
                    estado.getTiempoActual(),
                    horizonteFinal,
                    grafo,
                    capacidadActualTanques
            );

            // Evaluar solución
            double calidad = evaluarSolucion(solucion, estado.getTiempoActual());
            solucion.setCalidad(calidad);
            soluciones.add(solucion);

            // Actualizar mejor solución
            if (calidad > mejorCalidadIteracion) {
                mejorCalidadIteracion = calidad;
                mejorSolucionIteracion = solucion;
            }
        }

        // Actualizar feromonas
        pheromonesMatrix.actualizarFeromonas(soluciones, parameters.getFactorEvaporacion());

        // Actualizar histórico
        if (mejorSolucionIteracion != null) {
            historicoSoluciones.add(mejorSolucionIteracion.clone());
            if (historicoSoluciones.size() > 10) {
                historicoSoluciones.remove(0);
            }
            actualizarMatrizFrecuencia(mejorSolucionIteracion);
        }

        return mejorSolucionIteracion;
    }

    /**
     * Guarda estado para resultado final
     */
    private void guardarEstadoParaResultado(List<Rutas> rutasCompletas, ACOSolution solucion, LocalDateTime tiempo) {
        List<Rutas> rutasConvertidas = convertirSolucionARutas(solucion);

        // Añadir a las rutas completas
        rutasCompletas.addAll(rutasConvertidas);
    }

    /**
     * Verifica si el sistema está en estado de colapso
     */
    private boolean detectarEstadoColapso(List<Pedido> pedidosPendientes, LocalDateTime tiempoActual) {
        if (pedidosPendientes.isEmpty()) {
            return false;
        }

        // Contar pedidos con plazo vencido
        long pedidosVencidos = pedidosPendientes.stream()
                .filter(p -> p.getFechaLimite().isBefore(tiempoActual))
                .count();

        double porcentajeVencidos = (double) pedidosVencidos / pedidosPendientes.size();

        return porcentajeVencidos > parameters.getUmbralColapso();
    }

    private void verificarEstadoCombustibleFlota() {
        for (Camion camion : mapa.getFlota()) {
            if (camion.getGalones() < parameters.getUmbralCombustibleCritico() &&
                    camion.getEstado() == EstadoCamion.DISPONIBLE) {
                System.out.println("⚠️ ALERTA: Camión " + camion.getId() +
                        " con nivel crítico de combustible: " + camion.getGalones() + " galones");
            }

            if (camion.getGalones() <= 0) {
                System.out.println("❌ ERROR: Camión " + camion.getId() +
                        " sin combustible, marcando como no disponible");
                camion.setEstado(EstadoCamion.MANTENIMIENTO);
            }
        }
    }

    /**
     * Ejecuta múltiples iteraciones de ACO dinámico para un horizonte específico
     */
    private ACOSolution ejecutarACODinamicoConMultiplesIteraciones(EstadoActual estadoInicial,
                                                                   LocalDateTime horizonteFinal) {
        // Persistir matriz de feromonas o crear una nueva
        if (this.pheromonesMatrix != null) {
            this.pheromonesMatrix.aplicarEvaporacionEntrePlanificaciones(factorEvaporacionEntrePlanificaciones);
            System.out.println("🧠 Utilizando matriz de feromonas persistida de replanificación anterior");
        } else {
            this.pheromonesMatrix = new PheromoneMatrix(grafo.getTotalNodos(), parameters.getFeromonaInicial());
            System.out.println("🆕 Inicializando nueva matriz de feromonas");
        }

        // Inicializar heurística
        this.heuristicCalculator = new HeuristicCalculator(grafo, parameters);

        // Mejor solución encontrada en todas las iteraciones
        ACOSolution mejorSolucionGlobalACO = null;
        double mejorCalidadGlobalACO = Double.NEGATIVE_INFINITY;

        // Estado actual del sistema (inicialmente el proporcionado)
        EstadoActual estadoActual = estadoInicial;

        // Ejecutar múltiples iteraciones de ACO
        for (int iterACO = 0; iterACO < parameters.getNumeroIteraciones(); iterACO++) {
            System.out.println("\n🐜 ITERACIÓN ACO: " + iterACO);

            // Actualizar heurística según estado actual
            heuristicCalculator.actualizarHeuristicaDinamica(
                    estadoActual.getPedidosPendientes(),
                    mapa.getBloqueos(),
                    estadoActual.getTiempoActual(),
                    capacidadActualTanques
            );

            // Configurar hormigas con posiciones actuales
            for (Ant hormiga : colony.getHormigas()) {
                hormiga.setPosicionesActuales(estadoActual.getPosicionesCamiones());
            }

            // Construcción de soluciones por cada hormiga
            List<ACOSolution> soluciones = new ArrayList<>();
            ACOSolution mejorSolucionIteracion = null;
            double mejorCalidadIteracion = Double.NEGATIVE_INFINITY;

            // Cada hormiga construye una solución
            for (int i = 0; i < parameters.getNumeroHormigas(); i++) {
                Ant hormiga = colony.getHormigas().get(i);

                // Si es una de las primeras hormigas, usar solución histórica
                if (i < 2 && !historicoSoluciones.isEmpty() && random.nextDouble() < factorAprendizaje) {
                    ACOSolution solucionGuia = seleccionarSolucionHistoricaAleatoria();
                    hormiga.setSolucionGuia(solucionGuia);
                }

                // Construir solución
                ACOSolution solucion = hormiga.construirSolucionDesdeEstadoActual(
                        estadoActual.getPedidosPendientes(),
                        estadoActual.getCamionesDisponibles(),
                        pheromonesMatrix,
                        heuristicCalculator,
                        estadoActual.getTiempoActual(),
                        horizonteFinal,
                        grafo,
                        capacidadActualTanques
                );

                // Evaluar solución
                double calidad = evaluarSolucion(solucion, estadoActual.getTiempoActual());
                solucion.setCalidad(calidad);
                soluciones.add(solucion);

                // Actualizar mejor solución de esta iteración
                if (calidad > mejorCalidadIteracion) {
                    mejorCalidadIteracion = calidad;
                    mejorSolucionIteracion = solucion;
                }
            }

            // Actualizar feromonas basado en soluciones de esta iteración
            pheromonesMatrix.actualizarFeromonas(soluciones, parameters.getFactorEvaporacion());

            // Aplicar búsqueda local a la mejor solución de esta iteración
            if (mejorSolucionIteracion != null && busquedaLocalActiva) {
                aplicarBusquedaLocal(mejorSolucionIteracion);
                // Re-evaluar después de la búsqueda local
                mejorSolucionIteracion.setCalidad(evaluarSolucion(mejorSolucionIteracion, estadoActual.getTiempoActual()));
            }

            // Actualizar mejor solución global
            if (mejorSolucionIteracion != null &&
                    mejorSolucionIteracion.getCalidad() > mejorCalidadGlobalACO) {
                mejorCalidadGlobalACO = mejorSolucionIteracion.getCalidad();
                mejorSolucionGlobalACO = mejorSolucionIteracion;

                System.out.println("🌟 Nueva mejor solución global encontrada - Calidad: " +
                        String.format("%.6f", mejorCalidadGlobalACO));
            }

            // Simulación de cambios dinámicos entre iteraciones
            estadoActual = simularCambiosDinamicos(estadoActual, iterACO);

            // Registrar solución para histórico
            if (mejorSolucionIteracion != null) {
                historicoSoluciones.add(mejorSolucionIteracion.clone());
                if (historicoSoluciones.size() > 10) {
                    historicoSoluciones.remove(0);
                }
                actualizarMatrizFrecuencia(mejorSolucionIteracion);
            }

            // Log de la iteración
            System.out.println("🔄 Iteración ACO " + iterACO + " - Calidad: " +
                    String.format("%.6f", mejorCalidadIteracion) +
                    " - Pedidos: " + (mejorSolucionIteracion != null ?
                    mejorSolucionIteracion.getNumeroPedidosAsignados() : 0) +
                    "/" + estadoActual.getPedidosPendientes().size());
        }

        // Log final de ACO
        if (mejorSolucionGlobalACO != null) {
            loggerACO.logIteracion(
                    iteracion,
                    mejorSolucionGlobalACO,
                    estadoInicial.getCamionesDisponibles(),
                    estadoInicial.getPedidosPendientes().size(),
                    System.currentTimeMillis() - tiempoInicioEjecucion
            );
            monitor.mostrarRutasDetalladas(mejorSolucionGlobalACO);
        }

        // Actualizar mejores soluciones globales para todo el algoritmo
        if (mejorSolucionGlobalACO != null && mejorSolucionGlobalACO.getCalidad() > mejorCalidadGlobal) {
            mejorSolucionGlobal = mejorSolucionGlobalACO;
            mejorCalidadGlobal = mejorSolucionGlobalACO.getCalidad();
        }

        return mejorSolucionGlobalACO;
    }

    /**
     * Simula cambios dinámicos en el sistema entre iteraciones de ACO
     * (nuevos pedidos, averías, etc.)
     */
    private EstadoActual simularCambiosDinamicos(EstadoActual estadoActual, int iteracion) {
        // Crear una copia del estado actual
        EstadoActual nuevoEstado = new EstadoActual(
                new HashMap<>(estadoActual.getPosicionesCamiones()),
                new ArrayList<>(estadoActual.getCamionesDisponibles()),
                new ArrayList<>(estadoActual.getPedidosPendientes()),
                estadoActual.getTiempoActual()
        );

        // 1. Simular llegada de nuevos pedidos (solo en algunas iteraciones para simular naturaleza estocástica)
        if (iteracion % 3 == 0) { // Cada 3 iteraciones
            // Generar pedidos sintéticos o usar pedidos reales con timestamps futuros
            List<Pedido> nuevosPedidos = simularNuevosPedidos(estadoActual.getTiempoActual());

            if (!nuevosPedidos.isEmpty()) {
                nuevoEstado.getPedidosPendientes().addAll(nuevosPedidos);
                System.out.println("📦 Simulación: " + nuevosPedidos.size() +
                        " nuevos pedidos en iteración ACO " + iteracion);
            }
        }

        // 2. Simular averías aleatorias (con probabilidad baja)
        if (random.nextDouble() < 0.05) { // 5% de probabilidad de avería
            Camion camionAveriado = simularAveriaCamion(nuevoEstado.getCamionesDisponibles());

            if (camionAveriado != null) {
                // Remover camión de disponibles
                nuevoEstado.getCamionesDisponibles().remove(camionAveriado);

                System.out.println("🔧 Simulación: Avería de camión " +
                        camionAveriado.getId() + " en iteración ACO " + iteracion);
            }
        }

        return nuevoEstado;
    }

    /**
     * Simula la llegada de nuevos pedidos
     */
    private List<Pedido> simularNuevosPedidos(LocalDateTime tiempo) {
        // Para testing, podemos usar pedidos reales con timestamps cercanos al futuro
        return mapa.getPedidos().stream()
                .filter(p -> p.getFechaRegistro().isAfter(tiempo) &&
                        p.getFechaRegistro().isBefore(tiempo.plusMinutes(30)) &&
                        !pedidosProcesados.contains(p.getIdPedido()))
                .peek(p -> pedidosProcesados.add(p.getIdPedido()))
                .collect(Collectors.toList());
    }

    /**
     * Simula una avería en un camión aleatorio
     */
    private Camion simularAveriaCamion(List<Camion> camionesDisponibles) {
        if (camionesDisponibles.isEmpty()) {
            return null;
        }

        // Seleccionar un camión aleatorio
        int idx = random.nextInt(camionesDisponibles.size());
        return camionesDisponibles.get(idx);
    }

    /**
     * Clase interna para representar estado actual del sistema
     */
    @Getter @Setter
    private static class EstadoActual {
        private Map<String, Ubicacion> posicionesCamiones;
        private List<Camion> camionesDisponibles;
        private List<Pedido> pedidosPendientes;
        private LocalDateTime tiempoActual;

        public EstadoActual(Map<String, Ubicacion> posicionesCamiones, List<Camion> camionesDisponibles,
                            List<Pedido> pedidosPendientes, LocalDateTime tiempoActual) {
            this.posicionesCamiones = posicionesCamiones;
            this.camionesDisponibles = camionesDisponibles;
            this.pedidosPendientes = pedidosPendientes;
            this.tiempoActual = tiempoActual;
        }
    }
}

