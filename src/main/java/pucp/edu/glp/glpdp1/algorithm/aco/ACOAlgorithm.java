package pucp.edu.glp.glpdp1.algorithm.aco;

import lombok.Getter;
import lombok.Setter;
import pucp.edu.glp.glpdp1.algorithm.model.CamionAsignacion;
import pucp.edu.glp.glpdp1.algorithm.model.GrafoRutas;
import pucp.edu.glp.glpdp1.algorithm.model.Ruta;
import pucp.edu.glp.glpdp1.algorithm.utils.AlgorithmUtils;
import pucp.edu.glp.glpdp1.domain.Almacen;
import pucp.edu.glp.glpdp1.domain.Averia;
import pucp.edu.glp.glpdp1.domain.Bloqueo;
import pucp.edu.glp.glpdp1.domain.Camion;
import pucp.edu.glp.glpdp1.domain.Mapa;
import pucp.edu.glp.glpdp1.domain.Pedido;
import pucp.edu.glp.glpdp1.domain.Rutas;
import pucp.edu.glp.glpdp1.domain.Ubicacion;
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

    // Estructuras para el control de tanques intermedios
    private Map<TipoAlmacen, Double> capacidadActualTanques;

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

    private void debugAsignacion(Camion camion, List<Pedido> pedidos) {
        System.out.println("🛻 [" + camion.getIdC() + "] Asignado con " + pedidos.size() + " pedidos:");
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

        // RF97: Detección de inconsistencias en datos
        if (detectarInconsistencias()) {
            logger.warning("Se detectaron inconsistencias en los datos de entrada");
        }

        LocalDateTime tiempoActual = mapa.getFechaInicio() != null ? mapa.getFechaInicio() : LocalDateTime.now();
        ultimaReplanificacion = tiempoActual;

        // RF99: Ajuste dinámico de frecuencia de replanificación
        ajustarFrecuenciaReplanificacion();

        // Añade estas variables para medir rendimiento
        long tiempoInicio = System.currentTimeMillis();
        int mejorPedidosAsignados = 0;

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

            // Actualizar heurística con información dinámica actual
            heuristicCalculator.actualizarHeuristicaDinamica(
                    mapa.getPedidos(),
                    mapa.getBloqueos(),
                    tiempoActual,
                    capacidadActualTanques
            );

            List<Camion> camionesPriorizados = priorizarCamionesPorCombustible();
            // Para reporte de diagnóstico
            int totalPedidosAsignados = 0;
            int hormigasActivas = 0;

            priorizarTanquesPorTiempoAgotamiento();

            // Construcción de soluciones por cada hormiga
            List<ACOSolution> soluciones = new ArrayList<>();

            for (int i = 0; i < parameters.getNumeroHormigas(); i++) {
                // Construir solución con una hormiga
                Ant hormiga = colony.getHormigas().get(i);

                // Copia fresca para cada hormiga para evitar dañar el mismo recurso
                List<Camion> camionesCopia = new ArrayList<>(camionesPriorizados);

                // RF85: Agrupamiento inteligente de entregas
                ACOSolution solucion = hormiga.construirSolucion(
//                        mapa.getPedidos(),
                        new ArrayList<>(mapa.getPedidos()),
                        camionesCopia,
                        pheromonesMatrix,
                        heuristicCalculator,
                        tiempoActual,
                        grafo,
//                        capacidadActualTanques
                        new HashMap<>(capacidadActualTanques)
                );

                // Diagnóstico
                int pedidosAsignados = solucion.getNumeroPedidosAsignados();
                if (pedidosAsignados > 0) {
                    hormigasActivas++;
                }
                totalPedidosAsignados += pedidosAsignados;

                System.out.println("\n=== Hormiga #" + (i + 1) + "  - Iteración " + iteracion + " ===");
                if (solucion.getAsignaciones().isEmpty()) {
                    System.out.println("⚠️ No se pudieron realizar asignaciones");
                } else {
                    for (CamionAsignacion asignacion : solucion.getAsignaciones()) {
                        debugAsignacion(asignacion.getCamion(), asignacion.getPedidos());
                    }
                }

                if (!solucion.getPedidosNoAsignados().isEmpty()) {
                    System.out.println("❌ " + solucion.getPedidosNoAsignados().size() +
                            " pedidos no pudieron asignarse");
                }

                // Evaluar calidad de la solución
                double calidad = evaluarSolucion(solucion, tiempoActual);
                solucion.setCalidad(calidad);
                soluciones.add(solucion);

                // Actualizar mejor solución global
                if (calidad > mejorCalidadGlobal) {
                    mejorSolucionGlobal = solucion;
                    mejorCalidadGlobal = calidad;
                }
            }

            // Actualizar feromonas basado en las soluciones
            pheromonesMatrix.actualizarFeromonas(soluciones, parameters.getFactorEvaporacion());

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
                    // Convergencia temprana: perturbar para escapar de óptimo local
                    pheromonesMatrix.perturbarFeromonas(parameters.getFeromonaInicial());
                    iterSinMejora = 0;
                } else {
                    // Convergencia tardía: asumir que se encontró buena solución
                    logger.info("Convergencia alcanzada en iteración " + iteracion);
                    break;
                }
            }

            // RF93: Generación de datos para visualización
            generarDatosVisualizacion(mejorSolucionGlobal, iteracion);

            iteracion++;

            // Avanzar tiempo para simulación
            if (mapa.getFechaInicio() != null) {
                tiempoActual = tiempoActual.plusMinutes(parameters.getTiempoAvanceSimulacion());
            }

            // Imprimir diagnóstico
            System.out.println("=== DIAGNÓSTICO ACO ===");
            System.out.println("Hormigas activas: " + hormigasActivas + "/" + parameters.getNumeroHormigas());
            System.out.println("Pedidos asignados total: " + totalPedidosAsignados);
            System.out.println("Promedio pedidos por hormiga: " +
                    (hormigasActivas > 0 ? totalPedidosAsignados/hormigasActivas : 0));
        }

        logger.info("Algoritmo ACO finalizado después de " + iteracion + " iteraciones");
        // Mostrar la mejor solución encontrada
        System.out.println("\n✅ MEJOR SOLUCIÓN ENCONTRADA (Calidad: " +
                String.format("%.6f", mejorCalidadGlobal)  + ")");
        if (mejorSolucionGlobal != null) {
            System.out.println("Total asignaciones: " + mejorSolucionGlobal.getAsignaciones().size());
            for (CamionAsignacion asignacion : mejorSolucionGlobal.getAsignaciones()) {
                debugAsignacion(asignacion.getCamion(), asignacion.getPedidos());
                // Mostramos también la ruta planificada
                System.out.println(" - Ruta: " + asignacion.getRutas().stream());
            }
            if (!mejorSolucionGlobal.getPedidosNoAsignados().isEmpty()) {
                System.out.println("❌ " + mejorSolucionGlobal.getPedidosNoAsignados().size() +
                        " pedidos no pudieron asignarse");
            }
        }
        return convertirSolucionARutas(mejorSolucionGlobal);
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
        return u1.getX() == u2.getX() || u1.getY() == u2.getY();
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
     *
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
                    debug("Activado bloqueo en tiempo: " + tiempoActual);
                } else {
                    logger.info("Desactivado bloqueo en tiempo " + tiempoActual);
                    debug("Desactivado bloqueo en tiempo: " + tiempoActual);
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

    private String formatearTiempo(long milisegundos) {
        long segundos = milisegundos / 1000;
        long minutos = segundos / 60;
        segundos = segundos % 60;

        return String.format("%d min %d seg", minutos, segundos);
    }
}