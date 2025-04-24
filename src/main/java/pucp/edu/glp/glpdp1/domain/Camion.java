package pucp.edu.glp.glpdp1.domain;

import lombok.Getter;
import lombok.Setter;
import pucp.edu.glp.glpdp1.domain.enums.EstadoCamion;
import pucp.edu.glp.glpdp1.domain.enums.TipoCamion;

@Getter @Setter
public class Camion {
    private String idC;
    private TipoCamion tipo;
    private double pesoBrutoTon;       // ton
    private double cargaM3;           // m3
    private double pesoCargaTon;       // ton
    private double pesoCombinadoTon; // ton
    private double distanciaMaximaKm; // km
    private int galones;
    private boolean averiado;
    private EstadoCamion estado;


    //peso bruto = peso del camion
    //carga = peso de la carga volumen
    //peso carga = lo que llevo
    //peso = peso del camion + peso de la carga

    public boolean puedeCargar(double volumen){
        double pesoCarga = volumen * pesoCargaTon;
        return this.getPesoCombinadoTon() >= pesoCarga;
    }
}
