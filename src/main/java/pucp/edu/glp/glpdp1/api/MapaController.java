package pucp.edu.glp.glpdp1.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pucp.edu.glp.glpdp1.domain.Mapa;
import pucp.edu.glp.glpdp1.service.MapaService;

import java.io.IOException;

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
    public ResponseEntity<String> cargarPedidos(@RequestParam("archivo") MultipartFile archivo) {
        try {
            // Cargar pedidos desde el archivo subido
            mapaService.cargarPedidosEnMapaDesdeBytes(mapa, archivo.getBytes());
            return ResponseEntity.ok("Pedidos cargados correctamente. Total: " + mapa.getPedidos().size());
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Error al cargar pedidos: " + e.getMessage());
        }
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
}