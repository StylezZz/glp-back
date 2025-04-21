package pucp.edu.glp.glpdp1.aco;

import pucp.edu.glp.glpdp1.models.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Clase que gestiona los tanques intermedios para reabastecimiento
 * Implementa RF86, RF88, RF96 y RF100
 */
public class GestorTanques {
    // Mapa de tanques intermedios (ubicación -> capacidad restante)
    private Map<Coordenada, Double> tanquesIntermedios;
    // Mapa de reservas planificadas (ubicación -> lista de reservas)
    private Map<Coordenada, List<ReservaCombustible>> reservas;
    // Mapa de tiempos de agotamiento estimados (ubicación -> tiempo)
    private Map<Coordenada, LocalDateTime> tiemposAgotamiento;

    public GestorTanques() {
        this.tanquesIntermedios = new HashMap<>();
        this.reservas = new HashMap<>();
        this.tiemposAgotamiento = new HashMap<>();
        inicializarTanques();
    }

    /**
     * Inicializa los tanques intermedios (RF96)
     */
    private void inicializarTanques() {
        // Definir ubicaciones de tanques intermedios
        Coordenada[] ubicacionesTanques = {
                new Coordenada(30, 30),
                new Coordenada(15, 40),
                new Coordenada(50, 20),
                new Coordenada(60, 40)
        };

        // Capacidades en galones
        double[] capacidades = {2000, 1500, 1500, 1000};

        // Inicializar tanques con capacidad completa
        for (int i = 0; i < ubicacionesTanques.length; i++) {
            tanquesIntermedios.put(ubicacionesTanques[i], capacidades[i]);
            reservas.put(ubicacionesTanques[i], new ArrayList<>());

            // Tiempo de agotamiento inicial (7 días)
            tiemposAgotamiento.put(ubicacionesTanques[i],
                    LocalDateTime.now().plusDays(7));
        }
    }

    /**
     * Rellena todos los tanques intermedios al inicio del día (RF96)
     */
    public void rellenarTanquesIntermedios() {
        // Definir capacidades máximas
        Map<Coordenada, Double> capacidadesMaximas = new HashMap<>();
        capacidadesMaximas.put(new Coordenada(30, 30), 2000.0);
        capacidadesMaximas.put(new Coordenada(15, 40), 1500.0);
        capacidadesMaximas.put(new Coordenada(50, 20), 1500.0);
        capacidadesMaximas.put(new Coordenada(60, 40), 1000.0);

        // Rellenar todos los tanques
        for (Map.Entry<Coordenada, Double> entry : capacidadesMaximas.entrySet()) {
            tanquesIntermedios.put(entry.getKey(), entry.getValue());
            reservas.put(entry.getKey(), new ArrayList<>());

            // Resetear tiempo de agotamiento
            tiemposAgotamiento.put(entry.getKey(),
                    LocalDateTime.now().plusDays(7));

            System.out.println("Tanque en " + entry.getKey() + " rellenado a capacidad máxima: " + entry.getValue() + " galones");
        }

        // Limpiar reservas antiguas
        for (List<ReservaCombustible> listaReservas : reservas.values()) {
            listaReservas.clear();
        }
    }

    /**
     * Verifica la disponibilidad de combustible en un tanque (RF88)
     * @param ubicacion Ubicación del tanque
     * @param cantidadRequerida Cantidad de combustible requerida
     * @return true si hay disponibilidad, false en caso contrario
     */
    public boolean verificarDisponibilidad(Coordenada ubicacion, double cantidadRequerida) {
        if (!tanquesIntermedios.containsKey(ubicacion)) {
            return false;
        }

        double disponible = calcularDisponibilidadReal(ubicacion);
        return disponible >= cantidadRequerida;
    }

    /**
     * Calcula la disponibilidad real considerando reservas (RF88)
     * @param ubicacion Ubicación del tanque
     * @return Cantidad disponible real
     */
    private double calcularDisponibilidadReal(Coordenada ubicacion) {
        double capacidadActual = tanquesIntermedios.getOrDefault(ubicacion, 0.0);
        double reservado = 0.0;

        // Sumar todas las reservas activas
        if (reservas.containsKey(ubicacion)) {
            for (ReservaCombustible reserva : reservas.get(ubicacion)) {
                if (reserva.estaActiva()) {
                    reservado += reserva.getCantidad();
                }
            }
        }

        return Math.max(0, capacidadActual - reservado);
    }

