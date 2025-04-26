package pucp.edu.glp.glpdp1.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pucp.edu.glp.glpdp1.algorithm.aco.ACOParameters;
import pucp.edu.glp.glpdp1.domain.*;
import pucp.edu.glp.glpdp1.service.AlgoritmoService;
import pucp.edu.glp.glpdp1.service.MapaService;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/mapa")
public class MapaController {

    private final MapaService mapaService;
    private Mapa mapa; // Esto sería gestionado por un servicio/repositorio en una app real

    @Autowired
    public MapaController(MapaService mapaService) {
        this.mapaService = mapaService;
        this.mapa = new Mapa(70, 50); // Inicialización del mapa con dimensiones
    }

    @PostMapping("/cargar-pedidos")
    public ResponseEntity<String> cargarPedidos(@RequestParam("archivo") MultipartFile archivo,
                                                @RequestParam(required = false) String fechaInicio,
                                                @RequestParam(required = false) String fechaFin) {
        try {
            // Cargar pedidos desde el archivo subido
            mapaService.cargarPedidosEnMapaDesdeBytes(mapa, archivo.getBytes());
            if (!mapa.getPedidos().isEmpty()) {
                mapa.getPedidos().stream().limit(3).forEach(p ->
                        System.out.println("Pedido ID: " + p.getIdPedido() + " | Fecha Registro: " + p.getFechaRegistro() + " | Fecha Límite: " + p.getFechaLimite()));
            }

            if (fechaInicio != null && !fechaInicio.isEmpty()) {
                LocalDateTime inicio = LocalDateTime.parse(fechaInicio);
                mapa.setFechaInicio(inicio);
            }

            if (fechaFin != null && !fechaFin.isEmpty()) {
                LocalDateTime fin = LocalDateTime.parse(fechaFin);
                mapa.setFechaFin(fin);
            }

            if (mapa.getFechaInicio() != null && mapa.getFechaFin() != null) {
                List<Pedido> pedidosFiltrados = filtrarPedidosPorRangoFecha(
                        mapa.getPedidos(), mapa.getFechaInicio(), mapa.getFechaFin()
                );
                mapa.setPedidos(pedidosFiltrados);
            }
            return ResponseEntity.ok("Pedidos cargados correctamente. Total: " + mapa.getPedidos().size());
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Error al cargar pedidos: " + e.getMessage());
        }
    }

    @GetMapping("/pedidos")
    public ResponseEntity<?> obtenerPedidos() {
        return ResponseEntity.ok(mapa.getPedidos());
    }

    @PostMapping("/cargar-averias")
    public ResponseEntity<String> cargarAverias(@RequestParam("archivo") MultipartFile archivo) {
        try {
            // Cargar averias desde el archivo subido
            mapaService.cargarAveriasEnMapaDesdeBytes(mapa, archivo.getBytes());
            return ResponseEntity.ok("Averias cargadas correctamente. Total: " + mapa.getAverias().size());
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Error al cargar averias: " + e.getMessage());
        }
    }

    @GetMapping("/averias")
    public ResponseEntity<?> obtenerAverias() {
        return ResponseEntity.ok(mapa.getAverias());
    }

    @PostMapping("/cargar-bloqueos")
    public ResponseEntity<String> cargarBloqueos(@RequestParam("archivo") MultipartFile archivo) {
        try {
            mapaService.cargarBloqueosEnMapaDesdeBytes(mapa, archivo.getBytes());
            return ResponseEntity.ok("Bloqueos cargados correctamente. Total: " + mapa.getBloqueos().size());
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Error al cargar bloqueos: " + e.getMessage());
        }
    }

    @GetMapping("/bloqueos")
    public ResponseEntity<?> obtenerBloqueos() {
        return ResponseEntity.ok(mapa.getBloqueos());
    }

    @GetMapping("/ver-mapa")
    public ResponseEntity<?> verMapa() {
        return ResponseEntity.ok(mapa);
    }

    @Autowired
    private AlgoritmoService acoAlgorithmService; // Servicio para el algoritmo ACO

