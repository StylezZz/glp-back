package pucp.edu.glp.glpdp1.domain;

import lombok.Getter;
import lombok.Setter;
import pucp.edu.glp.glpdp1.domain.enums.TipoAlmacen;

@Getter @Setter
public class Almacen {
    private TipoAlmacen tipoAlmacen;
    private Ubicacion ubicacion;
    private double capacidadEfectivaM3;
    private double capacidadActualM3;
}
