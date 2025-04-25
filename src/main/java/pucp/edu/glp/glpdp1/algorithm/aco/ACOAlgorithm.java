package pucp.edu.glp.glpdp1.algorithm.aco;

import lombok.Getter;
import lombok.Setter;
import pucp.edu.glp.glpdp1.algorithm.model.CamionAsignacion;
import pucp.edu.glp.glpdp1.algorithm.model.GrafoRutas;
import pucp.edu.glp.glpdp1.algorithm.model.Nodo;
import pucp.edu.glp.glpdp1.algorithm.model.Ruta;
import pucp.edu.glp.glpdp1.algorithm.utils.ACOLogger;
import pucp.edu.glp.glpdp1.algorithm.utils.ACOMonitor;
import pucp.edu.glp.glpdp1.algorithm.utils.AlgorithmUtils;
import pucp.edu.glp.glpdp1.algorithm.utils.DistanceCalculator;
import pucp.edu.glp.glpdp1.algorithm.utils.UrgencyCalculator;
import pucp.edu.glp.glpdp1.domain.Almacen;
import pucp.edu.glp.glpdp1.domain.Averia;
import pucp.edu.glp.glpdp1.domain.Bloqueo;
import pucp.edu.glp.glpdp1.domain.Camion;
import pucp.edu.glp.glpdp1.domain.Mapa;
import pucp.edu.glp.glpdp1.domain.Pedido;
import pucp.edu.glp.glpdp1.domain.Rutas;
import pucp.edu.glp.glpdp1.domain.Ubicacion;
import pucp.edu.glp.glpdp1.domain.enums.EstadoCamion;
import pucp.edu.glp.glpdp1.domain.enums.Incidente;
import pucp.edu.glp.glpdp1.domain.enums.TipoAlmacen;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Implementación del algoritmo de Colonia de Hormigas (ACO) para optimización de rutas
 * de distribución de GLP.
 *
 * Esta clase implementa los requisitos RF85-RF100 especificados en el proyecto.
 */
@Getter
@Setter
public class ACOAlgorithm {
    private static final Logger logger = Logger.getLogger(ACOAlgorithm.class.getName());

    // Parámetros del algoritmo
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

    // NUEVOS CAMPOS PARA MEJORAS
    // Historial de mejores soluciones por iteración
    private List<ACOSolution> historicoSoluciones = new ArrayList<>();
    // Matriz para almacenar frecuencia de uso de aristas en buenas soluciones
    private int[][] matrizFrecuenciaAristas;
    // Factor para controlar influencia de la búsqueda ogi
    private double factorBusquedaLocal = 0.8;
    // Factor para controlar influencia del aprendizaje entre iteraciones
    private double factorAprendizaje = 0.3;
    // Bandera para activar/desactivar búsqueda ogi
    private boolean busquedaLocalActiva = true;
    // Contador de iteraciones sin mejora global (más estricto que iterSinMejora)
    private int iteracionesSinMejoraGlobal;
    // Mejor calidad histórica
    private double mejorCalidadHistorica = Double.NEGATIVE_INFINITY;

    /**
     * Constructor principal del algoritmo
     * @param mapa Mapa con los datos de la ciudad, flota, pedidos, etc.
     */
    public ACOAlgorithm(Mapa mapa) {
        this.mapa = mapa;
        this.parameters = new ACOParameters();
        inicializarAlgoritmo();
    }

    /**
     * Constructor con parámetros personalizados
     * @param mapa Mapa con los datos de la ciudad, flota, pedidos, etc.
     * @param parameters Parámetros personalizados del algoritmo
     */
    public ACOAlgorithm(Mapa mapa, ACOParameters parameters) {
        this.mapa = mapa;
        this.parameters = parameters;
        inicializarAlgoritmo();
    }

    private void debug(String mensaje){
        System.out.println("[ACO] "+ mensaje);
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
     * RF86, RF88, RF96: Control de tanques intermedios
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
        int corregidos = 0;
        for (Camion camion : mapa.getFlota()) {
            if (camion.getEstado() == null) {
                camion.setEstado(EstadoCamion.DISPONIBLE);
                corregidos++;
            }
        }

        if (corregidos > 0) {
            System.out.println("⚠️ Se inicializaron " + corregidos +
                    " camiones que tenían estado null");
        }
    }

