package pucp.edu.glp.glpdp1.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Almacen {
    private Coordenada ubicacion;
    private String nombre;
    private TipoAlmacen tipo;
    private double capacidad;

    public enum TipoAlmacen{
        CENTRAL, INTERMEDIO_NORTE,INTERMEDIO_ESTE
    }
    // TODO: Construir , getters , setters
}
