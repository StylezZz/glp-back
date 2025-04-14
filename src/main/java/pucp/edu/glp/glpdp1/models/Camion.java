package pucp.edu.glp.glpdp1.models;
import lombok.Setter;
import lombok.Getter;
public class Camion {
    private @Getter @Setter String codigo; // ej: TA01
    private @Getter @Setter TipoCamion tipo;
    private @Getter @Setter double capacidadTanque; // en galones
    private @Getter @Setter double combustibleActual; // en galones
    private @Getter @Setter double cargaActual; // en m³
    private @Getter @Setter Coordenada posicionActual;
    private @Getter @Setter EstadoCamion estado;

    public enum EstadoCamion {
        DISPONIBLE, EN_RUTA, MANTENIMIENTO_PREVENTIVO, AVERIA_TIPO1, AVERIA_TIPO2, AVERIA_TIPO3
    }

    // Constructor, getters, setters

    public double calcularConsumo(double distancia) {
        double peso = tipo.calcularPesoCombinado(cargaActual);
        return (distancia * peso) / 180.0;
    }

    public boolean puedeRecorrer(double distancia) {
        return calcularConsumo(distancia) <= combustibleActual;
    }

    public void cargarCombustible() {
        this.combustibleActual = capacidadTanque;
    }

    public boolean puedeLlevar(double cargaAdicional) {
        return (cargaActual + cargaAdicional) <= tipo.getCapacidad();
    }
}
