package pucp.edu.glp.glpdp1.modelos;

public class Camion {
    private String codigo;
    private String tipo;
    private double tara;
    private double capacidadM3;
    private double pesoCargaLleno;
    private boolean disponible;
    private double combustibleActual;

    public Camion(String codigo, String tipo, double tara, double capacidadM3, double pesoCargaLleno) {
        this.codigo = codigo;
        this.tipo = tipo;
        this.tara = tara;
        this.capacidadM3 = capacidadM3;
        this.pesoCargaLleno = pesoCargaLleno;
        this.disponible = true;
        this.combustibleActual = 0.0;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getTipo() {
        return tipo;
    }

    public double getTara() {
        return tara;
    }

    public double getCapacidadM3() {
        return capacidadM3;
    }

    public double getPesoCargaLleno() {
        return pesoCargaLleno;
    }

    public boolean isDisponible() {
        return disponible;
    }

    public void setDisponible(boolean disponible) {
        this.disponible = disponible;
    }

    public double getCombustibleActual() {
        return combustibleActual;
    }

    public double getPesoCombina(double m3cargado){
        return tara + (pesoCargaLleno*(m3cargado/capacidadM3));
    }

    public double calcularConsumo(double distanciaKm,double pesoTon){
        return (distanciaKm*pesoTon)/100.0;
    }
}
