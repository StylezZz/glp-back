package pucp.edu.glp.glpdp1.algorithm.aco;

import lombok.Getter;
import lombok.Setter;

/**
 * Parámetros configurables para el algoritmo ACO.
 * Esta clase centraliza todos los parámetros ajustables para facilitar
 * la experimentación y optimización del algoritmo.
 */
@Getter
@Setter
public class ACOParameters {

    // Parámetros básicos del algoritmo ACO
    private int numeroHormigas = 10;
    private int numeroIteraciones = 50;
    private int maxIteracionesSinMejora = 15;
    private double umbralConvergenciaTemprana = 0.6;  // 60% del total de iteraciones
    private double factorEvaporacion = 0.7;
    private double alfa = 1.0;  // Importancia de las feromonas
    private double beta = 2.0;  // Importancia de la heurística
    private double q0 = 0.95;    // Parámetro de exploración vs explotación
    private double feromonaInicial = 0.1;

    // Parámetros específicos del problema de distribución
    private int tiempoDescargaCliente = 15;       // Minutos para descarga en cliente
    private int tiempoMantenimientoRutina = 15;   // Minutos para mantenimiento rutinario
    private double umbralColapso = 0.20;          // % de pedidos no entregables para considerar colapso
    private int frecuenciaReplanificacionBase = 60; // Minutos entre replanificaciones
    private int plazoMinimoEntrega = 4;           // Horas mínimas de plazo
    private double velocidadPromedio = 50.0;      // Km/h
    private double factorPriorizacionTanques = 1.2; // Factor para priorizar tanques intermedios
    private double factorPenalizacionRetraso = 1000.0; // Penalización por minuto de retraso
    private double factorPenalizacionBloqueo = 5000.0; // Penalización por bloqueo en ruta
    private double factorPriorizacionUrgencia = 2.0; // Factor para priorizar pedidos urgentes

    // Parámetros para simulación
    private int tiempoAvanceSimulacion = 15;      // Minutos que avanza la simulación en cada iteración

    // Umbral de combustible crítico para camiones
    private int umbralCombustibleCritico = 5;     // Galones

    // Capacidad mínima para considerar un tanque como viable para reabastecimiento
    private double capacidadMinimaReabastecimiento = 10.0; // m3

    // Umbral de distancia para considerar pedidos como cercanos (para agrupamiento)
    private double umbralDistanciaPedidosCercanos = 50.0; // km

    // Máximo de pedidos por grupo en agrupamiento inteligente
    private int maxPedidosPorGrupo = 5;

    /**
     * Constructor por defecto con valores predefinidos
     */
    public ACOParameters() {
        // Los valores por defecto se inicializan en las declaraciones
    }

    /**
     * Crea una nueva instancia con valores personalizados para experimentación
     * @param numeroHormigas Número de hormigas en la colonia
     * @param numeroIteraciones Número máximo de iteraciones
     * @param factorEvaporacion Factor de evaporación de feromonas (0-1)
     */
    public ACOParameters(int numeroHormigas, int numeroIteraciones, double factorEvaporacion) {
        this.numeroHormigas = numeroHormigas;
        this.numeroIteraciones = numeroIteraciones;
        this.factorEvaporacion = factorEvaporacion;
    }

    /**
     * Crea una configuración optimizada para respuesta rápida
     * @return Parámetros optimizados para velocidad
     */
    public static ACOParameters getConfiguracionRapida() {
        ACOParameters params = new ACOParameters();
        params.setNumeroHormigas(15);
        params.setNumeroIteraciones(100);
        params.setMaxIteracionesSinMejora(20);
        return params;
    }

    /**
     * Crea una configuración optimizada para calidad de solución
     * @return Parámetros optimizados para calidad
     */
    public static ACOParameters getConfiguracionCalidad() {
        ACOParameters params = new ACOParameters();
        params.setNumeroHormigas(50);
        params.setNumeroIteraciones(1000);
        params.setMaxIteracionesSinMejora(100);
        params.setQ0(0.8); // Más exploración
        return params;
    }

    /**
     * Crea una configuración equilibrada entre velocidad y calidad
     * @return Parámetros equilibrados
     */
    public static ACOParameters getConfiguracionEquilibrada() {
        ACOParameters params = new ACOParameters();
        // Valores por defecto
        return params;
    }

    /**
     * Aplica una configuración para detección de colapso más sensible
     */
    public void activarDeteccionColapsoSensible() {
        this.umbralColapso = 0.15;  // 15% en lugar de 20%
    }

    /**
     * Ajusta los parámetros para mayor priorización de pedidos urgentes
     */
    public void aumentarPriorizacionUrgencia() {
        this.factorPriorizacionUrgencia = 3.0;
        this.factorPenalizacionRetraso = 2000.0;
    }
}