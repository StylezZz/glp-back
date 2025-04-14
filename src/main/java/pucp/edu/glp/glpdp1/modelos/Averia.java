package pucp.edu.glp.glpdp1.modelos;

import java.time.LocalDateTime;

public class Averia {
    private String codigoCamion;
    private String tipoIncidente;
    private LocalDateTime momento;
    private Coordenada ubicacion;

    public int getDuracionHorasInmovilizado(){
        return switch(tipoIncidente){
            case "TI1" -> 2;
            case "TI2" -> 2;
            case "TI3" -> 4;
            default -> 0;
        };
    }

    public int getTiempoTallerHoras(){
        return switch(tipoIncidente){
            case "TI2" -> 8;
            case "TI3" -> 24;
            default -> 0;
        };
    }
}
