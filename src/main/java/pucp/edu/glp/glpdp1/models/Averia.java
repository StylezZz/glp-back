package pucp.edu.glp.glpdp1.models;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

public class Averia {
    private TipoAveria tipo;
    private @Getter @Setter String turno;
    private @Getter @Setter String codigoCamion;
    private @Getter @Setter LocalDateTime momento;
    private @Getter @Setter Coordenada ubicacion;

    public enum TipoAveria{
        TIPO1,TIPO2,TIPO3
    }

    public LocalDateTime calcularFinInmovilizacion(){
        switch(tipo){
            case TIPO1: return momento.plusHours(2);
            case TIPO2: return momento.plusHours(2);
            case TIPO3: return momento.plusHours(4);
            default: return momento;
        }
    }

    public LocalDateTime calcularDisponibilidad(){
        // Calcular cuándo vuelve a estar disponible según tipo y turno


        return null;
    }
}
