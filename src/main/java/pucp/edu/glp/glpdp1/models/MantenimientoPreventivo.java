package pucp.edu.glp.glpdp1.models;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class MantenimientoPreventivo {
    private @Getter @Setter LocalDate fecha;
    private @Getter @Setter String codigoCamion;
}
