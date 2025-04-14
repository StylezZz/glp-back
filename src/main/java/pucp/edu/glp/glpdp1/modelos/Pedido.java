package pucp.edu.glp.glpdp1.modelos;

import java.time.LocalDateTime;

public class Pedido {
    private String codigoCliente;
    private Coordenada ubicacion;
    private double volumeSolicitado;
    private LocalDateTime fechaHoraPedido;
    private int horasLimite;

    public Pedido(String codigoCliente, Coordenada ubicacion, double volumeSolicitado, LocalDateTime fechaHoraPedido, int horasLimite) {
        this.codigoCliente = codigoCliente;
        this.ubicacion = ubicacion;
        this.volumeSolicitado = volumeSolicitado;
        this.fechaHoraPedido = fechaHoraPedido;
        this.horasLimite = horasLimite;
    }

    public String getCodigoCliente() {
        return codigoCliente;
    }

    public void setCodigoCliente(String codigoCliente) {
        this.codigoCliente = codigoCliente;
    }

    public Coordenada getUbicacion() {
        return ubicacion;
    }

    public void setUbicacion(Coordenada ubicacion) {
        this.ubicacion = ubicacion;
    }

    public double getVolumeSolicitado() {
        return volumeSolicitado;
    }

    public void setVolumeSolicitado(double volumeSolicitado) {
        this.volumeSolicitado = volumeSolicitado;
    }

    public LocalDateTime getFechaHoraPedido() {
        return fechaHoraPedido;
    }

    public void setFechaHoraPedido(LocalDateTime fechaHoraPedido) {
        this.fechaHoraPedido = fechaHoraPedido;
    }

    public int getHorasLimite() {
        return horasLimite;
    }

    public void setHorasLimite(int horasLimite) {
        this.horasLimite = horasLimite;
    }

    public LocalDateTime getFechaLimiteEntrega(){
        return fechaHoraPedido.plusHours(horasLimite);
    }
}
