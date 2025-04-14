package pucp.edu.glp.glpdp1.models;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Flota {
    private List<Camion> camiones;

    public Flota(){
        this.camiones = new ArrayList<>();
    }

    public void agregarCamion(Camion camion){
        camiones.add(camion);
    }

    public List<Camion> obtenerDisponibles(){
        return camiones.stream()
                .filter(c -> c.getEstado() == Camion.EstadoCamion.DISPONIBLE)
                .collect(Collectors.toList());
    }
}
