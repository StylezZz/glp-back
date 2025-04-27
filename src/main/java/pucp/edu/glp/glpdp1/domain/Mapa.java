package pucp.edu.glp.glpdp1.domain;

import lombok.Getter;
import lombok.Setter;
import pucp.edu.glp.glpdp1.domain.enums.EstadoCamion;
import pucp.edu.glp.glpdp1.domain.enums.TipoAlmacen;
import pucp.edu.glp.glpdp1.domain.enums.TipoCamion;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

@Getter
@Setter
public class Mapa {
    private final int ancho, alto;
    private List<Camion> flota = new ArrayList<>();
    private List<Pedido> pedidos = new ArrayList<>();
    private List<Bloqueo> bloqueos = new ArrayList<>();
    private List<Almacen> almacenes = new ArrayList<>();
    private List<Averia> averias = new ArrayList<>();
    private List<Rutas> rutas;
    private LocalDateTime fechaInicio, fechaFin;

    public Mapa(int ancho, int alto) {
        this.ancho = ancho;
        this.alto = alto;
        inicializarAlmacenes();
        inicializarFlota();
    }

    private void inicializarAlmacenes() {
        var specs = List.of(
                new AlmacenSpec(TipoAlmacen.CENTRAL, 12, 8, 160.0),
                new AlmacenSpec(TipoAlmacen.INTERMEDIO_ESTE, 63, 3, 160.0),
                new AlmacenSpec(TipoAlmacen.INTERMEDIO_NORTE, 42, 42, 160.0)
        );
        specs.stream()
                .map(s -> {
                    var a = new Almacen();
                    a.setTipoAlmacen(s.tipo());
                    a.setUbicacion(new Ubicacion(s.x(), s.y()));
                    a.setCapacidadEfectivaM3(s.capacidad());
                    return a;
                })
                .forEach(almacenes::add);
    }

    private void inicializarFlota() {
        var specs = List.of(
                new CamionSpec(TipoCamion.TD, "TD", 1.0, 5.0, 2.5, 3.5, 10),
                new CamionSpec(TipoCamion.TC, "TC", 1.5, 10.0, 5.0, 6.5, 4),
                new CamionSpec(TipoCamion.TB, "TB", 2.0, 15.0, 7.5, 9.5, 4),
                new CamionSpec(TipoCamion.TA, "TA", 2.5, 25.0, 12.5, 15.0, 2)
        );

        specs.forEach(spec ->
                IntStream.rangeClosed(1, spec.count()).mapToObj(i -> {
                    var c = new Camion();
                    c.setId(spec.prefix() + String.format("%02d", i));
                    c.setTipo(spec.tipo());
                    c.setPesoBrutoTon(spec.pesoBrutoTon());
                    c.setCargaM3(spec.cargaM3());
                    c.setPesoCargaTon(spec.pesoCargaTon());
                    c.setPesoCombinadoTon(spec.pesoCombinadoTon());
                    c.setEstado(EstadoCamion.DISPONIBLE);
                    c.setGalones(25);
                    return c;
                }).forEach(flota::add)
        );
    }

    // Specs auxiliares para inicializar sin repetir
    private record AlmacenSpec(TipoAlmacen tipo, int x, int y, double capacidad) {
    }

    private record CamionSpec(
            TipoCamion tipo,
            String prefix,
            double pesoBrutoTon,
            double cargaM3,
            double pesoCargaTon,
            double pesoCombinadoTon,
            int count
    ) {
    }
}
