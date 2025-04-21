package pucp.edu.glp.glpdp1.aco;

import pucp.edu.glp.glpdp1.models.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Clase principal que implementa el algoritmo híbrido de colonia de hormigas para optimización de rutas
 * Versión actualizada con requisitos RF85-RF100
 */
public class AlgoritmoACO {
    // Parámetros del algoritmo
    private int numeroHormigas;
    private int numeroIteraciones;
    private int maxIteracionesSinMejora;
    private double umbralConvergenciaTemprana;
    private double factorEvaporacion;
    private double alfa;
    private double beta;
    private double q0;
    private double feromonaInicial;
    private double umbralColapsoSistema; // RF94: Umbral para detección de colapso

    // Componentes del sistema
    private Grafo grafo;
    private double[][] feromonas;
    private double[][] heuristicaBase;
    private double[][] heuristicaActual;
    private Mapa mapa;
    private Flota flota;
    private List<Pedido> pedidos;
    private List<Bloqueo> bloqueos;
    private Map<String, Almacen> almacenes;
    private LocalDateTime momentoActual;
    private GestorEventos gestorEventos;
    private EvaluadorSoluciones evaluador;
    private GestorTanques gestorTanques; // RF86, RF88, RF96, RF100
    private OptimizadorSecuencia optimizador; // RF85, RF98
    private ValidadorDatos validador; // RF97
    private int frecuenciaPlanificacion; // RF99: Para ajuste dinámico

    // Variables de control
    private Solucion mejorSolucionGlobal;
    private double mejorCalidadGlobal;
    private double mejorCalidadAnterior;
    private double indicadorColapso; // RF94: Indicador de colapso del sistema

    /**
     * Constructor con los parámetros del algoritmo
     */
    public AlgoritmoACO() {
        // Configuración por defecto
        this.numeroHormigas = 30;
        this.numeroIteraciones = 500;
        this.maxIteracionesSinMejora = 50;
        this.umbralConvergenciaTemprana = 0.6;
        this.factorEvaporacion = 0.5;
        this.alfa = 1.0;
        this.beta = 2.0;
        this.q0 = 0.9;
        this.feromonaInicial = 0.1;
        this.umbralColapsoSistema = 0.8; // RF94: Valor umbral para colapso
        this.almacenes = new HashMap<>();
        this.momentoActual = LocalDateTime.now();
        this.frecuenciaPlanificacion = 60; // Minutos entre replanificaciones
        this.indicadorColapso = 0.0;
    }

    /**
     * Configura el algoritmo con los elementos del sistema
     */
    public void configurar(Mapa mapa, Flota flota, List<Pedido> pedidos, List<Bloqueo> bloqueos,
                           List<MantenimientoPreventivo> mantenimientos, LocalDateTime momentoActual) {
        this.mapa = mapa;
        this.flota = flota;
        this.pedidos = pedidos;
        this.bloqueos = bloqueos;
        this.momentoActual = momentoActual;

        // Inicializar componentes adicionales
        this.validador = new ValidadorDatos(mapa);
        this.gestorTanques = new GestorTanques();
        this.optimizador = new OptimizadorSecuencia();

        // Validar datos de entrada (RF97)
        boolean datosValidos = validador.validarDatos(flota, pedidos, bloqueos, mantenimientos);
        if (!datosValidos) {
            System.out.println("ADVERTENCIA: Existen inconsistencias en los datos de entrada");
            // Podríamos añadir lógica para corregir automáticamente algunos problemas
        }

        // RF96: Rellenar tanques intermedios al inicio del día
        gestorTanques.rellenarTanquesIntermedios();

        // Inicializar grafo de la ciudad
        this.grafo = new Grafo(mapa.getAncho(), mapa.getAlto());

        // Mapear almacenes por su tipo
        for (Almacen almacen : mapa.getAlmacenes()) {
            this.almacenes.put(almacen.getTipo().toString(), almacen);
        }

        // Crear matriz de feromonas y matrices heurísticas
        int tamanio = (grafo.getAncho() + 1) * (grafo.getAlto() + 1);
        this.feromonas = new double[tamanio][tamanio];
        this.heuristicaBase = new double[tamanio][tamanio];
        this.heuristicaActual = new double[tamanio][tamanio];

        // Inicializar matrices
        inicializarMatrizFeromonas();
        calcularMatrizHeuristicaBase();

        // Inicializar gestores
        this.gestorEventos = new GestorEventos(this.bloqueos);

        // Registrar mantenimientos programados
        for (MantenimientoPreventivo mantenimiento : mantenimientos) {
            this.gestorEventos.registrarMantenimiento(mantenimiento);
        }

        this.evaluador = new EvaluadorSoluciones(this.mapa, this.momentoActual);

        // Inicializar variables de control
        this.mejorSolucionGlobal = new Solucion();
        this.mejorCalidadGlobal = 0.0;
        this.mejorCalidadAnterior = 0.0;
        this.indicadorColapso = 0.0;
    }

