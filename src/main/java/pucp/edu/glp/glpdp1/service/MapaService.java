package pucp.edu.glp.glpdp1.service;

import org.springframework.stereotype.Service;
import pucp.edu.glp.glpdp1.domain.Bloqueo;
import pucp.edu.glp.glpdp1.domain.Mapa;
import pucp.edu.glp.glpdp1.domain.Pedido;

import java.io.IOException;
import java.util.List;

@Service
public class MapaService {

    private final PedidoService pedidoService;
    private final BloqueosService bloqueosService;

    public MapaService(PedidoService pedidoService, BloqueosService bloqueosService) {
        this.pedidoService = pedidoService;
        this.bloqueosService = bloqueosService;
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

    public void cargarBloqueosEnMapa(Mapa mapa,String rutaArchivo){
        try{
            List<Bloqueo> bloqueos = bloqueosService.cargarBloqueosDesdeArchivo(rutaArchivo);
            mapa.setBloqueos(bloqueos);
        }catch(IOException e){
            throw new RuntimeException("Error al cargar bloqueos desde archivo: " + rutaArchivo, e);
        }
    }

    public void cargarBloqueosEnMapaDesdeBytes(Mapa mapa, byte[] datos){
        try{
            List<Bloqueo> bloqueos = bloqueosService.cargarBloqueosDesdeBytes(datos);
            mapa.setBloqueos(bloqueos);
        }catch(IOException e){
            throw new RuntimeException("Error al cargar bloqueos desde datos binarios", e);
        }
    }
}