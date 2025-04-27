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

    /**
     * Obtiene una representación formateada de la ruta completa
     * @return String con formato (x,y)>(x,y)>...
     */
    public String getRutaFormateada() {
        if (ubicaciones == null || ubicaciones.isEmpty()) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ubicaciones.size(); i++) {
            Ubicacion u = ubicaciones.get(i);
            sb.append("(").append(u.getX()).append(",").append(u.getY()).append(")");
            if (i < ubicaciones.size() - 1) {
                sb.append(">");
            }
        }
        return sb.toString();
    }
}