    /**
     * Realiza una reserva de combustible (RF100)
     * @param ubicacion Ubicación del tanque
     * @param camion Camión que realizará la recarga
     * @param tiempoEstimado Tiempo estimado de la recarga
     * @return true si se pudo reservar, false en caso contrario
     */
    public boolean reservarCombustible(Coordenada ubicacion, Camion camion, LocalDateTime tiempoEstimado) {
        if (!tanquesIntermedios.containsKey(ubicacion)) {
            return false;
        }

        // Estimar cantidad a recargar (tanque lleno - nivel actual)
        double cantidadEstimada = camion.getCapacidadTanque() - camion.getCombustibleActual();

        // Verificar disponibilidad
        if (!verificarDisponibilidad(ubicacion, cantidadEstimada)) {
            return false;
        }

        // Registrar reserva
        ReservaCombustible reserva = new ReservaCombustible(
                camion.getCodigo(), cantidadEstimada, tiempoEstimado);

        reservas.get(ubicacion).add(reserva);

        // Actualizar tiempo de agotamiento
        actualizarTiempoAgotamiento(ubicacion);

        return true;
    }

    /**
     * Realiza una recarga de combustible
     * @param ubicacion Ubicación del tanque
     * @param camion Camión a recargar
     * @return Cantidad recargada
     */
    public double realizarRecarga(Coordenada ubicacion, Camion camion) {
        if (!tanquesIntermedios.containsKey(ubicacion)) {
            return 0.0;
        }

        double disponible = calcularDisponibilidadReal(ubicacion);
        double necesario = camion.getCapacidadTanque() - camion.getCombustibleActual();
        double aRecargar = Math.min(disponible, necesario);

        if (aRecargar <= 0) {
            return 0.0;
        }

        // Actualizar nivel del tanque
        double nivelActual = tanquesIntermedios.get(ubicacion);
        tanquesIntermedios.put(ubicacion, nivelActual - aRecargar);

        // Actualizar nivel del camión
        camion.setCombustibleActual(camion.getCombustibleActual() + aRecargar);

        // Eliminar reserva correspondiente
        eliminarReserva(ubicacion, camion.getCodigo());

        // Actualizar tiempo de agotamiento
        actualizarTiempoAgotamiento(ubicacion);

        return aRecargar;
    }

    /**
     * Elimina una reserva después de realizarla
     */
    private void eliminarReserva(Coordenada ubicacion, String codigoCamion) {
        if (!reservas.containsKey(ubicacion)) {
            return;
        }

        List<ReservaCombustible> listaReservas = reservas.get(ubicacion);
        listaReservas.removeIf(r -> r.getCodigoCamion().equals(codigoCamion));
    }

    /**
     * Actualiza el tiempo estimado de agotamiento (RF100)
     */
    private void actualizarTiempoAgotamiento(Coordenada ubicacion) {
        if (!tanquesIntermedios.containsKey(ubicacion)) {
            return;
        }

        double nivelActual = tanquesIntermedios.get(ubicacion);
        double consumoDiario = estimarConsumoDiario(ubicacion);

        if (consumoDiario <= 0) {
            // Si no hay consumo estimado, poner tiempo lejano
            tiemposAgotamiento.put(ubicacion, LocalDateTime.now().plusMonths(1));
            return;
        }

        // Calcular días hasta agotamiento
        double diasHastaAgotamiento = nivelActual / consumoDiario;

        // Convertir a LocalDateTime
        LocalDateTime tiempoAgotamiento = LocalDateTime.now().plusDays((long)diasHastaAgotamiento);
        tiemposAgotamiento.put(ubicacion, tiempoAgotamiento);

        // Generar alerta si el tiempo es crítico (menos de 2 días)
        if (diasHastaAgotamiento < 2) {
            System.out.println("ALERTA: Tanque en " + ubicacion +
                    " se agotará en " + String.format("%.1f", diasHastaAgotamiento) +
                    " días. Programar recarga urgente.");
        }
    }

    /**
     * Estima el consumo diario basado en reservas y tendencia
     */
    private double estimarConsumoDiario(Coordenada ubicacion) {
        if (!reservas.containsKey(ubicacion)) {
            return 0.0;
        }

        // Sumar reservas para las próximas 24 horas
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime limite = ahora.plusDays(1);

        double totalReservado = 0.0;
        for (ReservaCombustible reserva : reservas.get(ubicacion)) {
            if (reserva.getTiempoEstimado().isAfter(ahora) &&
                    reserva.getTiempoEstimado().isBefore(limite)) {
                totalReservado += reserva.getCantidad();
            }
        }

        // Añadir factor histórico (simplificado para este ejemplo)
        double factorHistorico = 100.0; // 100 galones/día de base

        return totalReservado + factorHistorico;
    }

