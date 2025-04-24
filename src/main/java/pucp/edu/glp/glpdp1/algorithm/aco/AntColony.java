package pucp.edu.glp.glpdp1.algorithm.aco;

import lombok.Getter;
import lombok.Setter;
import pucp.edu.glp.glpdp1.algorithm.model.GrafoRutas;

import java.util.ArrayList;
import java.util.List;

/**
 * Representa una colonia de hormigas en el algoritmo ACO.
 * Administra la población de hormigas que construyen soluciones.
 */
@Getter
@Setter
public class AntColony {

    private List<Ant> hormigas;
    private ACOParameters parameters;
    private GrafoRutas grafo;

    /**
     * Constructor
     * @param numHormigas Número de hormigas en la colonia
     * @param parameters Parámetros del algoritmo
     * @param grafo Grafo que representa la ciudad
     */
    public AntColony(int numHormigas, ACOParameters parameters, GrafoRutas grafo) {
        this.parameters = parameters;
        this.grafo = grafo;
        inicializarHormigas(numHormigas);
    }

    /**
     * Inicializa la población de hormigas
     * @param numHormigas Número de hormigas a crear
     */
    private void inicializarHormigas(int numHormigas) {
        this.hormigas = new ArrayList<>(numHormigas);
        for (int i = 0; i < numHormigas; i++) {
            Ant hormiga = new Ant(i, parameters);
            this.hormigas.add(hormiga);
        }
    }

    /**
     * Reinicia el estado de todas las hormigas para una nueva iteración
     */
    public void reiniciarHormigas() {
        for (Ant hormiga : hormigas) {
            // Devolver cada hormiga a su posición inicial
            // Para este problema, no es necesario definir una posición inicial fija
            // ya que cada hormiga construye su solución desde cero en cada iteración
        }
    }

    /**
     * Obtiene la mejor solución construida por las hormigas en la iteración actual
     * @param soluciones Lista de soluciones generadas
     * @return La mejor solución según su calidad
     */
    public ACOSolution obtenerMejorSolucion(List<ACOSolution> soluciones) {
        if (soluciones.isEmpty()) {
            return null;
        }

        ACOSolution mejor = soluciones.get(0);

        for (ACOSolution solucion : soluciones) {
            if (solucion.getCalidad() > mejor.getCalidad()) {
                mejor = solucion;
            }
        }

        return mejor;
    }
}