    private void debugAsignacion(Camion camion,List<Pedido> pedidos){
        System.out.println("🛻 ["+camion.getIdC()+"] Asignado con " + pedidos.size() + " pedidos:");
        for(Pedido p: pedidos){
            System.out.println("   📦 Pedido #" + p.getIdPedido() +
                    " - Volumen: " + p.getVolumen() + "m³" +
                    " - Destino: (" + p.getDestino().getX() + "," + p.getDestino().getY() + ")");
        }
    }

    /**
     * Método principal que ejecuta el algoritmo ACO
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
        // RF97: Detección de inconsistencias en datos
        if (detectarInconsistencias()) {
            logger.warning("Se detectaron inconsistencias en los datos de entrada");
        }

        LocalDateTime tiempoActual = mapa.getFechaInicio() != null ? mapa.getFechaInicio() : LocalDateTime.now();
        ultimaReplanificacion = tiempoActual;

        // RF99: Ajuste dinámico de frecuencia de replanificación
        ajustarFrecuenciaReplanificacion();

        // Parámetros adaptativos para búsqueda ogi
        double factorBusquedaLocalInicial = factorBusquedaLocal;

        while (iteracion < parameters.getNumeroIteraciones() && !estadoColapso) {
            // Verificar si toca replanificar
            if (ChronoUnit.MINUTES.between(ultimaReplanificacion, tiempoActual) >= frecuenciaReplanificacion) {
                ultimaReplanificacion = tiempoActual;
                ajustarFrecuenciaReplanificacion();
            }

            // RF90/RF91: Exclusión de camiones en mantenimiento
            actualizarEstadoCamionesMantenimiento(tiempoActual);

            // RF96: Relleno automático de tanques al empezar el día
            if (esInicioDeDia(tiempoActual)) {
                rellenarTanquesIntermedios();
            }

            // Verificar y actualizar eventos dinámicos (averías, bloqueos, etc.)
            boolean huboEventos = actualizarEventosDinamicos(tiempoActual);
            if (huboEventos) {
                iterSinMejora = 0;
            }

            // RF88: Verificar disponibilidad de combustible en tanques
            verificarDisponibilidadCombustible(tiempoActual);

            actualizarEstadoCamiones(tiempoActual);

            // MODIFICACIÓN: Ajustar estrategia de búsqueda ogi según progreso
            ajustarEstrategiaBusquedaLocal();

            // MODIFICACIÓN: Actualizar heurística con conocimiento histórico
            actualizarHeuristicaConConocimientoHistorico();

            // Actualizar heurística con información dinámica actual
            heuristicCalculator.actualizarHeuristicaDinamica(
                    mapa.getPedidos(),
                    mapa.getBloqueos(),
                    tiempoActual,
                    capacidadActualTanques
            );

            // RF95: Priorización por nivel de combustible
            List<Camion> camionesPriorizados = priorizarCamionesPorCombustible();

            camionesPriorizados = filtrarCamionesDisponibles(camionesPriorizados);

            // RF100: Gestión preventiva de inventario
            priorizarTanquesPorTiempoAgotamiento();

            // Construcción de soluciones por cada hormiga
            List<ACOSolution> soluciones = new ArrayList<>();
            ACOSolution mejorSolucionIteracion = null;
            double mejorCalidadIteracion = Double.NEGATIVE_INFINITY;

            for (int i = 0; i < parameters.getNumeroHormigas(); i++) {
                List<Camion> camionesHormiga = new ArrayList<>(camionesPriorizados);
                Map<TipoAlmacen,Double> capacidadTanquesHormiga = new HashMap<>(capacidadActualTanques);

                // Construir solución con una hormiga
                Ant hormiga = colony.getHormigas().get(i);

                // MODIFICACIÓN: Si es una de las primeras hormigas y hay histórico,
                // usar solución histórica como guía
                if (i < 2 && !historicoSoluciones.isEmpty() && random.nextDouble() < factorAprendizaje) {
                    ACOSolution solucionGuia = seleccionarSolucionHistoricaAleatoria();
                    hormiga.setSolucionGuia(solucionGuia);
                }

                // Construir solución
                ACOSolution solucion = hormiga.construirSolucion(
                        mapa.getPedidos(),
                        camionesHormiga,
                        pheromonesMatrix,
                        heuristicCalculator,
                        tiempoActual,
                        grafo,
                        capacidadTanquesHormiga
                );

                // NUEVA FUNCIONALIDAD: Aplicar búsqueda ogi
                if (busquedaLocalActiva && random.nextDouble() < factorBusquedaLocal) {
                    aplicarBusquedaLocal(solucion);
                }

                System.out.println("\n=== Hormiga #" + (i + 1) + "  - Iteración " + iteracion + " ===");
                if(solucion.getAsignaciones().isEmpty()){
                    System.out.println("⚠️ No se pudieron realizar asignaciones");
                }else{
                    for(CamionAsignacion asignacion: solucion.getAsignaciones()){
                        debugAsignacion(asignacion.getCamion(), asignacion.getPedidos());
                    }
                }

                if(!solucion.getPedidosNoAsignados().isEmpty()){
                    System.out.println("❌ " + solucion.getPedidosNoAsignados().size() +
                            " pedidos no pudieron asignarse");
                }

                // Evaluar calidad de la solución
                double calidad = evaluarSolucion(solucion, tiempoActual);
                solucion.setCalidad(calidad);
                soluciones.add(solucion);

                // Actualizar mejor solución de esta iteración
                if (calidad > mejorCalidadIteracion) {
                    mejorCalidadIteracion = calidad;
                    mejorSolucionIteracion = solucion;
                }

                // Actualizar mejor solución global
                if (calidad > mejorCalidadGlobal) {
                    mejorSolucionGlobal = solucion;
                    mejorCalidadGlobal = calidad;
                    iteracionesSinMejoraGlobal = 0;
                } else {
                    iteracionesSinMejoraGlobal++;
                }
            }

            // MODIFICACIÓN: Actualizar historial de soluciones
            if (mejorSolucionIteracion != null) {
                historicoSoluciones.add(mejorSolucionIteracion.clone());
                // Limitar el histórico a las últimas 10 iteraciones
                if (historicoSoluciones.size() > 10) {
                    historicoSoluciones.remove(0);
                }

                // Actualizar matriz de frecuencia de aristas
                actualizarMatrizFrecuencia(mejorSolucionIteracion);

                // Actualizar mejor calidad histórica
                if (mejorSolucionIteracion.getCalidad() > mejorCalidadHistorica) {
                    mejorCalidadHistorica = mejorSolucionIteracion.getCalidad();
                }
            }

            // Actualizar feromonas basado en las soluciones
            pheromonesMatrix.actualizarFeromonas(soluciones, parameters.getFactorEvaporacion());

            // MODIFICACIÓN: Intensificar feromonas en las mejores rutas históricas
            if (iteracion % 5 == 0 && !historicoSoluciones.isEmpty()) {
                intensificarFeromonasHistoricas();
            }

            // Verificar mejora y control de convergencia
            if (mejorCalidadGlobal > mejorCalidadAnterior) {
                iterSinMejora = 0;
            } else {
                iterSinMejora++;
            }
            mejorCalidadAnterior = mejorCalidadGlobal;

            // RF94: Detección de colapso del sistema
            if (detectarEstadoColapso(soluciones)) {
                logger.warning("ALERTA: Sistema en estado de colapso irreversible detectado");
                estadoColapso = true;
                break;
            }

            // Aplicar mecanismo anti-estancamiento si es necesario
            if (iterSinMejora >= parameters.getMaxIteracionesSinMejora()) {
                if (iteracion < parameters.getNumeroIteraciones() * parameters.getUmbralConvergenciaTemprana()) {
                    // Convergencia temprana: perturbar para escapar de óptimo ogi
                    pheromonesMatrix.perturbarFeromonas(parameters.getFeromonaInicial());
                    iterSinMejora = 0;

                    // MODIFICACIÓN: Aprovechar el conocimiento histórico tras perturbación
                    if (!historicoSoluciones.isEmpty() && random.nextDouble() < 0.5) {
                        incorporarConocimientoHistorico();
                    }
                } else {
                    // Convergencia tardía: asumir que se encontró buena solución
                    logger.info("Convergencia alcanzada en iteración " + iteracion);
                    break;
                }
            }

            // RF93: Generación de datos para visualización
            generarDatosVisualizacion(mejorSolucionGlobal, iteracion);
            long tiempoTranscurrido = System.currentTimeMillis() - tiempoInicioEjecucion;
            loggerACO.logIteracion(iteracion, mejorSolucionGlobal, camionesPriorizados,
                    mapa.getPedidos().size(), tiempoTranscurrido);
            monitor.registrarIteracion(iteracion, mejorSolucionGlobal,
                    mapa.getPedidos().size(), camionesPriorizados);
            iteracion++;

            // Avanzar tiempo para simulación
            if (mapa.getFechaInicio() != null) {
                tiempoActual = tiempoActual.plusMinutes(parameters.getTiempoAvanceSimulacion());
            }
        }

        logger.info("Algoritmo ACO finalizado después de " + iteracion + " iteraciones");

        // MODIFICACIÓN: Aplicar búsqueda ogi intensiva a la mejor solución final
        if (mejorSolucionGlobal != null) {
            logger.info("Aplicando búsqueda ogi intensiva a la mejor solución global");
            double factorOriginal = factorBusquedaLocal;
            factorBusquedaLocal = 1.0; // Máxima intensidad
            for (int i = 0; i < 10; i++) { // Múltiples iteraciones de mejora
                aplicarBusquedaLocal(mejorSolucionGlobal);
            }
            factorBusquedaLocal = factorOriginal;
        }

        // Mostrar la mejor solución encontrada
        System.out.println("\n✅ MEJOR SOLUCIÓN ENCONTRADA (Calidad: " +
                String.format("%.6f", mejorCalidadGlobal) + ")");
        if (mejorSolucionGlobal != null) {
            System.out.println("Total asignaciones: " + mejorSolucionGlobal.getAsignaciones().size());
            for (CamionAsignacion asignacion : mejorSolucionGlobal.getAsignaciones()) {
                debugAsignacion(asignacion.getCamion(), asignacion.getPedidos());
            }
            if (!mejorSolucionGlobal.getPedidosNoAsignados().isEmpty()) {
                System.out.println("❌ " + mejorSolucionGlobal.getPedidosNoAsignados().size() +
                        " pedidos no pudieron asignarse");
            }
        }

        // Generar informes finales
        long tiempoTotalMs = System.currentTimeMillis() - tiempoInicioEjecucion;
        loggerACO.logAsignacionDetallada(mejorSolucionGlobal);
        loggerACO.generarDiagnostico(mejorSolucionGlobal, mapa.getPedidos().size());
        monitor.analizarProblemas(mejorSolucionGlobal, mapa.getPedidos().size(), mapa.getFlota());
        loggerACO.guardarEstadisticas(mejorSolucionGlobal, mapa.getPedidos().size(), tiempoTotalMs);

        System.out.println("\n✅ ALGORITMO ACO FINALIZADO");
        System.out.println("Tiempo total: " + String.format("%.2f", tiempoTotalMs / 1000.0) + " segundos");
        System.out.println("Archivos de resultados generados en carpeta logs/aco/");
        return convertirSolucionARutas(mejorSolucionGlobal);
    }

    /**
     * RF97: Detección de inconsistencias en los datos de entrada
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
                    for(Ubicacion tramo : bloqueo.getTramos()){
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
                errores.add("Camión " + camion.getIdC() + ": Inconsistencia en pesos o capacidades");
                inconsistenciasDetectadas = true;
            }

            // Verificar galones no negativos
            if (camion.getGalones() < 0) {
                errores.add("Camión " + camion.getIdC() + ": Galones negativos");
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
                logger.info("Camión " + camion.getIdC() + " no disponible por mantenimiento preventivo");
            }
            // Si está en mantenimiento correctivo por avería previa
            else if (estaEnMantenimientoCorrectivo(camion, tiempoActual)) {
                camion.setAveriado(true);
                logger.info("Camión " + camion.getIdC() + " no disponible por mantenimiento correctivo");
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
        String idNum = camion.getIdC().substring(2); // Extraer número del ID (ej: "TA01" -> "01")
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
     * RF96: Relleno automático de tanques intermedios al inicio del día
     */
    private void rellenarTanquesIntermedios() {
        for (Almacen almacen : mapa.getAlmacenes()) {
            if (almacen.getTipoAlmacen() != TipoAlmacen.CENTRAL) {
                capacidadActualTanques.put(almacen.getTipoAlmacen(), almacen.getCapacidadEfectivaM3());
                logger.info("Tanque " + almacen.getTipoAlmacen() + " recargado a " + almacen.getCapacidadEfectivaM3() + "m3");
            }
        }
    }

