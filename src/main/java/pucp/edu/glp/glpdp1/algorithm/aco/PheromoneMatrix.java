package pucp.edu.glp.glpdp1.algorithm.aco;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Random;

/**
 * Representa la matriz de feromonas del algoritmo ACO.
 * Gestiona la actualización, evaporación y perturbación de feromonas.
 */
@Getter
@Setter
public class PheromoneMatrix {

    private double[][] matriz;
    private int tamanio;
    private Random random;

    /**
     * Constructor
     * @param tamanio Tamaño de la matriz (número de nodos)
     * @param valorInicial Valor inicial de feromona
     */
    public PheromoneMatrix(int tamanio, double valorInicial) {
        this.tamanio = tamanio;
        this.matriz = new double[tamanio][tamanio];
        this.random = new Random();

        // Inicializar matriz con valor inicial
        for (int i = 0; i < tamanio; i++) {
            for (int j = 0; j < tamanio; j++) {
                matriz[i][j] = valorInicial;
            }
        }
    }

    /**
     * Obtiene el valor de feromona entre dos nodos
     * @param origen ID del nodo origen
     * @param destino ID del nodo destino
     * @return Valor de feromona
     */
    public double getValor(int origen, int destino) {
        if (origen >= 0 && origen < tamanio && destino >= 0 && destino < tamanio) {
            return matriz[origen][destino];
        }
        return 0.0;
    }

    /**
     * Establece el valor de feromona entre dos nodos
     * @param origen ID del nodo origen
     * @param destino ID del nodo destino
     * @param valor Nuevo valor de feromona
     */
    public void setValor(int origen, int destino, double valor) {
        if (origen >= 0 && origen < tamanio && destino >= 0 && destino < tamanio) {
            matriz[origen][destino] = valor;
        }
    }

    /**
     * Actualiza la matriz de feromonas basada en las soluciones generadas
     * @param soluciones Lista de soluciones
     * @param factorEvaporacion Factor de evaporación (0-1)
     */
    public void actualizarFeromonas(List<ACOSolution> soluciones, double factorEvaporacion) {
        // Evaporación global de feromonas
        for (int i = 0; i < tamanio; i++) {
            for (int j = 0; j < tamanio; j++) {
                matriz[i][j] *= (1 - factorEvaporacion);
            }
        }

        // Depósito de feromonas proporcional a la calidad de las soluciones
        for (ACOSolution solucion : soluciones) {
            double calidad = solucion.getCalidad();

            // Factor de depósito base proporcional a la calidad
            double factorDeposito = calidad * 10.0;

            // Depositar feromona en cada tramo de las rutas de la solución
            solucion.getAsignaciones().forEach(asignacion -> {
                // Obtener nodos anteriores y siguientes para cada ruta
                for (int i = 0; i < asignacion.getRutas().size(); i++) {
                    int origen = asignacion.getRutas().get(i).getOrigen().getX() * 1000 +
                            asignacion.getRutas().get(i).getOrigen().getY();
                    int destino = asignacion.getRutas().get(i).getDestino().getX() * 1000 +
                            asignacion.getRutas().get(i).getDestino().getY();

                    // Asegurar que los IDs están dentro del rango de la matriz
                    origen = Math.min(origen, tamanio - 1);
                    destino = Math.min(destino, tamanio - 1);

                    // Factor dependiente de la distancia (inversamente proporcional)
                    double distancia = asignacion.getRutas().get(i).getDistancia();
                    double factorDistancia = distancia > 0 ? 1.0 / distancia : 1.0;

                    // Incrementar feromona
                    double incremento = factorDeposito * factorDistancia;
                    matriz[origen][destino] += incremento;
                    matriz[destino][origen] += incremento; // Grafo no dirigido
                }
            });
        }
    }

    /**
     * Perturba la matriz de feromonas para escapar de óptimos locales
     * @param feromonaMinima Valor mínimo de feromona
     */
    public void perturbarFeromonas(double feromonaMinima) {
        double perturbacion = 0.2; // 20% de perturbación máxima

        for (int i = 0; i < tamanio; i++) {
            for (int j = 0; j < tamanio; j++) {
                // Añadir ruido aleatorio entre -perturbación y +perturbación
                double factorRuido = 1.0 + (random.nextDouble() * 2 * perturbacion - perturbacion);
                matriz[i][j] *= factorRuido;

                // Garantizar valor mínimo de feromona
                if (matriz[i][j] < feromonaMinima / 2) {
                    matriz[i][j] = feromonaMinima / 2;
                }
            }
        }

        // Reforzar aleatoriamente algunos caminos
        int numRefuerzos = tamanio / 10; // 10% de los nodos

        for (int k = 0; k < numRefuerzos; k++) {
            int i = random.nextInt(tamanio);
            int j = random.nextInt(tamanio);

            // Solo reforzar nodos diferentes
            if (i != j) {
                matriz[i][j] *= 3.0; // Triplicar feromona
                matriz[j][i] *= 3.0;
            }
        }
    }

    /**
     * Aplica un factor de evaporación entre planificaciones para mantener aprendizaje
     * pero evitar estancamiento en patrones obsoletos
     * @param factorEvaporacionEntrePlanificaciones Factor de evaporación (0-1)
     */
    public void aplicarEvaporacionEntrePlanificaciones(double factorEvaporacionEntrePlanificaciones) {
        // Limitar el factor entre 0 y 1
        double factor = Math.max(0.0, Math.min(1.0, factorEvaporacionEntrePlanificaciones));

        // Aplicar evaporación a toda la matriz
        for (int i = 0; i < tamanio; i++) {
            for (int j = 0; j < tamanio; j++) {
                matriz[i][j] *= (1.0 - factor);

                // Asegurar valor mínimo de feromona para mantener exploración
                if (matriz[i][j] < 0.1) {
                    matriz[i][j] = 0.1;
                }
            }
        }

        System.out.println("🔄 Feromonas persistidas con factor de evaporación: " +
                String.format("%.2f", factor));
    }
}