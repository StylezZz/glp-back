package pucp.edu.glp.glpdp1.service;

import org.springframework.stereotype.Service;
import pucp.edu.glp.glpdp1.domain.Mapa;
import pucp.edu.glp.glpdp1.domain.Pedido;
import pucp.edu.glp.glpdp1.domain.Averia;
import pucp.edu.glp.glpdp1.service.AveriaService;

import java.io.IOException;
import java.util.List;

@Service
public class MapaService {

    private final PedidoService pedidoService;
    private final AveriaService averiaService;

    public MapaService(PedidoService pedidoService, AveriaService averiaService) {
        this.pedidoService = pedidoService;
        this.averiaService = averiaService;
    }

    /**
     * Carga pedidos desde un archivo y los asigna al mapa
     */
    public void cargarPedidosEnMapa(Mapa mapa, String rutaArchivo) {
        try {
            List<Pedido> pedidos = pedidoService.cargarPedidosDesdeArchivo(rutaArchivo);
            mapa.setPedidos(pedidos);
        } catch (IOException e) {
            // Puedes manejar la excepción según la política de tu aplicación
            // Por ejemplo, loguear el error o relanzar una excepción personalizada
            throw new RuntimeException("Error al cargar pedidos desde archivo: " + rutaArchivo, e);
        }
    }

    /**
     * Carga pedidos desde bytes (desde API) y los asigna al mapa
     */
    public void cargarPedidosEnMapaDesdeBytes(Mapa mapa, byte[] datos) {
        try {
            List<Pedido> pedidos = pedidoService.cargarPedidosDesdeBytes(datos);
            mapa.setPedidos(pedidos);
        } catch (IOException e) {
            throw new RuntimeException("Error al cargar pedidos desde datos binarios", e);
        }
    }

    public void cargarAveriasEnMapaDesdeBytes(Mapa mapa, byte[] datos) {
        try {
            List<Averia> averias = averiaService.cargarAveriasDesdeBytes(datos);
            mapa.setAverias(averias);
        } catch (IOException e) {
            throw new RuntimeException("Error al cargar averias desde datos binarios", e);
        }
    }
}