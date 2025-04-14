package pucp.edu.glp.glpdp1.models;

import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.LocalDateTime;

public class Pedido {
    private @Getter @Setter String idCliente;
    private @Getter @Setter LocalDateTime fechaPedido;
    private @Getter @Setter Coordenada ubicacion;
    private @Getter @Setter double cantidad;
    private @Getter @Setter Duration tiempoLimite;
    private @Getter @Setter EstadoPedido estado;

    public enum EstadoPedido {
        PENDIENTE, ASIGNADO, ENTREGADO, CANCELADO
    }

    public Pedido(String idCliente, LocalDateTime fechaPedido, Coordenada ubicacion,
                  double cantidad, Duration tiempoLimite) {
        this.idCliente = idCliente;
        this.fechaPedido = fechaPedido;
        this.ubicacion = ubicacion;
        this.cantidad = cantidad;
        this.tiempoLimite = tiempoLimite;
        this.estado = EstadoPedido.PENDIENTE;
    }

}