    /**
     * Verifica si es el inicio de un nuevo día (00:00)
     */
    private boolean esInicioDeDia(LocalDateTime tiempo) {
        return tiempo.getHour() == 0 && tiempo.getMinute() == 0;
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

    /**
     * RF100: Gestión preventiva de inventario - priorizar tanques por tiempo de agotamiento
     */
    private void priorizarTanquesPorTiempoAgotamiento() {
        // En una implementación real, se calcularía la tasa de consumo para estimar tiempo hasta agotamiento
        // Simulación básica: priorizar según capacidad actual

        // Esta información se usará al seleccionar tanques para reabastecimiento
        // Se implementa a través del método encontrarTanqueMasConveniente en la clase Ant
    }

    /**
     * Actualiza eventos dinámicos como averías, bloqueos, etc.
     * @return true si hubo cambios en el estado del sistema
     */
    private boolean actualizarEventosDinamicos(LocalDateTime tiempoActual) {
        boolean huboEventos = false;

        // Actualizar estado de bloqueos (activar/desactivar según tiempo)
        for (Bloqueo bloqueo : mapa.getBloqueos()) {
            boolean estaBloqueadoAhora = estaActivoEnTiempo(bloqueo, tiempoActual);
            // Si cambió el estado, hay un evento
            if (estaBloqueadoAhora != estaActivoEnTiempo(bloqueo, tiempoActual.minusMinutes(1))) {
                huboEventos = true;
                if (estaBloqueadoAhora) {
                    logger.info("Activado bloqueo en tiempo " + tiempoActual);
                    debug("Activado bloqueo en tiempo: "+ tiempoActual);
                } else {
                    logger.info("Desactivado bloqueo en tiempo " + tiempoActual);
                    debug("Desactivado bloqueo en tiempo: "+ tiempoActual);
                }
            }
        }

        // Procesar averías programadas
        if (mapa.getAverias() != null) {
            for (Averia averia : mapa.getAverias()) {
                // Si la avería ocurre en este momento
                if (averia.getFechaIncidente() != null &&
                        Math.abs(ChronoUnit.MINUTES.between(averia.getFechaIncidente(), tiempoActual)) < parameters.getTiempoAvanceSimulacion()) {

                    String codigoCamion = averia.getCodigo();

                    // Buscar el camión correspondiente
                    for (Camion camion : mapa.getFlota()) {
                        if (camion.getIdC().equals(codigoCamion)) {
                            // Marcar camión como averiado si no lo estaba ya
                            if (!camion.isAveriado()) {
                                camion.setAveriado(true);
                                huboEventos = true;
                                logger.info("Camión " + codigoCamion + " ha sufrido una avería tipo " + averia.getIncidente());
                            }
                            break;
                        }
                    }
                }
            }
        }

        return huboEventos;
    }

    /**
     * Actualiza el estado de los camiones según mantenimiento o averías
     */
    private void actualizarEstadoCamiones(LocalDateTime tiempoActual) {
        for(Camion camion: mapa.getFlota()){
            if(estaEnMantenimientoPreventivo(camion,tiempoActual)){
                camion.setEstado(EstadoCamion.MANTENIMIENTO);
                logger.info("Camión "+ camion.getIdC() + " no disponible por mantenimiento preventivo");
                continue;
            }

            if(camion.isAveriado()){
                camion.setEstado(EstadoCamion.MANTENIMIENTO);
                logger.info("Camión "+ camion.getIdC() + " no disponible por mantenimiento correctivo");
                continue;
            }

            camion.setEstado(EstadoCamion.DISPONIBLE);
        }
    }

    private List<Camion> filtrarCamionesDisponibles(List<Camion> camiones){
        return camiones.stream()
                .filter(camion -> {
                    // Si el estado es null, considerarlo como no disponible
                    if (camion.getEstado() == null) {
                        // Arreglo en tiempo de ejecución: inicializar estado
                        camion.setEstado(EstadoCamion.DISPONIBLE);
                        System.out.println("⚠️ Camión " + camion.getIdC() +
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
     * RF99: Ajuste dinámico de frecuencia de replanificación según densidad de pedidos
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

        // Evaluar cada asignación camión-pedidos-ruta
        for (CamionAsignacion asignacion : solucion.getAsignaciones()) {
            Camion camion = asignacion.getCamion();
            List<Pedido> pedidos = asignacion.getPedidos();
            List<Ruta> rutas = asignacion.getRutas();

            // Calcular consumo para esta asignación
            double pesoInicial = camion.getPesoBrutoTon() + AlgorithmUtils.calcularPesoCargaTotal(pedidos);
            double pesoActual = pesoInicial;
            LocalDateTime tiempoEstimado = tiempoActual;

            for (Ruta ruta : rutas) {
                double distancia = ruta.getDistancia();

                // RF87: Cálculo dinámico de consumo de combustible
                double consumoRuta = (distancia * pesoActual) / 180.0;
                consumoTotal += consumoRuta;

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
        return 1.0 / (1.0 + consumoTotal + penalizacionTiempo + penalizacionRestricciones);
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
     * RF93: Genera datos para visualización del sistema
     */
    private void generarDatosVisualizacion(ACOSolution solucion, int iteracion) {
        if (solucion == null) {
            return;
        }

        // En una implementación real, esta información se enviaría a un componente de visualización
        // Para este ejemplo, simplemente registramos información relevante

        logger.info("Iteración " + iteracion + " - Calidad: " + String.format("%.6f", solucion.getCalidad()));
        logger.info("Pedidos asignados: " + (mapa.getPedidos().size() - solucion.getPedidosNoAsignados().size()) +
                " de " + mapa.getPedidos().size());

        // Calcular estadísticas
        double distanciaTotal = solucion.getAsignaciones().stream()
                .flatMap(a -> a.getRutas().stream())
                .mapToDouble(Ruta::getDistancia)
                .sum();

        logger.info("Distancia total estimada: " + String.format("%.2f", distanciaTotal) + " km");

        // En un entorno real, los datos se transmitirían a un módulo de visualización gráfica
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

            // Convertir las rutas de la solución a lista de ubicaciones
            List<Ubicacion> ubicaciones = new ArrayList<>();
            for (Ruta ruta : asignacion.getRutas()) {
                ubicaciones.add(ruta.getOrigen());
                // La última ruta añade también el destino
                if (asignacion.getRutas().indexOf(ruta) == asignacion.getRutas().size() - 1) {
                    ubicaciones.add(ruta.getDestino());
                }
            }

            rutaEntity.setUbicaciones(ubicaciones);

            // Calcular distancia total
            double distanciaTotal = asignacion.getRutas().stream()
                    .mapToDouble(Ruta::getDistancia)
                    .sum();
            rutaEntity.setDistanciaTotal(distanciaTotal);

            // Calcular tiempo total (considerando velocidad promedio y tiempos de carga/descarga)
            double tiempoHoras = distanciaTotal / parameters.getVelocidadPromedio();
            // Añadir 15 min por cada entrega (convertido a horas)
            tiempoHoras += asignacion.getPedidos().size() * (15.0 / 60.0);
            // Añadir 15 min de mantenimiento rutinario al final
            tiempoHoras += 15.0 / 60.0;

            rutaEntity.setTiempoTotal(tiempoHoras);

            // Calcular consumo total
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
        for (Almacen almacen : mapa.getAlmacenes()) {
            if (almacen.getTipoAlmacen() == TipoAlmacen.CENTRAL) {
                return almacen.getUbicacion();
            }
        }
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
     * Actualiza la heurística incorporando conocimiento histórico
     */
    private void actualizarHeuristicaConConocimientoHistorico() {
        // Solo aplicar si hay información histórica
        if (historicoSoluciones.isEmpty() || matrizFrecuenciaAristas == null) {
            return;
        }

        // Factor de influencia del conocimiento histórico
        double factorInfluencia = factorAprendizaje * (1.0 - (double)iteracion / parameters.getNumeroIteraciones());

        // Obtener la matriz heurística original
        double[][] matrizHeuristicaOriginal = heuristicCalculator.getMatrizHeuristicaActual();

        // Crear copia para modificar
        double[][] matrizModificada = new double[matrizHeuristicaOriginal.length][];
        for (int i = 0; i < matrizHeuristicaOriginal.length; i++) {
            matrizModificada[i] = matrizHeuristicaOriginal[i].clone();
        }

        // Aplicar conocimiento histórico
        for (int i = 0; i < matrizFrecuenciaAristas.length; i++) {
            for (int j = 0; j < matrizFrecuenciaAristas[i].length; j++) {
                if (matrizFrecuenciaAristas[i][j] > 0 &&
                        i < matrizModificada.length &&
                        j < matrizModificada[i].length) {

                    // Incrementar heurística según frecuencia histórica
                    double incremento = Math.log(1 + matrizFrecuenciaAristas[i][j]) * factorInfluencia;
                    matrizModificada[i][j] *= (1.0 + incremento);
                }
            }
        }

        // Establecer matriz modificada
        heuristicCalculator.setMatrizHeuristicaActual(matrizModificada);
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

    /**
     * Intensifica feromonas en las mejores rutas históricas
     */
    private void intensificarFeromonasHistoricas() {
        if (historicoSoluciones.isEmpty()) {
            return;
        }

        // Seleccionar la mejor solución histórica
        ACOSolution mejorHistorica = historicoSoluciones.stream()
                .max(Comparator.comparingDouble(ACOSolution::getCalidad))
                .orElse(null);

        if (mejorHistorica == null) {
            return;
        }

        // Factor de intensificación
        double factorIntensificacion = 2.0;

        // Para cada asignación en la mejor solución
        for (CamionAsignacion asignacion : mejorHistorica.getAsignaciones()) {
            // Para cada ruta
            for (Ruta ruta : asignacion.getRutas()) {
                // Obtener nodos origen y destino
                int origen = calcularIdNodo(ruta.getOrigen());
                int destino = calcularIdNodo(ruta.getDestino());

                // Intensificar feromona
                if (origen >= 0 && destino >= 0 &&
                        origen < pheromonesMatrix.getTamanio() &&
                        destino < pheromonesMatrix.getTamanio()) {

                    double valorActual = pheromonesMatrix.getValor(origen, destino);
                    pheromonesMatrix.setValor(origen, destino, valorActual * factorIntensificacion);
                    pheromonesMatrix.setValor(destino, origen, valorActual * factorIntensificacion);
                }
            }
        }
    }

    /**
     * Ajusta la estrategia de búsqueda ogi según el progreso del algoritmo
     */
    private void ajustarEstrategiaBusquedaLocal() {
        // Calcular progreso (0 a 1)
        double progreso = (double)iteracion / parameters.getNumeroIteraciones();

        // En etapas iniciales, menos búsqueda ogi para favorecer diversificación
        if (progreso < 0.3) {
            factorBusquedaLocal = 0.4;
        }
        // En etapas intermedias, búsqueda ogi moderada
        else if (progreso < 0.7) {
            factorBusquedaLocal = 0.7;
        }
        // En etapas finales, intensa búsqueda ogi para refinar soluciones
        else {
            factorBusquedaLocal = 0.9;
        }

        // Si llevamos muchas iteraciones sin mejora, intensificar búsqueda ogi
        if (iterSinMejora > parameters.getMaxIteracionesSinMejora() / 2) {
            factorBusquedaLocal = Math.min(1.0, factorBusquedaLocal + 0.2);
        }
    }

    /**
     * Incorpora conocimiento histórico tras una perturbación
     */
    private void incorporarConocimientoHistorico() {
        if (historicoSoluciones.isEmpty()) {
            return;
        }

        // Tomar una solución buena aleatoria del histórico
        int indice = new Random().nextInt(Math.min(historicoSoluciones.size(), 3));
        ACOSolution solucionHistorica = historicoSoluciones.get(indice);

        logger.info("Incorporando conocimiento de solución histórica con calidad " +
                String.format("%.6f", solucionHistorica.getCalidad()));

        // Reforzar feromonas en rutas de la solución histórica
        for (CamionAsignacion asignacion : solucionHistorica.getAsignaciones()) {
            for (Ruta ruta : asignacion.getRutas()) {
                int origen = calcularIdNodo(ruta.getOrigen());
                int destino = calcularIdNodo(ruta.getDestino());

                // Asegurar que los IDs están dentro del rango
                if (origen >= 0 && destino >= 0 &&
                        origen < pheromonesMatrix.getTamanio() &&
                        destino < pheromonesMatrix.getTamanio()) {

                    // Reforzar feromona
                    double valorActual = pheromonesMatrix.getValor(origen, destino);
                    pheromonesMatrix.setValor(origen, destino, valorActual * 2.0);
                    pheromonesMatrix.setValor(destino, origen, valorActual * 2.0);
                }
            }
        }
    }

    // Para compatibilidad con algunas funciones de la clase Ant
    private Random random = new Random();
}