    /**
     * Selecciona el tanque óptimo para reabastecimiento (RF86, RF100)
     * @param ubicacionActual Ubicación actual del camión
     * @param destinos Lista de destinos a visitar
     * @param consumoEstimado Consumo estimado de la ruta
     * @return Ubicación del tanque óptimo, null si no es necesario
     */
    public Coordenada seleccionarTanqueOptimo(Coordenada ubicacionActual,
                                              List<Coordenada> destinos, double consumoEstimado, double combustibleActual) {

        // Si el combustible es suficiente, no necesitamos reabastecimiento
        if (consumoEstimado <= combustibleActual * 0.8) {
            return null;
        }

        // Encontrar el punto medio de la ruta
        Coordenada puntoMedio = calcularPuntoMedio(ubicacionActual, destinos);

        // Buscar tanque más cercano al punto medio con disponibilidad
        Coordenada mejorTanque = null;
        double mejorDistancia = Double.MAX_VALUE;

        for (Map.Entry<Coordenada, Double> entry : tanquesIntermedios.entrySet()) {
            Coordenada ubicacionTanque = entry.getKey();

            // Verificar disponibilidad (al menos 80% del tanque del camión)
            if (verificarDisponibilidad(ubicacionTanque, combustibleActual * 0.8)) {
                double distancia = puntoMedio.distancia(ubicacionTanque);

                // Criterio: proximidad al punto medio + prioridad por tiempo de agotamiento
                // Los tanques con más tiempo hasta agotamiento son preferibles
                LocalDateTime tiempoAgotamiento = tiemposAgotamiento.get(ubicacionTanque);

                // Factor de prioridad por tiempo (1.0 - 2.0)
                double factorTiempo = 1.0;
                if (tiempoAgotamiento != null) {
                    long diasHastaAgotamiento = java.time.Duration.between(
                            LocalDateTime.now(), tiempoAgotamiento).toDays();
                    factorTiempo = Math.min(2.0, 1.0 + (diasHastaAgotamiento / 30.0));
                }

                // Distancia ajustada (menor es mejor)
                double distanciaAjustada = distancia / factorTiempo;

                if (distanciaAjustada < mejorDistancia) {
                    mejorDistancia = distanciaAjustada;
                    mejorTanque = ubicacionTanque;
                }
            }
        }

        return mejorTanque;
    }

    /**
     * Calcula el punto medio aproximado de una ruta
     */
    private Coordenada calcularPuntoMedio(Coordenada inicio, List<Coordenada> destinos) {
        if (destinos.isEmpty()) {
            return inicio;
        }

        int sumaX = inicio.getX();
        int sumaY = inicio.getY();

        for (Coordenada destino : destinos) {
            sumaX += destino.getX();
            sumaY += destino.getY();
        }

        return new Coordenada(sumaX / (destinos.size() + 1), sumaY / (destinos.size() + 1));
    }

    /**
     * Obtiene todos los tanques disponibles
     * @return Mapa de ubicaciones a capacidades
     */
    public Map<Coordenada, Double> getTanquesDisponibles() {
        Map<Coordenada, Double> disponibles = new HashMap<>();

        for (Map.Entry<Coordenada, Double> entry : tanquesIntermedios.entrySet()) {
            disponibles.put(entry.getKey(), calcularDisponibilidadReal(entry.getKey()));
        }

        return disponibles;
    }

    /**
     * Obtiene los tanques con mayor tiempo hasta agotamiento (RF100)
     * @return Lista de tanques ordenados por tiempo de agotamiento (descendente)
     */
    public List<Coordenada> getTanquesPorTiempoAgotamiento() {
        List<Map.Entry<Coordenada, LocalDateTime>> lista = new ArrayList<>(tiemposAgotamiento.entrySet());

        // Ordenar por tiempo de agotamiento (descendente)
        Collections.sort(lista, (a, b) -> b.getValue().compareTo(a.getValue()));

        // Extraer solo las ubicaciones
        List<Coordenada> resultado = new ArrayList<>();
        for (Map.Entry<Coordenada, LocalDateTime> entry : lista) {
            resultado.add(entry.getKey());
        }

        return resultado;
    }

    /**
     * Clase interna para gestionar reservas de combustible
     */
    private static class ReservaCombustible {
        private String codigoCamion;
        private double cantidad;
        private LocalDateTime tiempoEstimado;
        private boolean completada;

        public ReservaCombustible(String codigoCamion, double cantidad, LocalDateTime tiempoEstimado) {
            this.codigoCamion = codigoCamion;
            this.cantidad = cantidad;
            this.tiempoEstimado = tiempoEstimado;
            this.completada = false;
        }

        public String getCodigoCamion() {
            return codigoCamion;
        }

        public double getCantidad() {
            return cantidad;
        }

        public LocalDateTime getTiempoEstimado() {
            return tiempoEstimado;
        }

        public boolean estaActiva() {
            return !completada && tiempoEstimado.isAfter(LocalDateTime.now().minusHours(2));
        }

        public void completar() {
            this.completada = true;
        }
    }
}