    @PostMapping("/planificar-rutas")
    public ResponseEntity<?> planificarRutas(
            @RequestParam(required = false) Map<String, String> requestParams,
            @RequestParam(required = false, defaultValue = "dia") String escenario,
            @RequestParam(required = false) String fechaInicio,
            @RequestParam(required = false) String fechaFin) {

        try {
            if (requestParams != null) {
                if (requestParams.containsKey("escenario")) {
                    escenario = requestParams.get("escenario");
                }
                if (requestParams.containsKey("fechaInicio")) {
                    fechaInicio = requestParams.get("fechaInicio");
                }
                if (requestParams.containsKey("fechaFin")) {
                    fechaFin = requestParams.get("fechaFin");
                }
            }
            // Configurar fechas para simulación si se proporcionan
            if (fechaInicio != null && !fechaInicio.isEmpty()) {
                LocalDateTime inicio = LocalDateTime.parse(fechaInicio);
                System.out.println("Fecha de inicio: " + inicio);
                mapa.setFechaInicio(inicio);
            }

            if (fechaFin != null && !fechaFin.isEmpty()) {
                LocalDateTime fin = LocalDateTime.parse(fechaFin);
                System.out.println("Fecha de fin: " + fin);
                mapa.setFechaFin(fin);
            }

            List<Pedido> pedidosFiltrados = filtrarPedidosPorRangoFecha(
                    mapa.getPedidos(),
                    mapa.getFechaInicio(),
                    mapa.getFechaFin()
            );

            Mapa mapaFiltrado = new Mapa(mapa.getAncho(), mapa.getAlto());
            mapaFiltrado.setPedidos(pedidosFiltrados);
            mapaFiltrado.setBloqueos(mapa.getBloqueos());
            mapaFiltrado.setAlmacenes(mapa.getAlmacenes());
            mapaFiltrado.setAverias(mapa.getAverias());
            mapaFiltrado.setFlota(mapa.getFlota());
            mapaFiltrado.setFechaInicio(mapa.getFechaInicio());
            mapaFiltrado.setFechaFin(mapa.getFechaFin());

            // Configurar parámetros según el escenario seleccionado
            ACOParameters params;
            switch (escenario.toLowerCase()) {
                case "dia":
                case "diario":
                    // 1. Escenario de operaciones día a día
                    params = ACOParameters.getConfiguracionEquilibrada();
                    params.setNumeroIteraciones(50);  // Menos iteraciones para respuesta rápida
                    break;

                case "semana":
                case "semanal":
                    // 2. Escenario de simulación semanal (7 días)
                    params = ACOParameters.getConfiguracionRapida();
                    params.setNumeroIteraciones(25);  // Más iteraciones para mejor calidad
                    // Configurar para que termine en tiempo adecuado (20-50 minutos)
                    params.setTiempoAvanceSimulacion(15);  // 30 minutos por iteración

                    // Si no se especifica un período, configurar 7 días por defecto
                    if (mapa.getFechaInicio() == null) {
                        mapa.setFechaInicio(LocalDateTime.now());
                    }
                    if (mapa.getFechaFin() == null) {
                        mapa.setFechaFin(mapa.getFechaInicio().plusDays(7));
                    }
                    break;

                case "colapso":
                    // 3. Escenario de simulación hasta colapso
                    params = ACOParameters.getConfiguracionCalidad();
                    params.activarDeteccionColapsoSensible();
                    params.setNumeroIteraciones(5000);  // Muchas iteraciones para llegar al colapso
                    params.setUmbralColapso(0.15);      // Umbral de colapso más sensible (15%)

                    // Si no se especifica un período, configurar un período largo
                    if (mapa.getFechaInicio() == null) {
                        mapa.setFechaInicio(LocalDateTime.now());
                    }
                    if (mapa.getFechaFin() == null) {
                        mapa.setFechaFin(mapa.getFechaInicio().plusMonths(1)); // Simulación de hasta un mes
                    }
                    break;

                default:
                    return ResponseEntity.badRequest().body("Escenario no reconocido. Use 'dia', 'semana' o 'colapso'");
            }

            // Ejecutar algoritmo ACO
            List<Rutas> rutasOptimizadas = acoAlgorithmService.generarRutasOptimizadas(mapaFiltrado, params);

            // Guardar las rutas en el mapa
            mapa.setRutas(rutasOptimizadas);

            return ResponseEntity.ok(rutasOptimizadas);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Error al planificar rutas: " + e.getMessage());
        }
    }

    @GetMapping("/visualizar-rutas")
    public ResponseEntity<?> visualizarRutas(
            @RequestParam(required = false, defaultValue = "false") boolean detalleCompleto,
            @RequestParam(required = false, defaultValue = "-1") int idRuta) {

        // Obtener las rutas actuales del mapa
        List<Rutas> rutas = mapa.getRutas();
        if (rutas == null || rutas.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("No hay rutas generadas actualmente.");
        }

        Map<String, Object> datosVisualizacion = new HashMap<>();

        if (idRuta != -1) {
            // Solo expandir la ruta específica solicitada
            rutas.stream()
                    .filter(r -> r.getId() == idRuta)
                    .findFirst()
                    .ifPresent(ruta -> {
                        Rutas rutaExpandida = new Rutas();
                        // Copia propiedades básicas
                        rutaExpandida.setId(ruta.getId());
                        rutaExpandida.setCamion(ruta.getCamion());
                        rutaExpandida.setDistanciaTotal(ruta.getDistanciaTotal());
                        rutaExpandida.setTiempoTotal(ruta.getTiempoTotal());
                        rutaExpandida.setConsumoTotal(ruta.getConsumoTotal());

                        // Expandir ruta solo para esta ruta específica
                        rutaExpandida.setUbicaciones(expandirRuta(ruta.getUbicaciones()));
                        datosVisualizacion.put("rutaExpandida", rutaExpandida);
                    });
        }

        // Información básica siempre
        datosVisualizacion.put("rutas", rutas);
        datosVisualizacion.put("almacenes", mapa.getAlmacenes());
        datosVisualizacion.put("bloqueos", mapa.getBloqueos());
        datosVisualizacion.put("pedidos", mapa.getPedidos());

        return ResponseEntity.ok(datosVisualizacion);
    }

