package pucp.edu.glp.glpdp1.algorithm.aco;

import lombok.Getter;
import lombok.Setter;
import pucp.edu.glp.glpdp1.algorithm.model.CamionAsignacion;
import pucp.edu.glp.glpdp1.algorithm.model.GrafoRutas;
import pucp.edu.glp.glpdp1.algorithm.model.Nodo;
import pucp.edu.glp.glpdp1.algorithm.model.Ruta;
import pucp.edu.glp.glpdp1.algorithm.utils.AlgorithmUtils;
import pucp.edu.glp.glpdp1.algorithm.utils.DistanceCalculator;
import pucp.edu.glp.glpdp1.algorithm.utils.UrgencyCalculator;
import pucp.edu.glp.glpdp1.domain.Almacen;
import pucp.edu.glp.glpdp1.domain.Camion;
import pucp.edu.glp.glpdp1.domain.Pedido;
import pucp.edu.glp.glpdp1.domain.Ubicacion;
import pucp.edu.glp.glpdp1.domain.enums.TipoAlmacen;

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

    /**
     * Constructor
     * @param id Identificador único de la hormiga
     * @param parameters Parámetros del algoritmo
     */
    public Ant(int id, ACOParameters parameters) {
        this.id = id;
        this.parameters = parameters;
        this.random = new Random();
        this.random = new Random(System.nanoTime() + id * 1000);
    }

    /**
     * Método principal que construye una solución completa
     * Implementa RF85 (Agrupamiento inteligente) y RF98 (Optimización de secuencia)
     */
    public ACOSolution construirSolucion(
            List<Pedido> pedidos,
            List<Camion> camionesDisponibles,
            PheromoneMatrix feromonas,
            HeuristicCalculator heuristica,
            LocalDateTime tiempoActual,
            GrafoRutas grafo,
            Map<TipoAlmacen, Double> capacidadTanques) {

        // Crear solución vacía
        ACOSolution solucion = new ACOSolution();

        Collections.shuffle(pedidos, random);

        Collections.shuffle(camionesDisponibles, random);

        // IMPORTANTE: Hacer copia de los camiones disponibles para esta hormiga
        List<Camion> camionesDisponiblesHormiga = new ArrayList<>(camionesDisponibles);

        // Hacer copia de los pedidos para no modificar la lista original
        List<Pedido> pedidosPendientes = new ArrayList<>(pedidos);

        // Copia de capacidad de tanques para esta hormiga
        Map<TipoAlmacen,Double> capacidadTanquesHormiga = new HashMap<>(capacidadTanques);

        // RF85: Agrupar pedidos por proximidad
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
            // Si no quedan camiones disponibles, los pedidos quedan sin asignar
            if (capacidadTanquesHormiga.isEmpty()) {
                for (Pedido p : grupo) {
                    solucion.addPedidoNoAsignado(p);
                }
                continue;
            }

            // Calcular volumen total del grupo
            double volumenTotal = grupo.stream()
                    .mapToDouble(Pedido::getVolumen)
                    .sum();

            // Encontrar camión adecuado para este grupo
            Camion mejorCamion = null;
            double mejorRatio = Double.MAX_VALUE;

            for (Camion camion : camionesDisponiblesHormiga) {
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

            // Si no encontramos camión adecuado, intentamos dividir el grupo
            if (mejorCamion == null) {
                // Ordenar pedidos del grupo por volumen (menor a mayor)
                grupo.sort(Comparator.comparingDouble(Pedido::getVolumen));

                // Buscar el camión con mayor capacidad disponible
                Camion camionMayorCapacidad = camionesDisponiblesHormiga.stream()
                        .max(Comparator.comparingDouble(Camion::getCargaM3))
                        .orElse(null);

                if (camionMayorCapacidad != null) {
                    double capacidadRestante = camionMayorCapacidad.getCargaM3();
                    List<Pedido> pedidosAsignables = new ArrayList<>();

                    // Asignar tantos pedidos como sea posible
                    for (Pedido p : grupo) {
                        if (p.getVolumen() <= capacidadRestante) {
                            pedidosAsignables.add(p);
                            capacidadRestante -= p.getVolumen();
                        } else {
                            solucion.addPedidoNoAsignado(p);
                        }
                    }

                    // Si se pudieron asignar pedidos, usar este camión
                    if (!pedidosAsignables.isEmpty()) {
                        construirRutasOptimizadas(
                                solucion,
                                camionMayorCapacidad,
                                pedidosAsignables,
                                feromonas,
                                heuristica,
                                tiempoActual,
                                grafo,
                                capacidadTanquesHormiga
                        );

                        // Remover este camión de disponibles
                        camionesDisponiblesHormiga.remove(camionMayorCapacidad);
                    }
                } else {
                    // Si no hay camiones disponibles para dividir, todos los pedidos quedan sin asignar
                    for (Pedido p : grupo) {
                        solucion.addPedidoNoAsignado(p);
                    }
                }
            } else {
                // Construir rutas para el camión y grupo seleccionado
                construirRutasOptimizadas(
                        solucion,
                        mejorCamion,
                        grupo,
                        feromonas,
                        heuristica,
                        tiempoActual,
                        grafo,
                        capacidadTanquesHormiga
                );

                // Remover este camión de disponibles
                camionesDisponiblesHormiga.remove(mejorCamion);
            }
        }

        return solucion;
    }

    /**
     * RF85: Implementa agrupamiento inteligente de pedidos por proximidad
     */
    protected List<List<Pedido>> agruparPedidosPorProximidad(List<Pedido> pedidos) {
        List<List<Pedido>> grupos = new ArrayList<>();
        Set<Pedido> pedidosAgrupados = new HashSet<>();

        double umbralAjustado = parameters.getUmbralDistanciaPedidosCercanos()*(0.9+0.2*random.nextDouble());

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
                if(volumenAcumulado + pedidoCercano.getVolumen() <= capacidadMaxima){
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
     * RF98: Construye rutas optimizadas para minimizar viajes en vacío
     */
    private void construirRutasOptimizadas(
            ACOSolution solucion,
            Camion camion,
            List<Pedido> pedidos,
            PheromoneMatrix feromonas,
            HeuristicCalculator heuristica,
            LocalDateTime tiempoActual,
            GrafoRutas grafo,
            Map<TipoAlmacen, Double> capacidadTanquesHormiga) {

        // Crear asignación para este camión
        CamionAsignacion asignacion = new CamionAsignacion(camion, pedidos);

        // Ubicación inicial: almacén central
        Ubicacion ubicacionInicial = obtenerUbicacionAlmacenCentral(grafo);
        Nodo nodoActual = grafo.obtenerNodo(ubicacionInicial);

        // Lista de rutas a construir
        List<Ruta> rutas = new ArrayList<>();

        // Lista de pedidos por entregar
        List<Pedido> pedidosRestantes = new ArrayList<>(pedidos);

        // Variables para control de combustible
        double combustibleActual = camion.getGalones();
        double pesoCamion = camion.getPesoBrutoTon();
        double pesoCarga = AlgorithmUtils.calcularPesoCargaTotal(pedidos);
        double pesoTotal = pesoCamion + pesoCarga;

        // Calcular la máxima distancia posible con el combustible actual
        double distanciaMaximaPosible = (combustibleActual * 180) / pesoTotal;

        // Mientras queden pedidos por entregar
        while (!pedidosRestantes.isEmpty()) {
            // Seleccionar próximo pedido basado en feromonas y heurística
            Pedido siguiente = seleccionarSiguientePedido(
                    nodoActual,
                    pedidosRestantes,
                    feromonas,
                    heuristica,
                    grafo
            );

            // Ubicación del siguiente pedido
            Ubicacion ubicacionSiguiente = siguiente.getDestino();
            Nodo nodoSiguiente = grafo.obtenerNodo(ubicacionSiguiente);

            // Calcular distancia hasta el siguiente pedido
            double distanciaHastaSiguiente = DistanceCalculator.calcularDistanciaManhattan(
                    nodoActual.getUbicacion(), ubicacionSiguiente);

            // Verificar si hay suficiente combustible para ir y volver al almacén más cercano
            Ubicacion almacenMasCercanoASiguiente =
                    encontrarAlmacenMasCercano(ubicacionSiguiente, grafo);

            double distanciaAlmacenMasCercano = DistanceCalculator.calcularDistanciaManhattan(
                    ubicacionSiguiente, almacenMasCercanoASiguiente);

            // Si no alcanza el combustible, buscar reabastecimiento
            if ((distanciaHastaSiguiente + distanciaAlmacenMasCercano) > distanciaMaximaPosible) {
                // RF86: Encontrar tanque más conveniente para reabastecimiento
                Ubicacion tanqueMasConveniente = encontrarTanqueMasConveniente(
                        nodoActual.getUbicacion(),
                        ubicacionSiguiente,
                        grafo,
                        capacidadTanquesHormiga
                );

                // Construir ruta hasta tanque
                List<Nodo> caminoHastaTanque = grafo.encontrarRutaViable(
                        nodoActual,
                        grafo.obtenerNodo(tanqueMasConveniente),
                        tiempoActual
                );

                if (caminoHastaTanque.isEmpty()) {
                    // No se pudo encontrar ruta viable, el pedido no se puede entregar
                    pedidosRestantes.remove(siguiente);
                    solucion.addPedidoNoAsignado(siguiente);
                    continue;
                }

                // Crear ruta hasta tanque
                Ruta rutaReabastecimiento = new Ruta();
                rutaReabastecimiento.setOrigen(nodoActual.getUbicacion());
                rutaReabastecimiento.setDestino(tanqueMasConveniente);
                rutaReabastecimiento.setDistancia(calcularDistanciaRuta(caminoHastaTanque));
                rutaReabastecimiento.setPuntoReabastecimiento(true);
                rutas.add(rutaReabastecimiento);

                // Actualizar estado
                nodoActual = grafo.obtenerNodo(tanqueMasConveniente);

                // RF88/RF96: Actualizar combustible y capacidad del tanque
                TipoAlmacen tipoTanque = obtenerTipoAlmacen(tanqueMasConveniente, grafo);
                combustibleActual = 25; // Llenar tanque
                distanciaMaximaPosible = (combustibleActual * 180) / pesoTotal;

                // Descontar del tanque intermedio si no es central
                if (tipoTanque != TipoAlmacen.CENTRAL) {
                    double volumenActual = capacidadTanquesHormiga.get(tipoTanque);
                    double volumenConsumido = calcularVolumenReabastecimiento(camion);

                    if (volumenActual >= volumenConsumido) {
                        capacidadTanquesHormiga.put(tipoTanque, volumenActual - volumenConsumido);
                    } else {
                        // Si no hay suficiente capacidad, usar lo que queda
                        capacidadTanquesHormiga.put(tipoTanque, 0.0);
                    }
                }
            }

            // Construir ruta hasta el siguiente pedido
            List<Nodo> caminoHastaPedido = grafo.encontrarRutaViable(
                    nodoActual,
                    nodoSiguiente,
                    tiempoActual
            );

            if (caminoHastaPedido.isEmpty()) {
                // No se pudo encontrar ruta viable, el pedido no se puede entregar
                pedidosRestantes.remove(siguiente);
                solucion.addPedidoNoAsignado(siguiente);
                continue;
            }

            // Crear ruta hasta pedido
            Ruta rutaEntrega = new Ruta();
            rutaEntrega.setOrigen(nodoActual.getUbicacion());
            rutaEntrega.setDestino(ubicacionSiguiente);
            rutaEntrega.setDistancia(calcularDistanciaRuta(caminoHastaPedido));
            rutaEntrega.setPuntoEntrega(true);
            rutaEntrega.setPedidoEntrega(siguiente);
            rutas.add(rutaEntrega);

            // Actualizar estado
            nodoActual = nodoSiguiente;
            pesoCarga -= siguiente.getVolumen() * 0.5; // Peso estimado de la carga (0.5 ton por m3)
            pesoTotal = pesoCamion + pesoCarga;

            // Actualizar combustible consumido
            double distanciaRecorrida = rutaEntrega.getDistancia();
            double combustibleConsumido = (distanciaRecorrida * pesoTotal) / 180;
            combustibleActual -= combustibleConsumido;
            distanciaMaximaPosible = (combustibleActual * 180) / pesoTotal;

            // Eliminar pedido de pendientes
            pedidosRestantes.remove(siguiente);
        }

        // Añadir ruta de regreso al almacén más cercano
        Ubicacion almacenRegreso = encontrarAlmacenMasCercano(nodoActual.getUbicacion(), grafo);
        List<Nodo> caminoRegreso = grafo.encontrarRutaViable(
                nodoActual,
                grafo.obtenerNodo(almacenRegreso),
                tiempoActual
        );

        // Si no hay ruta viable de regreso, intentar con otro almacén
        if (caminoRegreso.isEmpty()) {
            List<Ubicacion> otrosAlmacenes = obtenerUbicacionesAlmacenes(grafo);
            otrosAlmacenes.remove(almacenRegreso);

            for (Ubicacion otroAlmacen : otrosAlmacenes) {
                caminoRegreso = grafo.encontrarRutaViable(
                        nodoActual,
                        grafo.obtenerNodo(otroAlmacen),
                        tiempoActual
                );

                if (!caminoRegreso.isEmpty()) {
                    almacenRegreso = otroAlmacen;
                    break;
                }
            }
        }

        // Si aún no hay ruta viable de regreso, crear una ruta vacía (caso extremo)
        if (caminoRegreso.isEmpty()) {
            almacenRegreso = obtenerUbicacionAlmacenCentral(grafo);
        }

        // Crear ruta de regreso
        Ruta rutaRegreso = new Ruta();
        rutaRegreso.setOrigen(nodoActual.getUbicacion());
        rutaRegreso.setDestino(almacenRegreso);
        rutaRegreso.setDistancia(caminoRegreso.isEmpty() ? 0 : calcularDistanciaRuta(caminoRegreso));
        rutaRegreso.setPuntoRegreso(true);
        rutas.add(rutaRegreso);

        // Asignar rutas a la asignación
        asignacion.setRutas(rutas);

        // Calcular consumo total
        double consumoTotal = 0;
        for (Ruta ruta : rutas) {
            // Para cada tramo, calcular el consumo según el peso en ese momento
            double pesoTramo = pesoCamion + (pedidos.size() - rutas.indexOf(ruta)) * 0.5;
            double consumoTramo = (ruta.getDistancia() * pesoTramo) / 180.0;
            consumoTotal += consumoTramo;
        }
        asignacion.setConsumoTotal(consumoTotal);

        // Añadir la asignación a la solución
        solucion.addAsignacion(asignacion);
    }

    /**
     * Selecciona el siguiente pedido basado en feromonas y heurística
     * Implementa la regla de transición de estado del algoritmo ACO
     */
    protected Pedido seleccionarSiguientePedido(
            Nodo nodoActual,
            List<Pedido> pedidosRestantes,
            PheromoneMatrix feromonas,
            HeuristicCalculator heuristica,
            GrafoRutas grafo) {

        // ADAPTACIÓN: Si hay solución guía, consultarla primero (25% de probabilidad)
        if (solucionGuia != null && random.nextDouble() < 0.25) {
            Pedido pedidoGuiado = consultarSolucionGuia(nodoActual, pedidosRestantes);
            if (pedidoGuiado != null) {
                return pedidoGuiado;
            }
        }

        double q0Efectivo = Math.max(0.1, parameters.getQ0()*(0.8+0.4*random.nextDouble()));

        if(random.nextDouble()<0.05){
            return pedidosRestantes.get(random.nextInt(pedidosRestantes.size()));
        }

        // Implementación de la regla de pseudoaleatorio proporcional
        if (random.nextDouble() < q0Efectivo) {
            // Explotación: elegir el mejor según feromonas y heurística
            double mejorValor = Double.NEGATIVE_INFINITY;
            Pedido mejorPedido = null;

            for (Pedido pedido : pedidosRestantes) {
                Nodo nodoPedido = grafo.obtenerNodo(pedido.getDestino());
                int idNodoActual = nodoActual.getId();
                int idNodoPedido = nodoPedido.getId();

                double valorFeromona = feromonas.getValor(idNodoActual, idNodoPedido);
                double valorHeuristica = heuristica.getValorHeuristica(idNodoActual, idNodoPedido);

                double ruido = 1.0 + (random.nextDouble()-0.5);

                // Ajustar por urgencia
                double urgencia = UrgencyCalculator.calcularUrgenciaNormalizada(pedido);
                double factorUrgencia = 1.0 + urgencia * parameters.getFactorPriorizacionUrgencia();

                double valor = Math.pow(valorFeromona, parameters.getAlfa()) *
                        Math.pow(valorHeuristica, parameters.getBeta()) *
                        factorUrgencia;

                if (valor > mejorValor) {
                    mejorValor = valor;
                    mejorPedido = pedido;
                }
            }

            return mejorPedido != null ? mejorPedido : pedidosRestantes.get(0);
        } else {
            // Exploración: selección probabilística
            double total = 0;
            Map<Pedido, Double> probabilidades = new HashMap<>();

            for (Pedido pedido : pedidosRestantes) {
                Nodo nodoPedido = grafo.obtenerNodo(pedido.getDestino());
                int idNodoActual = nodoActual.getId();
                int idNodoPedido = nodoPedido.getId();

                double valorFeromona = feromonas.getValor(idNodoActual, idNodoPedido);
                double valorHeuristica = heuristica.getValorHeuristica(idNodoActual, idNodoPedido);

                double pertubacion = 0.9 + 0.2 * random.nextDouble();

                // Ajustar por urgencia
                double urgencia = UrgencyCalculator.calcularUrgenciaNormalizada(pedido);
                double factorUrgencia = 1.0 + urgencia * parameters.getFactorPriorizacionUrgencia();

                double valor = Math.pow(valorFeromona, parameters.getAlfa()) *
                        Math.pow(valorHeuristica, parameters.getBeta()) *
                        factorUrgencia;

                probabilidades.put(pedido, valor);
                total += valor;
            }

            // Selección por ruleta
            double seleccion = random.nextDouble() * total;
            double acumulado = 0;

            for (Map.Entry<Pedido, Double> entrada : probabilidades.entrySet()) {
                acumulado += entrada.getValue();
                if (acumulado >= seleccion) {
                    return entrada.getKey();
                }
            }

            // Si por algún error numérico no se seleccionó ninguno, devolver el primero
            return pedidosRestantes.get(random.nextInt(pedidosRestantes.size()));
        }
    }

    /**
     * Consulta la solución guía para encontrar un siguiente pedido recomendado
     */
    private Pedido consultarSolucionGuia(Nodo nodoActual, List<Pedido> pedidosRestantes) {
        // Si no hay solución guía, retornar null
        if (solucionGuia == null || solucionGuia.getAsignaciones().isEmpty()) {
            return null;
        }

        // Obtener ubicación actual
        Ubicacion ubicacionActual = nodoActual.getUbicacion();

        // Buscar en cada asignación de la solución guía
        for (CamionAsignacion asignacion : solucionGuia.getAsignaciones()) {
            List<Ruta> rutas = asignacion.getRutas();

            // Buscar una ruta que parta de la ubicación actual o cercana
            for (int i = 0; i < rutas.size(); i++) {
                Ruta ruta = rutas.get(i);

                // Si el origen es cercano a la ubicación actual
                if (esUbicacionCercana(ruta.getOrigen(), ubicacionActual, 3)) {
                    // Obtener el pedido de destino (si es punto de entrega)
                    if (ruta.isPuntoEntrega() && ruta.getPedidoEntrega() != null) {
                        Pedido pedidoSugerido = ruta.getPedidoEntrega();

                        // Verificar si este pedido está disponible
                        if (pedidosRestantes.contains(pedidoSugerido)) {
                            return pedidoSugerido;
                        }

                        // Si no está disponible, buscar uno similar
                        Pedido pedidoSimilar = buscarPedidoSimilar(pedidoSugerido, pedidosRestantes);
                        if (pedidoSimilar != null) {
                            return pedidoSimilar;
                        }
                    }
                }
            }
        }

        return null; // No se encontró pedido guía apropiado
    }

    /**
     * Verifica si dos ubicaciones están cerca (Manhattan)
     */
    private boolean esUbicacionCercana(Ubicacion u1, Ubicacion u2, int umbralDistancia) {
        int distancia = Math.abs(u1.getX() - u2.getX()) + Math.abs(u1.getY() - u2.getY());
        return distancia <= umbralDistancia;
    }

    /**
     * Busca un pedido similar al dado en una lista de pedidos
     */
    private Pedido buscarPedidoSimilar(Pedido pedidoReferencia, List<Pedido> pedidosDisponibles) {
        Ubicacion ubicacionReferencia = pedidoReferencia.getDestino();
        Pedido pedidoMasCercano = null;
        double distanciaMinima = Double.MAX_VALUE;

        for (Pedido p : pedidosDisponibles) {
            double distancia = Math.abs(p.getDestino().getX() - ubicacionReferencia.getX()) +
                    Math.abs(p.getDestino().getY() - ubicacionReferencia.getY());

            if (distancia < distanciaMinima) {
                distanciaMinima = distancia;
                pedidoMasCercano = p;
            }
        }

        // Solo retornar si está suficientemente cerca
        return distanciaMinima <= 10 ? pedidoMasCercano : null;
    }

    /**
     * RF86: Encuentra el tanque más conveniente para reabastecimiento
     */
    private Ubicacion encontrarTanqueMasConveniente(
            Ubicacion ubicacionActual,
            Ubicacion ubicacionDestino,
            GrafoRutas grafo,
            Map<TipoAlmacen, Double> capacidadTanquesHormiga) {

        // Obtener ubicaciones de todos los almacenes
        List<Ubicacion> ubicacionesAlmacenes = obtenerUbicacionesAlmacenes(grafo);

        // Si no hay tanques intermedios con capacidad, usar el almacén central
        Ubicacion almacenCentral = null;

        Map<Ubicacion, Double> puntuaciones = new HashMap<>();

        for (Ubicacion ubicacionAlmacen : ubicacionesAlmacenes) {
            TipoAlmacen tipoAlmacen = obtenerTipoAlmacen(ubicacionAlmacen, grafo);

            if (tipoAlmacen == TipoAlmacen.CENTRAL) {
                almacenCentral = ubicacionAlmacen;
                continue; // Evaluar el almacén central al final si es necesario
            }

            // RF88: Verificar si el tanque tiene capacidad suficiente
            double capacidadDisponible = capacidadTanquesHormiga.get(tipoAlmacen);
            if (capacidadDisponible < parameters.getCapacidadMinimaReabastecimiento()) {
                continue; // Tanque sin capacidad suficiente
            }

            // Calcular desviación (distancia extra que implica ir al tanque)
            double distanciaDirecta = DistanceCalculator.calcularDistanciaManhattan(
                    ubicacionActual, ubicacionDestino);

            double distanciaConTanque = DistanceCalculator.calcularDistanciaManhattan(
                    ubicacionActual, ubicacionAlmacen) +
                    DistanceCalculator.calcularDistanciaManhattan(
                            ubicacionAlmacen, ubicacionDestino);

            double desviacion = distanciaConTanque - distanciaDirecta;

            // RF86: Factor de priorización de tanques intermedios
            // Menor puntuación = mejor opción
            double factorCapacidad = capacidadDisponible / 160.0; // Normalizado entre 0-1
            double puntuacion = desviacion / (factorCapacidad * parameters.getFactorPriorizacionTanques());

            puntuaciones.put(ubicacionAlmacen, puntuacion);
        }

        // Encontrar el tanque con mejor puntuación
        Ubicacion mejorTanque = null;
        double mejorPuntuacion = Double.MAX_VALUE;

        for (Map.Entry<Ubicacion, Double> entrada : puntuaciones.entrySet()) {
            if (entrada.getValue() < mejorPuntuacion) {
                mejorPuntuacion = entrada.getValue();
                mejorTanque = entrada.getKey();
            }
        }

        // Si no hay tanques intermedios disponibles, usar almacén central
        return mejorTanque != null ? mejorTanque : almacenCentral;
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
     * Calcula el volumen necesario para reabastecimiento
     */
    private double calcularVolumenReabastecimiento(Camion camion) {
        // Para simplificar, asumimos reabastecimiento completo de la capacidad
        return camion.getCargaM3();
    }

    /**
     * Verifica si un camión puede cargar un pedido
     */
    private boolean puedeCargarPedido(Camion camion,Pedido pedido){
        boolean tieneEspacio = camion.getCargaM3()>=pedido.getVolumen();

        boolean soportaPeso = camion.puedeCargar(pedido.getVolumen());

        return tieneEspacio && soportaPeso;
    }


    /**
     * Divide un pedido grande entre varios camiones
     */
    private List<Pedido> dividirPedidoGrande(Pedido pedidoOriginal,List<Camion> camionesDisponiblesHormiga){
        List<Pedido> subpedidos = new ArrayList<>();
        double volumenRestante = pedidoOriginal.getVolumen();

        camionesDisponiblesHormiga.sort((c1,c2)->Double.compare(c2.getCargaM3(),c1.getCargaM3()));

        int subpedidoId = 1;
        for(Camion camion: camionesDisponiblesHormiga){
            if(volumenRestante <= 0) break;
            double capacidadCamion = camion.getCargaM3();
            double volumenAsignable = Math.min(capacidadCamion, volumenRestante);

            if(volumenAsignable > 0){
                Pedido subpedido = new Pedido();
                subpedido.setIdPedido(pedidoOriginal.getIdPedido()*1000+subpedidoId++);
                subpedido.setVolumen(volumenAsignable);
                subpedido.setDestino(pedidoOriginal.getDestino());
                subpedido.setFechaLimite(pedidoOriginal.getFechaLimite());

                subpedidos.add(subpedido);
                volumenRestante-= volumenAsignable;

            }
        }
        return subpedidos;
    }

    /**
     * Establece una solución guía para influir en la construcción
     */
    public void setSolucionGuia(ACOSolution solucionGuia) {
        this.solucionGuia = solucionGuia;
    }
}