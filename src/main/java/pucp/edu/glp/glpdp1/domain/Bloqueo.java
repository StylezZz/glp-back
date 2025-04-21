package pucp.edu.glp.glpdp1.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter
public class Bloqueo {
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFinal;
    private List<Ubicacion> tramos;

    public Bloqueo(LocalDateTime fechaInicio, LocalDateTime fechaFinal, List<Ubicacion> tramos) {
        this.fechaInicio = fechaInicio;
        this.fechaFinal = fechaFinal;
        this.tramos = tramos;
    }
}
