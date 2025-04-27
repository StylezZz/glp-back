package pucp.edu.glp.glpdp1.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pucp.edu.glp.glpdp1.algorithm.aco.ACOParameters;
import pucp.edu.glp.glpdp1.domain.*;
import pucp.edu.glp.glpdp1.service.AlgoritmoService;
import pucp.edu.glp.glpdp1.service.MapaService;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
            @RequestParam(defaultValue = "dia") String escenario,
            @RequestParam(required = false) String fechaInicio,
            @RequestParam(required = false) String fechaFin) {

        try {
            // ——— 1) Parseo de fechas ———
            if (StringUtils.hasText(fechaInicio)) {
                mapa.setFechaInicio(LocalDateTime.parse(fechaInicio));
            }
            if (StringUtils.hasText(fechaFin)) {
                mapa.setFechaFin(LocalDateTime.parse(fechaFin));
            }

            // ——— 2) Filtrar pedidos por rango ———
            List<Pedido> pedidosFilt = filtrarPedidosPorRangoFecha(
                    mapa.getPedidos(), mapa.getFechaInicio(), mapa.getFechaFin()
            );

            // ——— 3) Preparar mapa para ACO ———
            Mapa m = new Mapa(mapa.getAncho(), mapa.getAlto());
            m.setPedidos(pedidosFilt);
            m.setBloqueos(mapa.getBloqueos());
            m.setAlmacenes(mapa.getAlmacenes());
            m.setAverias(mapa.getAverias());
            m.setFlota(mapa.getFlota());
            m.setFechaInicio(mapa.getFechaInicio());
            m.setFechaFin(mapa.getFechaFin());

            // ——— 4) Configurar parámetros ACO según escenario ———
            ACOParameters params = switch (escenario.toLowerCase()) {
                case "semana", "semanal" -> {
                    var p = ACOParameters.getConfiguracionRapida();
                    p.setNumeroIteraciones(5);
                    p.setTiempoAvanceSimulacion(15);
                    asegurarPeriodo(mapa, 7, ChronoUnit.DAYS);
                    yield p;
                }
                case "colapso" -> {
                    var p = ACOParameters.getConfiguracionCalidad();
                    p.activarDeteccionColapsoSensible();
                    p.setNumeroIteraciones(5000);
                    p.setUmbralColapso(0.15);
                    asegurarPeriodo(mapa, 1, ChronoUnit.MONTHS);
                    yield p;
                }
                default -> {
                    var p = ACOParameters.getConfiguracionEquilibrada();
                    p.setNumeroIteraciones(50);
                    yield p;
                }
            };

            // ——— 5) Ejecutar ACO y devolver resultado ———
            var rutas = acoAlgorithmService.generarRutasOptimizadas(m, params);
            mapa.setRutas(rutas);
            return ResponseEntity.ok(rutas);

        } catch (Exception e) {
            return ResponseEntity
                    .badRequest()
                    .body("Error al planificar rutas: " + e.getMessage());
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

    // Auxiliar para poner fechas por defecto si hacen falta
    private void asegurarPeriodo(Mapa mapa, long cantidad, ChronoUnit unidad) {
        if (mapa.getFechaInicio() == null) {
            mapa.setFechaInicio(LocalDateTime.now());
        }
        if (mapa.getFechaFin() == null) {
            mapa.setFechaFin(mapa.getFechaInicio().plus(cantidad, unidad));
        }
    }
}
