package pucp.edu.glp.glpdp1.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class Pedido {
    private int idPedido;
    private Ubicacion destino;
    // fechaRegistro -> hora en la que se registro el pedido
    // horasLimite -> tiempo en el que se espera recibir el pedido max
    // fechaLimite -> fechaRegistro + horasLimite (xd)
    private int horasLimite;
    private LocalDateTime fechaLimite;
    private LocalDateTime fechaRegistro;
    private String idCliente;
    private double volumen;
}
