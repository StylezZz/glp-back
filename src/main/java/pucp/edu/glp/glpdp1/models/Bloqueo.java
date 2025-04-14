package pucp.edu.glp.glpdp1.models;

import org.springframework.cglib.core.Local;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Bloqueo {
    private LocalDateTime inicio;
    private LocalDateTime fin;
    private List<Coordenada> nodosPoligono;

    public Bloqueo(LocalDateTime inicio, LocalDateTime fin, List<Coordenada>nodos){
        this.inicio = inicio;
        this.fin = fin;
        this.nodosPoligono = new ArrayList<>(nodos);
    }

    public boolean estaActivo(LocalDateTime momento){
        return !momento.isBefore(inicio) && !momento.isAfter(fin);
    }

    public boolean afecta(Coordenada desde, Coordenada hasta, LocalDateTime momento){
        if(!estaActivo(momento))return false;

        for(int i=0;i<nodosPoligono.size();i++){
            Coordenada p1 = nodosPoligono.get(i);
            Coordenada p2 = nodosPoligono.get(i+1);
            if(intersectan(desde,hasta,p1,p2)){
                return true;
            }
        }

        return nodosPoligono.contains(desde) || nodosPoligono.contains(hasta);
    }

    private boolean intersectan(Coordenada a,Coordenada b, Coordenada c, Coordenada d){
        //Implementación del algoritmo para determinar si dos segmentos se intersectan

        return false;
    }

    public LocalDateTime getInicio(){ return inicio;}
    public LocalDateTime getFin(){return fin;}
    public List<Coordenada> getNodosPoligono(){return nodosPoligono;}
}
