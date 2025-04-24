// src/main/java/pucp/edu/glp/glpdp1/algorithms/GeneticoResolver.java
package pucp.edu.glp.glpdp1.algorithms;

import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import pucp.edu.glp.glpdp1.domain.Mapa;
import pucp.edu.glp.glpdp1.domain.Pedido;
import pucp.edu.glp.glpdp1.service.BloqueosService;
import pucp.edu.glp.glpdp1.service.PedidoService;
import pucp.edu.glp.glpdp1.service.AveriaService;
import pucp.edu.glp.glpdp1.service.MapaService;

@Service
public class GeneticoResolver {
    private final PedidoService pedidoService;
    private final BloqueosService bloqueosService;
    private final AveriaService averiaService;
    private final MapaService mapaService;

    public GeneticoResolver(PedidoService ps,
                            BloqueosService bs,
                            AveriaService as,
                            MapaService ms) {
        this.pedidoService   = ps;
        this.bloqueosService = bs;
        this.averiaService   = as;
        this.mapaService     = ms;
    }

    public GA.Individual solve(byte[] pedidosBytes,
                               byte[] bloqueosBytes,
                               byte[] averiasBytes) throws Exception {
        // 1) Inicializar mapa
        Mapa mapa = new Mapa(70, 50);
        // 2) Cargar datos
        mapaService.cargarPedidosEnMapaDesdeBytes (mapa, pedidosBytes);
        mapaService.cargarBloqueosEnMapaDesdeBytes(mapa, bloqueosBytes);
        mapaService.cargarAveriasEnMapaDesdeBytes  (mapa, averiasBytes);
        // 3) Ajustar fecha de inicio al primer pedido
        LocalDateTime inicio = mapa.getPedidos().stream()
                .map(Pedido::getFechaRegistro)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());
        mapa.setFechaInicio(inicio);

        // 4) Ejecutar GA
        GA ga = new GA(mapa, 100, 500, 0.8, 0.1, 0.1);
        GA.Individual best = ga.run();

        // 5)   imprimir en consola
        ga.printSolution(best);

        // 6) Devolver la mejor solución
        return best;
    }
}
