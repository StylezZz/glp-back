package pucp.edu.glp.glpdp1.algorithm;

import org.springframework.cglib.core.Local;
import pucp.edu.glp.glpdp1.models.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ACO {
    // Constantes
    private static final int NUM_HORMIGAS_DEFAULT = 30;
    private static final double TASA_EVAPORACION_DEFAULT = 0.5;
    private static final double ALPHA_DEFAULT = 1.0;
    private static final double BETA_DEFAULT = 2.0;
    private static final double Q0_DEFAULT = 0.9;
    private static final double FEROMONA_INCIAL = 0.1;

    // Atributos
    private int numHormigas;
    private double tasaEvaporacion;
    private double alpha;
    private double beta;
    private double q0;
    private double[][] feromonas;
    private double mejorCosto;
    private PlanRutas mejorSolucion;
    private Coordenada ultimaPosicion;

    public ACO(){
        this(NUM_HORMIGAS_DEFAULT, TASA_EVAPORACION_DEFAULT, ALPHA_DEFAULT, BETA_DEFAULT, Q0_DEFAULT);
    }

    public ACO(int numHormigas,double tasaEvaporacion,double alpha,double beta,double q0){
        this.numHormigas = numHormigas;
        this.tasaEvaporacion = tasaEvaporacion;
        this.alpha = alpha;
        this.beta = beta;
        this.q0 = q0;
    }

    public PlanRutas optimizar(List<Pedido> pedidos,Flota flota,Mapa mapa, LocalDateTime inicio){
        inicializarFeromonas(mapa);
        mejorCosto = Double.MAX_VALUE;
        mejorSolucion = null;

        for(int iteracion = 0; iteracion < 500; iteracion++){
            List<PlanRutas> soluciones = new ArrayList<>();
            for(int h=0;h<numHormigas;h++) {
                PlanRutas solucion = construirSolucion(pedidos, flota, mapa, inicio);
                double costo = evaluarSolucion(solucion);
                soluciones.add(solucion);

                if (costo < mejorCosto) {
                    mejorCosto = costo;
                    mejorSolucion = solucion;
                }
            }
            actualizarFeromonas(soluciones);
        }
        return mejorSolucion;
    }

    private void inicializarFeromonas(Mapa mapa){
        feromonas = new double[mapa.getAncho()][mapa.getAlto()];

        for(int i=0;i<mapa.getAncho();i++){
            for(int j=0;j<mapa.getAlto();j++){
                feromonas[i][j] = FEROMONA_INCIAL;
            }
        }
    }

    private PlanRutas construirSolucion(List<Pedido> pedidos,Flota flota, Mapa mapa, LocalDateTime inicio){
        PlanRutas plan = new PlanRutas();
        List<Pedido> pedidosPendientes = new ArrayList<>(pedidos);

        ultimaPosicion = mapa.getAlmacenes().get(0).getUbicacion();

        while(!pedidosPendientes.isEmpty()){
            double q = Math.random();
            Pedido siguiente;
            if(q <= q0){
                // Explotación: elegir mejor opción
                siguiente = seleccionarMejorPedido(pedidosPendientes,mapa);
            }else{
                siguiente = seleccionarPedidoProbabilistico(pedidosPendientes, mapa);
            }
            asignarPedidoACamion(siguiente,plan,flota);
            ultimaPosicion = siguiente.getUbicacion();
            pedidosPendientes.remove(siguiente);
        }
        return plan;
    }

    private Pedido seleccionarMejorPedido(List<Pedido> pedidosPendientes, Mapa mapa){
        double mejorValor = -1;
        Pedido mejorPedido = null;
        for(Pedido pedido: pedidosPendientes){
            double tau = obtenerFeromona(pedido, mapa);
            double eta = obtenerHeuristica(pedido, mapa);
            double valor = Math.pow(tau,alpha)*Math.pow(eta,beta);
            if(valor > mejorValor) {
                mejorValor = valor;
                mejorPedido = pedido;
            }
        }
        return mejorPedido;
    }

    private double obtenerFeromona(Pedido pedido, Mapa mapa){
        int x = pedido.getUbicacion().getX();
        int y = pedido.getUbicacion().getY();
        return feromonas[x][y];
    }

    private double obtenerHeuristica(Pedido pedido, Mapa mapa) {
        // 1.- Distancia al pedido (usando distacia Manhattan implementada en Coordenada)
        double distancia = pedido.getUbicacion().distancia(ultimaPosicion);

        // 2.- Urgencia: tiempo restante hasta el límite
        double tiempoRestante = pedido.getTiempoLimite().toMinutes();

        // 3.- Factor de cantidad
        double cantidad = pedido.getCantidad();

        double valorHeuristica = 1.0/(distancia*Math.max(1.0,tiempoRestante/60.0)*Math.max(1.0,cantidad/100.0));

        return valorHeuristica;
    }

    private Pedido seleccionarPedidoProbabilistico(List<Pedido> pedidosPendientes, Mapa mapa){
        //Implementación simplificada
        return pedidosPendientes.get(0);
    }

    private void asignarPedidoACamion(Pedido pedido,PlanRutas plan,Flota flota){
        // Implementación de asignación de pedido a camión
    }

    private double evaluarSolucion(PlanRutas solucion){
        //Implementación de evaluación de la solución
        return 0.0;
    }

    private void actualizarFeromonas(List<PlanRutas> soluciones){
        // Evaporación
        for(int i=0;i<feromonas.length;i++){
            for(int j=0;j<feromonas[i].length;j++){
                feromonas[i][j] *= (1 - tasaEvaporacion);
            }
        }

        // Depósito de feromonas
        for(PlanRutas solucion: soluciones) {
            double deltaTau = 1.0 / evaluarSolucion(solucion);
        }
    }
}
