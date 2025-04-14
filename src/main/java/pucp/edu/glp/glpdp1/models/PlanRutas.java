package pucp.edu.glp.glpdp1.models;

import java.time.LocalDateTime;
import java.util.List;

public class PlanRutas {
    private LocalDateTime fechaPlan;
    private List<Ruta> rutas;

    public double calcularConsumoTotal(){
        return rutas.stream()
                .mapToDouble(Ruta::getConsumoEstimado)
                .sum();
    }
}
