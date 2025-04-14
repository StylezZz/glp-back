package pucp.edu.glp.glpdp1.models;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class Ruta {
    private Camion camion;
    private List<Pedido> pedidos;
    private List<Coordenada> nodos;
    private LocalDateTime horaSalida;
    private double distanciaTotal;
    private double consumoTotal;

    public void calcularConsumoyDistancia(){
        // Implementar el calculo de la distancia total y consumo estimado
    }

    public List<LocalDateTime> estimarTiemposLlegada(){
        // Estimar los tiempos de llegada a cada nodo basado en la velocidad y distancia
        return null;
    }

    public boolean validarFactibilidad(Mapa mapa){
        // Verificar si la ruta es factible considerando bloqueos y restricciones
        return false;
    }

    public double getConsumoEstimado(){
        return 0;
    }
}
