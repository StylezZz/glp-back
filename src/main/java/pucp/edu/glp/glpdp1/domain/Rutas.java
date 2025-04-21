package pucp.edu.glp.glpdp1.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter @Setter
public class Rutas {
    private int id;
    private Camion camion;
    private List<Ubicacion> ubicaciones;
    private double distanciaTotal;
    private double tiempoTotal;
    private double consumoTotal;
}
