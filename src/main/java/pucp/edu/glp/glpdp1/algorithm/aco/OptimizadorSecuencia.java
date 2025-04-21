package pucp.edu.glp.glpdp1.aco;

import pucp.edu.glp.glpdp1.models.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Optimiza la secuencia de entregas para minimizar viajes en vacío (RF98)
 * También implementa agrupamiento inteligente por proximidad (RF85)
 */
public class OptimizadorSecuencia {

    /**
     * Agrupa pedidos por proximidad geográfica (RF85)
     * @param pedidos Lista de pedidos a agrupar
     * @return Lista de grupos de pedidos cercanos
     */
    public List<List<Pedido>> agruparPedidosPorProximidad(List<Pedido> pedidos) {
        List<List<Pedido>> grupos = new ArrayList<>();
        List<Pedido> pendientes = new ArrayList<>(pedidos);

        // Radio de agrupamiento (distancia Manhattan)
        final int RADIO_AGRUPAMIENTO = 10;

        while (!pendientes.isEmpty()) {
            // Tomar el primer pedido como semilla para un nuevo grupo
            Pedido semilla = pendientes.remove(0);
            List<Pedido> grupoActual = new ArrayList<>();
            grupoActual.add(semilla);

            // Buscar pedidos cercanos
            List<Pedido> cercanos = new ArrayList<>();
            Iterator<Pedido> iterator = pendientes.iterator();

            while (iterator.hasNext()) {
                Pedido candidato = iterator.next();
                if (semilla.getUbicacion().distancia(candidato.getUbicacion()) <= RADIO_AGRUPAMIENTO) {
                    cercanos.add(candidato);
                    iterator.remove();
                }
            }

            // Ordenar pedidos cercanos por urgencia
            Collections.sort(cercanos, Comparator.comparing(p ->
                    p.getFechaPedido().plus(p.getTiempoLimite())));

            // Añadir al grupo actual
            grupoActual.addAll(cercanos);
            grupos.add(grupoActual);
        }

        return grupos;
    }

    /**
     * Optimiza la secuencia de entregas para minimizar viajes en vacío (RF98)
     * @param camion Camión que realizará las entregas
     * @param pedidos Lista de pedidos a entregar
     * @return Lista de pedidos en orden óptimo
     */
    public List<Pedido> optimizarSecuenciaEntregas(Camion camion, List<Pedido> pedidos) {
        if (pedidos.isEmpty()) {
            return new ArrayList<>();
        }

        // Primero ordenar por urgencia (tiempo límite)
        List<Pedido> ordenadosPorUrgencia = new ArrayList<>(pedidos);
        Collections.sort(ordenadosPorUrgencia, Comparator.comparing(p ->
                p.getFechaPedido().plus(p.getTiempoLimite())));

        // Si hay pedidos muy urgentes, priorizarlos indistintamente de la ruta
        LocalDateTime ahora = LocalDateTime.now();
        List<Pedido> muyUrgentes = new ArrayList<>();
        List<Pedido> normales = new ArrayList<>();

        for (Pedido pedido : ordenadosPorUrgencia) {
            LocalDateTime limite = pedido.getFechaPedido().plus(pedido.getTiempoLimite());
            // Si queda menos de 2 horas para el límite, es muy urgente
            if (limite.isBefore(ahora.plusHours(2))) {
                muyUrgentes.add(pedido);
            } else {
                normales.add(pedido);
            }
        }

        // Para los pedidos normales, optimizar la ruta
        List<Pedido> normalesOptimizados = optimizarRutaParaPedidos(camion.getPosicionActual(), normales);

        // Combinar urgentes + normales optimizados
        List<Pedido> resultado = new ArrayList<>(muyUrgentes);
        resultado.addAll(normalesOptimizados);

        return resultado;
    }

    /**
     * Optimiza la ruta para un conjunto de pedidos usando un algoritmo de inserción más cercana
     * @param ubicacionInicial Ubicación inicial del camión
     * @param pedidos Lista de pedidos a ordenar
     * @return Lista de pedidos en orden óptimo para la ruta
     */
    private List<Pedido> optimizarRutaParaPedidos(Coordenada ubicacionInicial, List<Pedido> pedidos) {
        if (pedidos.isEmpty()) {
            return new ArrayList<>();
        }

        List<Pedido> resultado = new ArrayList<>();
        List<Pedido> pendientes = new ArrayList<>(pedidos);
        Coordenada ubicacionActual = ubicacionInicial;

        while (!pendientes.isEmpty()) {
            // Encontrar el pedido más cercano a la ubicación actual
            Pedido masCercano = null;
            double distanciaMinima = Double.MAX_VALUE;

            for (Pedido candidato : pendientes) {
                double distancia = ubicacionActual.distancia(candidato.getUbicacion());
                if (distancia < distanciaMinima) {
                    distanciaMinima = distancia;
                    masCercano = candidato;
                }
            }

            // Añadir el pedido más cercano al resultado
            resultado.add(masCercano);
            pendientes.remove(masCercano);

            // Actualizar ubicación actual
            ubicacionActual = masCercano.getUbicacion();
        }

        return resultado;
    }