    /**
     * Inicializa la matriz de feromonas con un valor inicial
     */
    private void inicializarMatrizFeromonas() {
        for (int i = 0; i < feromonas.length; i++) {
            for (int j = 0; j < feromonas[i].length; j++) {
                Coordenada origen = indiceACoordenada(i);
                Coordenada destino = indiceACoordenada(j);

                // Verificar si son nodos adyacentes
                if (grafo.sonAdyacentes(origen, destino)) {
                    feromonas[i][j] = feromonaInicial;
                    feromonas[j][i] = feromonaInicial; // Grafo no dirigido
                }
            }
        }
    }

    /**
     * Calcula la matriz heurística base según distancias Manhattan
     */
    private void calcularMatrizHeuristicaBase() {
        for (int i = 0; i < heuristicaBase.length; i++) {
            for (int j = 0; j < heuristicaBase[i].length; j++) {
                Coordenada origen = indiceACoordenada(i);
                Coordenada destino = indiceACoordenada(j);

                // Verificar si son nodos adyacentes
                if (grafo.sonAdyacentes(origen, destino)) {
                    double distancia = origen.distancia(destino);
                    if (distancia > 0) {
                        heuristicaBase[i][j] = 1.0 / distancia;
                        heuristicaBase[j][i] = 1.0 / distancia; // Grafo no dirigido
                    } else {
                        heuristicaBase[i][j] = 1.0;
                        heuristicaBase[j][i] = 1.0;
                    }
                }
            }
        }
    }

    /**
     * Convierte un índice en la matriz a una coordenada
     */
    private Coordenada indiceACoordenada(int indice) {
        int ancho = grafo.getAncho() + 1;
        int x = indice % ancho;
        int y = indice / ancho;
        return new Coordenada(x, y);
    }

    /**
     * Convierte una coordenada a un índice en la matriz
     */
    private int coordenadaAIndice(Coordenada c) {
        int ancho = grafo.getAncho() + 1;

        if (c.getX() < 0 || c.getX() > grafo.getAncho() ||
                c.getY() < 0 || c.getY() > grafo.getAlto()) {
            return -1;
        }

        return c.getY() * ancho + c.getX();
    }

