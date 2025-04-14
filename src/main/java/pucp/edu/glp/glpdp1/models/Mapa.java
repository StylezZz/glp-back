package pucp.edu.glp.glpdp1.models;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Mapa {
    private @Getter @Setter int ancho;
    private @Getter @Setter int alto;
    private List<Bloqueo> bloqueos;
    private List<Almacen> almacenes;

    public Mapa(int ancho,int alto){
        this.ancho = ancho;
        this.alto = alto;
        this.bloqueos = new ArrayList<>();
        this.almacenes = new ArrayList<>();
    }

    public void agregarBloqueo(Bloqueo bloqueo){
        bloqueos.add(bloqueo);
    }

    public void agregarAlmacen(Almacen almacen){
        almacenes.add(almacen);
    }

    public boolean estaBloqueado(Coordenada desde, Coordenada hasta, LocalDateTime momento){
        for(Bloqueo bloqueo: bloqueos){
            if(bloqueo.afecta(desde, hasta, momento)){
                return true;
            }
        }
        return false;
    }

    public List<Coordenada> obtenerVecinos(Coordenada posicion,LocalDateTime momento){
        List<Coordenada> vecinos = new ArrayList<>();
        // TODO: Implementar la logica para obtener los vecinos
        return vecinos;
    }
}
