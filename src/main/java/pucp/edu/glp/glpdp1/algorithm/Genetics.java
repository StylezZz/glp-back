package pucp.edu.glp.glpdp1.algorithm;

import pucp.edu.glp.glpdp1.models.Flota;
import pucp.edu.glp.glpdp1.models.Mapa;
import pucp.edu.glp.glpdp1.models.Pedido;
import pucp.edu.glp.glpdp1.models.PlanRutas;

import java.time.LocalDateTime;
import java.util.List;

public class Genetics {
    private int tamPoblacion;
    private double tasaMutacion;
    private double tasaCruce;
    private int maxGeneraciones;

    public Genetics(int tamPoblacion, double tasaMutacion, double tasaCruce, int maxGeneraciones) {
        this.tamPoblacion = tamPoblacion;
        this.tasaMutacion = tasaMutacion;
        this.tasaCruce = tasaCruce;
        this.maxGeneraciones = maxGeneraciones;
    }

    public PlanRutas optimizar(List<Pedido> pedidos, Flota flota, Mapa mapa, LocalDateTime inicio){
        return null;
    }
}