    /**
     * Actualiza la heurística con información dinámica actual
     */
    private void actualizarHeuristicaDinamica() {
        // Copiar la heurística base
        for (int i = 0; i < heuristicaBase.length; i++) {
            for (int j = 0; j < heuristicaBase[i].length; j++) {
                heuristicaActual[i][j] = heuristicaBase[i][j];
            }
        }

        // Ajustar según bloqueos actuales
        for (int i = 0; i < heuristicaActual.length; i++) {
            Coordenada origen = indiceACoordenada(i);

            for (int j = 0; j < heuristicaActual[i].length; j++) {
                Coordenada destino = indiceACoordenada(j);

                // Verificar si hay bloqueo entre origen y destino
                if (mapa.estaBloqueado(origen, destino, momentoActual)) {
                    heuristicaActual[i][j] = 0.0001; // Valor muy pequeño para evitar selección
                    heuristicaActual[j][i] = 0.0001;
                }
            }
        }

        // Ajustar según urgencia de pedidos
        for (Pedido pedido : pedidos) {
            if (pedido.getEstado() == Pedido.EstadoPedido.PENDIENTE) {
                Coordenada ubicacionPedido = pedido.getUbicacion();
                int indicePedido = coordenadaAIndice(ubicacionPedido);

                // Si el pedido está fuera de la rejilla, continuar
                if (indicePedido < 0) {
                    continue;
                }

                // Calcular urgencia normalizada (0 a 1, mayor es más urgente)
                LocalDateTime tiempoLimite = pedido.getFechaPedido().plus(pedido.getTiempoLimite());
                LocalDateTime ahora = momentoActual;

                // Calculamos cuántas horas quedan hasta el límite
                long horasRestantes = java.time.Duration.between(ahora, tiempoLimite).toHours();

                // Urgencia normalizada: 1 para pedidos vencidos, 0.1 para pedidos con +24h de margen
                double urgencia = 1.0 - Math.min(1.0, Math.max(0.1, horasRestantes / 24.0));

                // Aumentar heurística hacia pedidos urgentes
                for (int i = 0; i < heuristicaActual.length; i++) {
                    Coordenada nodo = indiceACoordenada(i);

                    // Si está cerca del pedido
                    if (nodo.distancia(ubicacionPedido) < 10) {
                        for (int j = 0; j < heuristicaActual[i].length; j++) {
                            Coordenada vecino = indiceACoordenada(j);

                            // Si el vecino está más cerca del pedido que el nodo actual
                            if (vecino.distancia(ubicacionPedido) < nodo.distancia(ubicacionPedido)) {
                                heuristicaActual[i][j] *= (1 + urgencia);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Actualiza las feromonas basado en las soluciones encontradas
     */
    private void actualizarFeromonas(List<Solucion> soluciones) {
        // Evaporación global de feromonas
        for (int i = 0; i < feromonas.length; i++) {
            for (int j = 0; j < feromonas[i].length; j++) {
                feromonas[i][j] *= (1 - factorEvaporacion);
            }
        }

        // Depósito de feromonas proporcional a la calidad
        for (Solucion solucion : soluciones) {
            double calidad = solucion.getCalidad();

            // Factor de depósito base proporcional a la calidad
            double factorDeposito = calidad * 10;

            // Para cada asignación en la solución
            for (Asignacion asignacion : solucion.getAsignaciones()) {
                List<Coordenada> ruta = asignacion.getRuta();

                // Para cada segmento de la ruta
                for (int i = 0; i < ruta.size() - 1; i++) {
                    Coordenada origen = ruta.get(i);
                    Coordenada destino = ruta.get(i + 1);

                    int indiceOrigen = coordenadaAIndice(origen);
                    int indiceDestino = coordenadaAIndice(destino);

                    // Si las coordenadas están dentro del rango válido
                    if (indiceOrigen >= 0 && indiceDestino >= 0 &&
                            indiceOrigen < feromonas.length && indiceDestino < feromonas[0].length) {
                        // Depósito de feromona
                        feromonas[indiceOrigen][indiceDestino] += factorDeposito;
                        feromonas[indiceDestino][indiceOrigen] += factorDeposito;
                    }
                }
            }
        }
    }

    /**
     * Perturba las feromonas para escapar de óptimos locales
     */
    private void perturbarFeromonas() {
        double perturbacion = 0.2; // 20% de perturbación máxima
        Random random = new Random();

        // Añadir perturbaciones aleatorias
        for (int i = 0; i < feromonas.length; i++) {
            for (int j = 0; j < feromonas[i].length; j++) {
                // Añadir ruido aleatorio entre -perturbación y +perturbación
                double factorRuido = 1 + (random.nextDouble() * 2 - 1) * perturbacion;
                feromonas[i][j] *= factorRuido;

                // Garantizar valor mínimo de feromona
                if (feromonas[i][j] < feromonaInicial / 2) {
                    feromonas[i][j] = feromonaInicial / 2;
                }
            }
        }

        // Reforzar aleatoriamente algunos caminos
        int numRefuerzos = feromonas.length / 10; // 10% de los nodos

        for (int k = 0; k < numRefuerzos; k++) {
            int i = random.nextInt(feromonas.length);
            int j = random.nextInt(feromonas.length);

            Coordenada origen = indiceACoordenada(i);
            Coordenada destino = indiceACoordenada(j);

            // Solo reforzar si son nodos adyacentes
            if (grafo.sonAdyacentes(origen, destino)) {
                feromonas[i][j] *= 3; // Triplicar feromona
                feromonas[j][i] *= 3;
            }
        }
    }

    /**
     * Ejecuta el algoritmo de colonia de hormigas
     * @return Mejor solución encontrada
     */
    public Solucion ejecutar() {
        int iteracion = 0;
        int iterSinMejora = 0;
        double densidadPedidosPrevio = calcularDensidadPedidos();  // RF99: Para ajuste dinámico

        System.out.println("Iniciando algoritmo ACO con " + numeroHormigas + " hormigas y " + numeroIteraciones + " iteraciones.");

        while (iteracion < numeroIteraciones) {
            // RF94: Detectar colapso del sistema
            if (indicadorColapso > umbralColapsoSistema) {
                System.out.println("ALERTA: Sistema en estado de colapso. Deteniendo simulación.");
                break;  // Terminar simulación
            }

            // RF99: Ajuste dinámico de frecuencia de planificación
            double densidadPedidosActual = calcularDensidadPedidos();
            if (Math.abs(densidadPedidosActual - densidadPedidosPrevio) > 0.2) {
                ajustarFrecuenciaPlanificacion(densidadPedidosActual);
                densidadPedidosPrevio = densidadPedidosActual;
            }

            // Revisar y actualizar eventos dinámicos
            boolean huboEventos = gestorEventos.revisarYActualizarEventos(momentoActual);
            if (huboEventos) {
                iterSinMejora = 0; // Resetear contador si cambió el entorno
            }

            // Actualizar heurística con información dinámica actual
            actualizarHeuristicaDinamica();

            // Construcción de soluciones por cada hormiga
            List<Solucion> soluciones = new ArrayList<>();

            for (int i = 0; i < numeroHormigas; i++) {
                // RF90/91: Exclusión por mantenimientos
                List<Camion> camionesDisponibles = filtrarCamionesPorMantenimiento();

                // RF95: Priorización por nivel de combustible
                camionesDisponibles = ordenarCamionesPorCombustible(camionesDisponibles);

                // Obtener pedidos pendientes
                List<Pedido> pedidosPendientes = obtenerPedidosPendientes();

                // Construir solución
                Solucion solucion = construirSolucion(camionesDisponibles, pedidosPendientes);

                // Evaluar calidad de la solución
                double calidad = evaluador.evaluarSolucion(solucion);
                solucion.setCalidad(calidad);
                soluciones.add(solucion);

                // Actualizar mejor global si corresponde
                if (calidad > mejorCalidadGlobal) {
                    mejorSolucionGlobal = solucion.copiar();
                    mejorCalidadGlobal = calidad;
                }
            }

            // Actualizar feromonas basado en soluciones encontradas
            actualizarFeromonas(soluciones);

            // Verificar mejora y control de convergencia
            if (mejorCalidadGlobal > mejorCalidadAnterior) {
                iterSinMejora = 0; // Hubo mejora, resetear contador
                indicadorColapso = 0; // Mejora indica que no hay colapso
                System.out.println("Iteración " + iteracion + ": Nueva mejor solución encontrada con calidad " + mejorCalidadGlobal);
            } else {
                iterSinMejora++; // No hubo mejora, incrementar
                indicadorColapso += 0.05; // Incrementar indicador de colapso
            }
            mejorCalidadAnterior = mejorCalidadGlobal;

            // Aplicar mecanismo anti-estancamiento si es necesario
            if (iterSinMejora >= maxIteracionesSinMejora) {
                if (iteracion < numeroIteraciones * umbralConvergenciaTemprana) {
                    // Convergencia temprana: perturbar para escapar de óptimo local
                    System.out.println("Iteración " + iteracion + ": Aplicando perturbación para escapar de óptimo local.");
                    perturbarFeromonas();
                    iterSinMejora = 0; // Resetear contador tras perturbación
                } else {
                    // Convergencia tardía: asumir que se encontró buena solución
                    System.out.println("Convergencia alcanzada en iteración " + iteracion);
                    break; // Terminar bucle principal
                }
            }

            // Incrementar contador de iteración
            iteracion++;
        }

        System.out.println("Algoritmo ACO finalizado después de " + iteracion + " iteraciones.");
        System.out.println("Mejor solución encontrada con calidad: " + mejorCalidadGlobal);

        // RF93: Generar datos para visualización
        generarDatosVisualizacion(mejorSolucionGlobal);

        return mejorSolucionGlobal;
    }

    /**
     * Construye una solución usando los componentes del algoritmo
     * @param camionesDisponibles Lista de camiones disponibles
     * @param pedidosPendientes Lista de pedidos pendientes
     * @return Solución construida
     */
    private Solucion construirSolucion(List<Camion> camionesDisponibles, List<Pedido> pedidosPendientes) {
        Solucion solucion = new Solucion();

        // RF85: Agrupamiento inteligente por proximidad
        // RF98: Optimización de secuencia de entregas
        Map<Camion, List<Pedido>> asignacionesOptimizadas =
                optimizador.asignarPedidosACamionesOptimizado(pedidosPendientes, camionesDisponibles);

        // Para cada asignación, construir la ruta
        for (Map.Entry<Camion, List<Pedido>> entry : asignacionesOptimizadas.entrySet()) {
            Camion camion = entry.getKey();
            List<Pedido> pedidosAsignados = entry.getValue();

            if (pedidosAsignados.isEmpty()) {
                continue;
            }

            // RF86: Priorización de tanques intermedios
            // RF88: Verificación de disponibilidad en tanques
            List<Coordenada> ruta = construirRutaConReabastecimiento(camion, pedidosAsignados);

            // Crear asignación y añadirla a la solución
            Asignacion asignacion = new Asignacion(camion, pedidosAsignados, ruta);
            solucion.agregarAsignacion(asignacion);

            // RF100: Actualizar inventario de tanques
            actualizarReservasTanques(camion, ruta);
        }

        return solucion;
    }

    /**
     * Construye una ruta para un camión y sus pedidos, incluyendo reabastecimiento si es necesario
     * @param camion Camión a asignar ruta
     * @param pedidos Pedidos asignados al camión
     * @return Lista de coordenadas que forman la ruta
     */
    private List<Coordenada> construirRutaConReabastecimiento(Camion camion, List<Pedido> pedidos) {
        List<Coordenada> rutaCompleta = new ArrayList<>();
        Coordenada ubicacionActual = camion.getPosicionActual();

        // Añadir posición inicial
        rutaCompleta.add(ubicacionActual);

        // Crear lista de destinos (ubicaciones de pedidos)
        List<Coordenada> destinos = new ArrayList<>();
        for (Pedido pedido : pedidos) {
            destinos.add(pedido.getUbicacion());
        }

        // Estimar consumo total
        double consumoEstimado = estimarConsumoTotal(camion, ubicacionActual, destinos);

        // RF86: Seleccionar tanque óptimo para reabastecimiento si es necesario
        Coordenada tanqueOptimo = gestorTanques.seleccionarTanqueOptimo(
                ubicacionActual, destinos, consumoEstimado, camion.getCombustibleActual());

        // Variables de control
        double combustibleActual = camion.getCombustibleActual();
        double cargaActual = calcularCargaTotal(pedidos);

        // Para cada pedido, construir la ruta
        for (int i = 0; i < pedidos.size(); i++) {
            Pedido pedido = pedidos.get(i);
            Coordenada destino = pedido.getUbicacion();

            // Calcular ruta directa
            List<Coordenada> subRuta = encontrarRutaViable(ubicacionActual, destino);

            // Estimar consumo para este tramo
            double consumoTramo = estimarConsumoTramo(camion, subRuta, cargaActual);

            // Verificar si necesitamos reabastecimiento antes de este tramo
            if (consumoTramo > combustibleActual * 0.8 && tanqueOptimo != null) {
                // Insertar parada en tanque antes del pedido
                List<Coordenada> rutaATanque = encontrarRutaViable(ubicacionActual, tanqueOptimo);

                // Añadir ruta al tanque (excluyendo el primer nodo que ya está en la ruta)
                if (rutaATanque.size() > 1) {
                    rutaCompleta.addAll(rutaATanque.subList(1, rutaATanque.size()));
                }

                // Actualizar ubicación actual y combustible
                ubicacionActual = tanqueOptimo;
                double recarga = gestorTanques.realizarRecarga(tanqueOptimo, camion);
                combustibleActual = camion.getCombustibleActual();

                // Recalcular ruta desde el tanque hasta el pedido
                subRuta = encontrarRutaViable(ubicacionActual, destino);
                consumoTramo = estimarConsumoTramo(camion, subRuta, cargaActual);
            }

            // Añadir ruta al pedido (excluyendo el primer nodo que ya está en la ruta)
            if (subRuta.size() > 1) {
                rutaCompleta.addAll(subRuta.subList(1, subRuta.size()));
            }

            // Actualizar estado
            ubicacionActual = destino;
            combustibleActual -= consumoTramo;
            cargaActual -= pedido.getCantidad();
        }

        // Añadir ruta de regreso al almacén más cercano
        Coordenada almacen = encontrarAlmacenMasCercano(ubicacionActual);

        // Verificar si necesitamos reabastecimiento antes de regresar
        double consumoRegreso = estimarConsumoTramo(camion,
                encontrarRutaViable(ubicacionActual, almacen), cargaActual);

        if (consumoRegreso > combustibleActual * 0.8 && tanqueOptimo != null) {
            // Insertar parada en tanque antes de regresar
            List<Coordenada> rutaATanque = encontrarRutaViable(ubicacionActual, tanqueOptimo);

            // Añadir ruta al tanque (excluyendo el primer nodo que ya está en la ruta)
            if (rutaATanque.size() > 1) {
                rutaCompleta.addAll(rutaATanque.subList(1, rutaATanque.size()));
            }

            // Actualizar estado
            ubicacionActual = tanqueOptimo;
            double recarga = gestorTanques.realizarRecarga(tanqueOptimo, camion);
            combustibleActual = camion.getCombustibleActual();
        }

        // Añadir ruta de regreso
        List<Coordenada> rutaRegreso = encontrarRutaViable(ubicacionActual, almacen);

        // Añadir ruta de regreso (excluyendo el primer nodo que ya está en la ruta)
        if (rutaRegreso.size() > 1) {
            rutaCompleta.addAll(rutaRegreso.subList(1, rutaRegreso.size()));
        }

        return rutaCompleta;
    }

    /**
     * Encuentra una ruta viable entre dos coordenadas usando A*
     * @param origen Coordenada origen
     * @param destino Coordenada destino
     * @return Lista de coordenadas que forman la ruta
     */
    private List<Coordenada> encontrarRutaViable(Coordenada origen, Coordenada destino) {
        // Implementar algoritmo A* considerando bloqueos

        // Conjunto de nodos abiertos (por explorar)
        PriorityQueue<NodoAStar> abiertos = new PriorityQueue<>(Comparator.comparing(NodoAStar::getF));

        // Conjunto de nodos cerrados (ya explorados)
        Set<Coordenada> cerrados = new HashSet<>();

        // Mapa de nodos a sus padres para reconstruir la ruta
        Map<Coordenada, Coordenada> padres = new HashMap<>();

        // Costos g (desde origen) y f (total estimado)
        Map<Coordenada, Double> g = new HashMap<>();
        Map<Coordenada, Double> f = new HashMap<>();

        // Inicializar con nodo origen
        g.put(origen, 0.0);
        f.put(origen, origen.distancia(destino));
        abiertos.add(new NodoAStar(origen, f.get(origen)));

        while (!abiertos.isEmpty()) {
            // Tomar nodo con menor f
            Coordenada actual = abiertos.poll().getNodo();

            // Si llegamos al destino, reconstruir y devolver la ruta
            if (actual.equals(destino)) {
                return reconstruirRuta(padres, actual);
            }

            // Marcar como explorado
            cerrados.add(actual);

            // Explorar vecinos
            for (Coordenada vecino : grafo.obtenerVecinos(actual)) {
                // Saltar si ya está explorado
                if (cerrados.contains(vecino)) {
                    continue;
                }

                // Verificar si hay bloqueo entre actual y vecino
                boolean hayBloqueo = false;
                for (Bloqueo bloqueo : bloqueos) {
                    if (bloqueo.afecta(actual, vecino, momentoActual)) {
                        hayBloqueo = true;
                        break;
                    }
                }

                if (hayBloqueo) {
                    continue;
                }

                // Calcular costo g tentativo (siempre es 1 entre nodos adyacentes)
                double costoTentativo = g.getOrDefault(actual, Double.MAX_VALUE) + 1;

                // Si no está en abiertos o si encontramos un mejor camino
                NodoAStar nodoVecino = new NodoAStar(vecino, 0);
                boolean esMejorCamino = false;

                if (!abiertos.contains(nodoVecino)) {
                    abiertos.add(nodoVecino);
                    esMejorCamino = true;
                } else if (costoTentativo < g.getOrDefault(vecino, Double.MAX_VALUE)) {
                    esMejorCamino = true;
                }

                if (esMejorCamino) {
                    // Actualizar información del vecino
                    padres.put(vecino, actual);
                    g.put(vecino, costoTentativo);
                    double heuristico = vecino.distancia(destino);
                    f.put(vecino, g.get(vecino) + heuristico);

                    // Actualizar f en la cola de prioridad
                    abiertos.remove(nodoVecino);
                    abiertos.add(new NodoAStar(vecino, f.get(vecino)));
                }
            }
        }

        // No se encontró ruta viable, devolver ruta directa
        List<Coordenada> rutaDirecta = new ArrayList<>();
        rutaDirecta.add(origen);
        rutaDirecta.add(destino);
        return rutaDirecta;
    }

    /**
     * Reconstruye la ruta a partir del mapa de padres
     * @param padres Mapa de padres
     * @param nodoActual Nodo actual
     * @return Lista de coordenadas que forman la ruta
     */
    private List<Coordenada> reconstruirRuta(Map<Coordenada, Coordenada> padres, Coordenada nodoActual) {
        List<Coordenada> ruta = new ArrayList<>();
        ruta.add(nodoActual);

        while (padres.containsKey(nodoActual)) {
            nodoActual = padres.get(nodoActual);
            ruta.add(0, nodoActual);
        }

        return ruta;
    }

    /**
     * Encuentra el almacén más cercano a una ubicación
     * @param ubicacion Ubicación
     * @return Coordenada del almacén más cercano
     */
    private Coordenada encontrarAlmacenMasCercano(Coordenada ubicacion) {
        Coordenada masCercano = null;
        double menorDistancia = Double.MAX_VALUE;

        for (Almacen almacen : almacenes.values()) {
            double distancia = ubicacion.distancia(almacen.getUbicacion());
            if (distancia < menorDistancia) {
                menorDistancia = distancia;
                masCercano = almacen.getUbicacion();
            }
        }

        // Si no hay almacenes, devolver la ubicación original
        return masCercano != null ? masCercano : ubicacion;
    }

    /**
     * Calcula la carga total de una lista de pedidos
     * @param pedidos Lista de pedidos
     * @return Carga total
     */
    private double calcularCargaTotal(List<Pedido> pedidos) {
        double total = 0;
        for (Pedido pedido : pedidos) {
            total += pedido.getCantidad();
        }
        return total;
    }

    /**
     * Estima el consumo total de una ruta
     * @param camion Camión que recorrerá la ruta
     * @param origen Ubicación de origen
     * @param destinos Lista de destinos
     * @return Consumo total estimado
     */
    private double estimarConsumoTotal(Camion camion, Coordenada origen, List<Coordenada> destinos) {
        if (destinos.isEmpty()) {
            return 0;
        }

        double consumoTotal = 0;
        Coordenada ubicacionActual = origen;
        double cargaActual = calcularCargaTotal(new ArrayList<>()); // Inicialmente vacío

        // Consumo de ir a cada destino
        for (Coordenada destino : destinos) {
            List<Coordenada> ruta = encontrarRutaViable(ubicacionActual, destino);
            double consumoTramo = estimarConsumoTramo(camion, ruta, cargaActual);
            consumoTotal += consumoTramo;
            ubicacionActual = destino;
        }

        // Consumo de regresar al almacén
        Coordenada almacen = encontrarAlmacenMasCercano(ubicacionActual);
        List<Coordenada> rutaRegreso = encontrarRutaViable(ubicacionActual, almacen);
        consumoTotal += estimarConsumoTramo(camion, rutaRegreso, 0); // Sin carga al regresar

        return consumoTotal;
    }

    /**
     * Estima el consumo de un tramo de ruta
     * @param camion Camión que recorrerá el tramo
     * @param ruta Lista de coordenadas del tramo
     * @param cargaActual Carga actual del camión
     * @return Consumo estimado para el tramo
     */
    private double estimarConsumoTramo(Camion camion, List<Coordenada> ruta, double cargaActual) {
        if (ruta.size() < 2) {
            return 0;
        }

        double distanciaTotal = 0;
        for (int i = 0; i < ruta.size() - 1; i++) {
            distanciaTotal += ruta.get(i).distancia(ruta.get(i + 1));
        }

        double peso = camion.getTipo().calcularPesoCombinado(cargaActual);
        return camion.calcularConsumo(distanciaTotal);
    }

    /**
     * Actualiza las reservas de tanques para una ruta planificada (RF100)
     * @param camion Camión que realizará la ruta
     * @param ruta Ruta planificada
     */
    private void actualizarReservasTanques(Camion camion, List<Coordenada> ruta) {
        // Obtener tanques en la ruta
        Map<Coordenada, Double> tanquesDisponibles = gestorTanques.getTanquesDisponibles();

        // Estimar tiempo de llegada a cada punto
        LocalDateTime tiempoActual = momentoActual;
        int tiempoPromedioMinutos = 5; // 5 minutos por unidad de distancia

        for (int i = 0; i < ruta.size(); i++) {
            Coordenada ubicacion = ruta.get(i);

            // Si esta ubicación es un tanque, reservar combustible
            if (tanquesDisponibles.containsKey(ubicacion)) {
                // Reservar combustible para este camión
                gestorTanques.reservarCombustible(ubicacion, camion, tiempoActual);
            }

            // Actualizar tiempo para el siguiente punto
            if (i < ruta.size() - 1) {
                double distancia = ubicacion.distancia(ruta.get(i + 1));
                tiempoActual = tiempoActual.plusMinutes((long)(distancia * tiempoPromedioMinutos));
            }
        }
    }

    /**
     * Filtra camiones según mantenimientos programados (RF90, RF91)
     * @return Lista de camiones disponibles
     */
    private List<Camion> filtrarCamionesPorMantenimiento() {
        List<Camion> camionesDisponibles = flota.obtenerDisponibles();
        List<Camion> resultado = new ArrayList<>();

        for (Camion camion : camionesDisponibles) {
            // Verificar mantenimiento preventivo (RF90)
            if (gestorEventos.tieneProgramadoMantenimiento(
                    camion.getCodigo(), momentoActual, momentoActual.plusHours(24))) {
                System.out.println("Camión " + camion.getCodigo() +
                        " excluido por mantenimiento preventivo programado.");
                continue;
            }

            // Verificar mantenimiento correctivo (RF91)
            if (camion.getEstado() == Camion.EstadoCamion.AVERIA_TIPO1 ||
                    camion.getEstado() == Camion.EstadoCamion.AVERIA_TIPO2 ||
                    camion.getEstado() == Camion.EstadoCamion.AVERIA_TIPO3 ||
                    camion.getEstado() == Camion.EstadoCamion.MANTENIMIENTO_PREVENTIVO) {
                System.out.println("Camión " + camion.getCodigo() +
                        " excluido por estado: " + camion.getEstado());
                continue;
            }

            // Camión disponible
            resultado.add(camion);
        }

        return resultado;
    }

    /**
     * Ordena camiones por nivel de combustible (menor a mayor) (RF95)
     * @param camiones Lista de camiones a ordenar
     * @return Lista ordenada
     */
    private List<Camion> ordenarCamionesPorCombustible(List<Camion> camiones) {
        // Crear lista de pares (camión, porcentaje de combustible)
        List<Map.Entry<Camion, Double>> pares = new ArrayList<>();

        for (Camion camion : camiones) {
            double porcentaje = camion.getCombustibleActual() / camion.getCapacidadTanque();
            pares.add(new AbstractMap.SimpleEntry<>(camion, porcentaje));
        }

        // Ordenar por porcentaje de combustible (menor a mayor)
        Collections.sort(pares, Comparator.comparing(Map.Entry::getValue));

        // Extraer solo los camiones ordenados
        List<Camion> resultado = new ArrayList<>();
        for (Map.Entry<Camion, Double> par : pares) {
            resultado.add(par.getKey());
        }

        return resultado;
    }

    /**
     * Calcula la densidad de pedidos actual (RF99)
     * @return Densidad normalizada (0-1)
     */
    private double calcularDensidadPedidos() {
        int totalPedidos = pedidos.size();
        if (totalPedidos == 0) {
            return 0;
        }

        int pedidosPendientes = 0;
        for (Pedido pedido : pedidos) {
            if (pedido.getEstado() == Pedido.EstadoPedido.PENDIENTE) {
                pedidosPendientes++;
            }
        }

        // Normalizar (0-1)
        return (double) pedidosPendientes / totalPedidos;
    }

    /**
     * Ajusta la frecuencia de planificación según densidad de pedidos (RF99)
     * @param densidadPedidos Densidad de pedidos actual
     */
    private void ajustarFrecuenciaPlanificacion(double densidadPedidos) {
        // Umbrales para ajuste
        final double UMBRAL_ALTA_DENSIDAD = 0.7;
        final double UMBRAL_BAJA_DENSIDAD = 0.3;

        // Ajustar parámetros del algoritmo
        if (densidadPedidos > UMBRAL_ALTA_DENSIDAD) {
            // Alta densidad: más exploración, más hormigas
            this.q0 = 0.7; // Más probabilidad de exploración
            this.numeroHormigas = 40; // Más hormigas para explorar más opciones
            this.frecuenciaPlanificacion = 30; // Replanificar cada 30 minutos

            System.out.println("Ajuste dinámico: Alta densidad de pedidos (" +
                    String.format("%.2f", densidadPedidos) +
                    "). Incrementando exploración y frecuencia de planificación.");
        } else if (densidadPedidos < UMBRAL_BAJA_DENSIDAD) {
            // Baja densidad: más explotación, menos hormigas
            this.q0 = 0.95; // Mayor explotación de soluciones conocidas
            this.numeroHormigas = 20; // Menos hormigas para resolver más rápido
            this.frecuenciaPlanificacion = 90; // Replanificar cada 90 minutos

            System.out.println("Ajuste dinámico: Baja densidad de pedidos (" +
                    String.format("%.2f", densidadPedidos) +
                    "). Incrementando explotación y reduciendo frecuencia de planificación.");
        } else {
            // Densidad media: valores equilibrados
            this.q0 = 0.9;
            this.numeroHormigas = 30;
            this.frecuenciaPlanificacion = 60; // Replanificar cada 60 minutos

            System.out.println("Ajuste dinámico: Densidad media de pedidos (" +
                    String.format("%.2f", densidadPedidos) +
                    "). Utilizando configuración equilibrada.");
        }
    }

    /**
     * Genera datos para visualización (RF93)
     * @param solucion Mejor solución encontrada
     */
    private void generarDatosVisualizacion(Solucion solucion) {
        System.out.println("\nREPORTE DE VISUALIZACIÓN:");
        System.out.println("===========================");

        // 1. Resumen general
        int totalPedidos = 0;
        double consumoTotal = 0;
        double distanciaTotal = 0;

        for (Asignacion asignacion : solucion.getAsignaciones()) {
            totalPedidos += asignacion.getPedidos().size();

            // Calcular distancia de la ruta
            List<Coordenada> ruta = asignacion.getRuta();
            double distanciaRuta = 0;
            for (int i = 0; i < ruta.size() - 1; i++) {
                distanciaRuta += ruta.get(i).distancia(ruta.get(i + 1));
            }

            distanciaTotal += distanciaRuta;

            // Estimar consumo
            double cargaInicial = calcularCargaTotal(asignacion.getPedidos());
            consumoTotal += asignacion.getCamion().calcularConsumo(distanciaRuta);
        }

        System.out.println("Resumen General:");
        System.out.println("- Calidad de la solución: " + String.format("%.4f", solucion.getCalidad()));
        System.out.println("- Total de camiones asignados: " + solucion.getAsignaciones().size());
        System.out.println("- Total de pedidos atendidos: " + totalPedidos);
        System.out.println("- Distancia total recorrida: " + String.format("%.2f", distanciaTotal) + " unidades");
        System.out.println("- Consumo total estimado: " + String.format("%.2f", consumoTotal) + " galones");

        // 2. Información de tanques
        System.out.println("\nEstado de Tanques:");
        Map<Coordenada, Double> tanques = gestorTanques.getTanquesDisponibles();
        List<Coordenada> tanquesOrdenados = gestorTanques.getTanquesPorTiempoAgotamiento();

        for (Coordenada ubicacion : tanquesOrdenados) {
            double disponible = tanques.get(ubicacion);
            System.out.println("- Tanque en " + ubicacion + ": " +
                    String.format("%.2f", disponible) + " galones disponibles");
        }

        // 3. Pedidos no atendidos
        List<Pedido> pedidosPendientes = obtenerPedidosPendientes();

        System.out.println("\nPedidos No Atendidos: " + pedidosPendientes.size());
        if (!pedidosPendientes.isEmpty()) {
            for (Pedido pedido : pedidosPendientes) {
                LocalDateTime limite = pedido.getFechaPedido().plus(pedido.getTiempoLimite());
                long horasRestantes = java.time.Duration.between(momentoActual, limite).toHours();

                System.out.println("- Cliente: " + pedido.getIdCliente() +
                        ", Ubicación: " + pedido.getUbicacion() +
                        ", Cantidad: " + pedido.getCantidad() +
                        ", Horas restantes: " + horasRestantes);
            }
        }

        // 4. Indicador de colapso
        System.out.println("\nIndicador de Colapso del Sistema: " +
                String.format("%.2f", indicadorColapso) +
                " (Umbral: " + umbralColapsoSistema + ")");

        System.out.println("===========================");
    }

    /**
     * Obtiene los pedidos pendientes
     */
    private List<Pedido> obtenerPedidosPendientes() {
        List<Pedido> pendientes = new ArrayList<>();
        for (Pedido pedido : pedidos) {
            if (pedido.getEstado() == Pedido.EstadoPedido.PENDIENTE) {
                pendientes.add(pedido);
            }
        }
        return pendientes;
    }

    // Clase auxiliar para el algoritmo A*
    private static class NodoAStar {
        private Coordenada nodo;
        private double f;

        public NodoAStar(Coordenada nodo, double f) {
            this.nodo = nodo;
            this.f = f;
        }

        public Coordenada getNodo() {
            return nodo;
        }

        public double getF() {
            return f;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof NodoAStar)) {
                return false;
            }
            NodoAStar otro = (NodoAStar) obj;
            return this.nodo.equals(otro.nodo);
        }

        @Override
        public int hashCode() {
            return nodo.hashCode();
        }
    }

    // Métodos para configurar parámetros del algoritmo
    public void setNumeroHormigas(int numeroHormigas) {
        this.numeroHormigas = numeroHormigas;
    }

    public void setNumeroIteraciones(int numeroIteraciones) {
        this.numeroIteraciones = numeroIteraciones;
    }

    public void setMaxIteracionesSinMejora(int maxIteracionesSinMejora) {
        this.maxIteracionesSinMejora = maxIteracionesSinMejora;
    }

    public void setUmbralConvergenciaTemprana(double umbralConvergenciaTemprana) {
        this.umbralConvergenciaTemprana = umbralConvergenciaTemprana;
    }

    public void setFactorEvaporacion(double factorEvaporacion) {
        this.factorEvaporacion = factorEvaporacion;
    }

    public void setAlfa(double alfa) {
        this.alfa = alfa;
    }

    public void setBeta(double beta) {
        this.beta = beta;
    }

    public void setQ0(double q0) {
        this.q0 = q0;
    }

    public void setFeromonaInicial(double feromonaInicial) {
        this.feromonaInicial = feromonaInicial;
    }

    public void setUmbralColapsoSistema(double umbralColapsoSistema) {
        this.umbralColapsoSistema = umbralColapsoSistema;
    }
}