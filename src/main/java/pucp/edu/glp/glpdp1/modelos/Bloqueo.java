package pucp.edu.glp.glpdp1.modelos;

import java.time.LocalDateTime;
import java.util.List;

public class Bloqueo {
    private LocalDateTime inicio;
    private LocalDateTime fin;
    private List<Coordenada> nodosBloqueados;

    public Bloqueo(LocalDateTime inicio, LocalDateTime fin, List<Coordenada>puntosPoligono){
        this.inicio = inicio;
        this.fin = fin;
        this.nodosBloqueados = puntosPoligono;
    }

    public boolean estaActivo(LocalDateTime momento){
        return momento.isAfter(inicio) && momento.isBefore(fin);
    }

    public boolean nodoBloqueado(Coordenada nodo){
        return nodosBloqueados.contains(nodo);
    }

    public LocalDateTime getInicio(){ return inicio;}
    public LocalDateTime getFin(){return fin;}
    public List<Coordenada> getNodosBloqueados(){return nodosBloqueados;}
}
