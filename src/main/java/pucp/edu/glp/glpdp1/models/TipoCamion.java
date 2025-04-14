package pucp.edu.glp.glpdp1.models;
import lombok.Getter;
import lombok.Setter;

public class TipoCamion {
    private @Getter @Setter String codigo; //TA, TB, TC, TD
    private @Getter @Setter double pesoTara;
    private @Getter @Setter double capacidad;
    private @Getter @Setter double pesoCargaLleno;


    public TipoCamion(String codigo,double pesoTara, double capacidad, double pesoCargaLleno) {
        this.codigo = codigo;
        this.pesoTara = pesoTara;
        this.capacidad = capacidad;
        this.pesoCargaLleno = pesoCargaLleno;
    }

    public double calcularPesoCombinado(double cargaActual){
        return pesoTara + (pesoCargaLleno * cargaActual / capacidad);
    }
}
