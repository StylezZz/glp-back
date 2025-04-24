package pucp.edu.glp.glpdp1.domain;

import lombok.Getter;
import lombok.Setter;
import pucp.edu.glp.glpdp1.domain.Rutas;
import pucp.edu.glp.glpdp1.domain.enums.TipoAlmacen;
import pucp.edu.glp.glpdp1.domain.enums.TipoCamion;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Getter @Setter
public class Mapa {
    private int ancho;
    private int alto;
    private List<Camion> flota;
    private List<Pedido> pedidos;
    private List<Bloqueo> bloqueos;
    private List<Almacen> almacenes;
    private List<Averia> averias;
    private List<Rutas> rutas;
    // Por el momento
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;

    // Setear almacenes
    public Mapa(int ancho,int alto){
        this.ancho = ancho;
        this.alto = alto;
        this.almacenes = new ArrayList<>();
        this.flota = new ArrayList<>();
        this.pedidos = new ArrayList<>();
        this.bloqueos = new ArrayList<>();

        cargaAlmacenes();
        cargaFlota();
        //cargarAverias(nombreArchivo);
    }

    private void cargaAlmacenes(){
        //Iniciar almancenes
        Almacen almacen1 = new Almacen();
        almacen1.setTipoAlmacen(TipoAlmacen.CENTRAL);
        almacen1.setUbicacion(new Ubicacion(12,8));
        almacen1.setCapacidadEfectivaM3(160.0);

        Almacen almacen2 = new Almacen();
        almacen2.setTipoAlmacen(TipoAlmacen.INTERMEDIO_ESTE);
        almacen2.setUbicacion(new Ubicacion(63,3));
        almacen2.setCapacidadEfectivaM3(160.0);

        Almacen almacen3 = new Almacen();
        almacen3.setTipoAlmacen(TipoAlmacen.INTERMEDIO_NORTE);
        almacen3.setUbicacion(new Ubicacion(42,42));
        almacen3.setCapacidadEfectivaM3(160.0);

        this.almacenes.add(almacen1);
        this.almacenes.add(almacen2);
        this.almacenes.add(almacen3);
    }

    private void cargaFlota(){
        cargarTipoD();
        cargarTipoC();
        cargarTipoB();
        cargarTipoA();
    }

    private void cargarTipoD(){
        for(int i =0; i<10;i++){
            Camion camion = new Camion();
            camion.setIdC("TD"+String.format("%02d",i+1));
            camion.setTipo(TipoCamion.TD);
            camion.setPesoBrutoTon(1.0);
            camion.setCargaM3(5.0);
            camion.setPesoCargaTon(2.5);
            camion.setPesoCombinadoTon(3.5);
            this.flota.add(camion);
        }
    }

    private void cargarTipoC(){
        for(int i=0;i<4;i++){
            Camion camion = new Camion();
            camion.setIdC("TC"+String.format("%02d",i+1));
            camion.setTipo(TipoCamion.TC);
            camion.setPesoBrutoTon(1.5);
            camion.setCargaM3(10.0);
            camion.setPesoCargaTon(5.0);
            camion.setPesoCombinadoTon(6.5);
            this.flota.add(camion);
        }
    }

    private void cargarTipoB(){
        for(int i=0;i<4;i++){
            Camion camion = new Camion();
            camion.setIdC("TB"+String.format("%02d",i+1));
            camion.setTipo(TipoCamion.TB);
            camion.setPesoBrutoTon(2.0);
            camion.setCargaM3(15.0);
            camion.setPesoCargaTon(7.5);
            camion.setPesoCombinadoTon(9.5);
            this.flota.add(camion);
        }
    }

    private void cargarTipoA(){
        for(int i=0;i<2;i++){
            Camion camion = new Camion();
            camion.setIdC("TA"+String.format("%02d",i+1));
            camion.setTipo(TipoCamion.TA);
            camion.setPesoBrutoTon(2.5);
            camion.setCargaM3(25.0);
            camion.setPesoCargaTon(12.5);
            camion.setPesoCombinadoTon(15.0);
            this.flota.add(camion);
        }
    }
}