    /**
     * Asigna pedidos a camiones de manera optimizada, agrupando por proximidad (RF85)
     * @param pedidos Lista de pedidos pendientes
     * @param camiones Lista de camiones disponibles
     * @return Mapa de camiones a listas de pedidos asignados
     */
    public Map<Camion, List<Pedido>> asignarPedidosACamionesOptimizado(List<Pedido> pedidos, List<Camion> camiones) {
        Map<Camion, List<Pedido>> asignaciones = new HashMap<>();

        // Si no hay pedidos o camiones, retornar mapa vacío
        if (pedidos.isEmpty() || camiones.isEmpty()) {
            return asignaciones;
        }

        // Agrupar pedidos por proximidad
        List<List<Pedido>> gruposPedidos = agruparPedidosPorProximidad(pedidos);

        // Ordenar grupos por urgencia (basada en el pedido más urgente de cada grupo)
        Collections.sort(gruposPedidos, (g1, g2) -> {
            LocalDateTime limite1 = obtenerTiempoLimiteGrupo(g1);
            LocalDateTime limite2 = obtenerTiempoLimiteGrupo(g2);
            return limite1.compareTo(limite2);
        });

        // Ordenar camiones por capacidad (mayor a menor)
        List<Camion> camionesPorCapacidad = new ArrayList<>(camiones);
        Collections.sort(camionesPorCapacidad, (c1, c2) ->
                Double.compare(c2.getTipo().getCapacidad(), c1.getTipo().getCapacidad()));

        // Asignar grupos a camiones
        for (List<Pedido> grupo : gruposPedidos) {
            double volumeTotal = calcularVolumenTotal(grupo);
            Camion camionAsignado = null;

            // Buscar el camión con capacidad suficiente y más ajustada
            for (Camion camion : camionesPorCapacidad) {
                double capacidadDisponible = camion.getTipo().getCapacidad() - camion.getCargaActual();

                if (capacidadDisponible >= volumeTotal) {
                    camionAsignado = camion;
                    break;
                }
            }

            if (camionAsignado != null) {
                // Asignar el grupo completo al camión
                List<Pedido> asignadosAlCamion = asignaciones.computeIfAbsent(camionAsignado, k -> new ArrayList<>());
                asignadosAlCamion.addAll(grupo);

                // Actualizar la carga actual del camión
                camionAsignado.setCargaActual(camionAsignado.getCargaActual() + volumeTotal);
            } else {
                // Si ningún camión tiene capacidad suficiente para el grupo completo,
                // intentar asignar pedidos individuales a camiones con espacio
                for (Pedido pedido : grupo) {
                    for (Camion camion : camionesPorCapacidad) {
                        double capacidadDisponible = camion.getTipo().getCapacidad() - camion.getCargaActual();

                        if (capacidadDisponible >= pedido.getCantidad()) {
                            // Asignar pedido individual
                            List<Pedido> asignadosAlCamion = asignaciones.computeIfAbsent(camion, k -> new ArrayList<>());
                            asignadosAlCamion.add(pedido);

                            // Actualizar carga
                            camion.setCargaActual(camion.getCargaActual() + pedido.getCantidad());
                            break;
                        }
                    }
                }
            }
        }

        // Optimizar la secuencia de cada asignación
        for (Map.Entry<Camion, List<Pedido>> entry : asignaciones.entrySet()) {
            Camion camion = entry.getKey();
            List<Pedido> pedidosAsignados = entry.getValue();

            // Reordenar los pedidos para optimizar la ruta
            List<Pedido> secuenciaOptima = optimizarSecuenciaEntregas(camion, pedidosAsignados);

            // Reemplazar la lista original con la secuencia optimizada
            entry.setValue(secuenciaOptima);
        }

        return asignaciones;
    }

    /**
     * Calcula el tiempo límite más urgente de un grupo de pedidos
     */
    private LocalDateTime obtenerTiempoLimiteGrupo(List<Pedido> grupo) {
        if (grupo.isEmpty()) {
            return LocalDateTime.now().plusYears(100); // Valor lejano por defecto
        }

        LocalDateTime masUrgente = grupo.get(0).getFechaPedido().plus(grupo.get(0).getTiempoLimite());

        for (int i = 1; i < grupo.size(); i++) {
            Pedido pedido = grupo.get(i);
            LocalDateTime limite = pedido.getFechaPedido().plus(pedido.getTiempoLimite());

            if (limite.isBefore(masUrgente)) {
                masUrgente = limite;
            }
        }

        return masUrgente;
    }

    /**
     * Calcula el volumen total de un grupo de pedidos
     */
    private double calcularVolumenTotal(List<Pedido> pedidos) {
        double total = 0;
        for (Pedido pedido : pedidos) {
            total += pedido.getCantidad();
        }
        return total;
    }
}