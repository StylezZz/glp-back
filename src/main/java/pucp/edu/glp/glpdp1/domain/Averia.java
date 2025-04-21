package pucp.edu.glp.glpdp1.domain;

import lombok.Getter;
import lombok.Setter;
import pucp.edu.glp.glpdp1.domain.enums.Incidente;
import pucp.edu.glp.glpdp1.domain.enums.Turnos;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter
public class Averia {
    private Turnos turno;
    private String codigo;
    private Incidente incidente;
    private LocalDateTime fechaIncidente;
}
