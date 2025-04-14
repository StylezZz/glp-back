package pucp.edu.glp.glpdp1.models;

import java.util.ArrayList;
import java.util.List;

public class Asignacion {
    private Camion camion;
    private List<Pedido> pedidos;
    private List<Coordenada> ruta;

    public Asignacion(Camion camion,List<Pedido> pedidos,List<Coordenada> ruta){
        this.camion = camion;
        this.pedidos = pedidos;
        this.ruta = ruta;
    }

    public Asignacion copiar(){
        return new Asignacion(this.camion,new ArrayList<>(this.pedidos),new ArrayList<>(this.ruta));
    }

    public Camion getCamion(){
        return camion;
    }

    public List<Pedido> getPedidos(){
        return pedidos;
    }

    public List<Coordenada> getRuta(){
        return ruta;
    }
}