    private List<Ubicacion> calcularPuntosIntermedios(Ubicacion origen, Ubicacion destino) {
        List<Ubicacion> puntosIntermedios = new ArrayList<>();

        int x1 = origen.getX();
        int y1 = origen.getY();
        int x2 = destino.getX();
        int y2 = destino.getY();

        // Factor de muestreo para reducir puntos (ajustar según necesidad)
        int factorMuestreo = Math.max(1, (Math.abs(x2 - x1) + Math.abs(y2 - y1)) / 50);

        // Primero nos movemos horizontalmente
        int paso = x1 < x2 ? factorMuestreo : -factorMuestreo;
        for (int x = x1 + paso; (paso > 0 && x < x2) || (paso < 0 && x > x2); x += paso) {
            puntosIntermedios.add(new Ubicacion(x, y1));
        }

        // Después nos movemos verticalmente
        paso = y1 < y2 ? factorMuestreo : -factorMuestreo;
        for (int y = y1 + paso; (paso > 0 && y < y2) || (paso < 0 && y > y2); y += paso) {
            puntosIntermedios.add(new Ubicacion(x2, y));
        }

        return puntosIntermedios;
    }

    // Método que expande una ruta
    private List<Ubicacion> expandirRuta(List<Ubicacion> rutaOriginal) {
        List<Ubicacion> rutaExpandida = new ArrayList<>();
        if (rutaOriginal.isEmpty()) return rutaExpandida;

        rutaExpandida.add(rutaOriginal.get(0));

        for (int i = 0; i < rutaOriginal.size() - 1; i++) {
            Ubicacion origen = rutaOriginal.get(i);
            Ubicacion destino = rutaOriginal.get(i + 1);
            rutaExpandida.addAll(calcularPuntosIntermedios(origen, destino));
        }

        return rutaExpandida;
    }

    @GetMapping("/diagnostico")
    public ResponseEntity<?> diagnosticarDatos() {
        Map<String, Object> diagnostico = new HashMap<>();

        // Datos generales
        diagnostico.put("totalPedidos", mapa.getPedidos().size());
        diagnostico.put("totalBloqueos", mapa.getBloqueos().size());
        diagnostico.put("totalCamiones", mapa.getFlota().size());
        diagnostico.put("fechaInicio", mapa.getFechaInicio());
        diagnostico.put("fechaFin", mapa.getFechaFin());

        // Filtrar pedidos por fecha
        if (mapa.getFechaInicio() != null && mapa.getFechaFin() != null) {
            List<Pedido> pedidosFiltrados = filtrarPedidosPorRangoFecha(
                    mapa.getPedidos(), mapa.getFechaInicio(), mapa.getFechaFin());
            diagnostico.put("pedidosEnRango", pedidosFiltrados.size());
        }

        // Verificar capacidad de flota vs demanda
        double capacidadTotalFlota = mapa.getFlota().stream()
                .mapToDouble(c -> c.getPesoCombinadoTon()).sum();
        double volumenTotalPedidos = mapa.getPedidos().stream()
                .mapToDouble(p -> p.getVolumen()).sum();
        diagnostico.put("capacidadTotalFlota", capacidadTotalFlota);
        diagnostico.put("volumenTotalPedidos", volumenTotalPedidos);

        return ResponseEntity.ok(diagnostico);
    }

    /**
     * Método para filtrar pedidos dentro del rango de fechas de simulación
     */
    private List<Pedido> filtrarPedidosPorRangoFecha(List<Pedido> pedidos, LocalDateTime fechaInicio, LocalDateTime fechaFin) {
        if (fechaInicio == null || fechaFin == null) {
            return pedidos; // Si no hay rango definido, devuelve todos
        }
        int pedidosDespuesDeInicio = 0;
        int pedidosAntesDeFin = 0;
        int pedidosDentroDelRango = 0;

        for (Pedido pedido : pedidos) {
            LocalDateTime fechaPedido = pedido.getFechaLimite();
            boolean despuesDeInicio = !fechaPedido.isBefore(fechaInicio);
            boolean antesDeFin = !fechaPedido.isAfter(fechaFin);

            if (despuesDeInicio) pedidosDespuesDeInicio++;
            if (antesDeFin) pedidosAntesDeFin++;
            if (despuesDeInicio && antesDeFin) pedidosDentroDelRango++;
        }
        return pedidos.stream()
                .filter(pedido -> {
                    LocalDateTime fechaPedido = pedido.getFechaLimite();
                    return !fechaPedido.isBefore(fechaInicio) &&
                            !fechaPedido.isAfter(fechaFin);
                })
                .collect(Collectors.toList());
    }
}
