package pucp.edu.glp.glpdp1.algorithms;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import pucp.edu.glp.glpdp1.domain.Mapa;
import pucp.edu.glp.glpdp1.domain.Pedido;
import pucp.edu.glp.glpdp1.domain.Rutas;
import pucp.edu.glp.glpdp1.domain.Ubicacion;

/**
 * Clase que implementa el algoritmo genético para asignación de pedidos a camiones
 * minimizando la distancia total recorrida.
 * Se trabaja sobre una lista de pedidos y flota de camiones almacenada en el Mapa.
 */
public class GA {
    private final Mapa mapa;                        /** Información del entorno (pedidos, camiones, almacenes) */
    private final int populationSize;               /** Tamaño de la población por generación */
    private final int maxGenerations;               /** Máximo número de generaciones */
    private final double crossoverRate;             /** Probabilidad de cruce entre padres */
    private final double mutationRate;              /** Probabilidad de mutación de un hijo */
    private final double elitismRate;               /** Porcentaje de individuos que pasan directo a la siguiente generación */
    private final Random random;                    /** Para generar números aleatorios */
    private List<Individual> population;

    /** Constructor */
    public GA(Mapa mapa,
              int populationSize,
              int maxGenerations,
              double crossoverRate,
              double mutationRate,
              double elitismRate) {
        this.mapa = mapa;
        this.populationSize = populationSize;
        this.maxGenerations = maxGenerations;
        this.crossoverRate = crossoverRate;
        this.mutationRate = mutationRate;
        this.elitismRate = elitismRate;
        this.random = new Random();
    }
    /**
     * Ejecuta el algoritmo genético completo: inicializa, evoluciona y retorna el mejor individuo.
     */
    public Individual run() {
        initializePopulation();                                 // Crea y evalúa la población inicial
        Individual best = getBestIndividual(population);
        int stagnationCount = 0;

        for (int gen = 0; gen < maxGenerations; gen++) {
            // Aplica elitismo, selección, cruce, mutación y evaluación
            List<Individual> next = new ArrayList<>();
            int eliteCount = (int)(elitismRate * populationSize);

            // Ordenar por fitness y preservar la élite
            Collections.sort(population, Comparator.comparingDouble(i -> i.fitness));
            for (int i = 0; i < eliteCount; i++) {
                next.add(population.get(i).copy());
            }

            // Generar el resto de la población
            while (next.size() < populationSize) {
                Individual p1 = tournamentSelection();                  // Selección por torneo
                Individual p2 = tournamentSelection();
                List<Individual> offspring;

                if (random.nextDouble() < crossoverRate) {
                offspring = orderedCrossover(p1, p2);                   // Cruce
                } else {
                    offspring = Arrays.asList(p1.copy(), p2.copy());    // Copia directa si no hay cruce
                }

                // Mutación y evaluación
                for (Individual c : offspring) {
                    c.evaluate();                                   // Evaluación obligatoria
                    if (random.nextDouble() < mutationRate) {
                        c.mutate();     // Mutación
                        c.evaluate();   // Reevaluación tras mutación
                    }
                    next.add(c);
                    if (next.size() >= populationSize) break;
                }
            }

            // Verificar si hay mejora
            population = next;
            Individual genBest = getBestIndividual(population);
            if (genBest.fitness < best.fitness) {
                best = genBest;
                stagnationCount = 0;
            } else {
                stagnationCount++;
            }
            // Si no mejora tras 50 generaciones, se detiene
            if (stagnationCount >= 50) break;
        }

        return best;
    }
    /**
     * Crea la población inicial con permutaciones aleatorias de pedidos y los evalúa.
     */
    private void initializePopulation() {
        population = new ArrayList<>();
        int n = mapa.getPedidos().size();
        for (int i = 0; i < populationSize; i++) {
            Individual ind = new Individual(n);
            ind.evaluate();             // Calcula fitness de entrada
            population.add(ind);
        }
    }
    // Selecciona al mejor de 3 individuos aleatorios (menor fitness)
    private Individual tournamentSelection() {
        Individual best = null;
        for (int i = 0; i < 3; i++) {
            Individual cand = population.get(random.nextInt(populationSize));
            if (best == null || cand.fitness < best.fitness) {
                best = cand;
            }
        }
        return best.copy();     // Se devuelve una copia para evitar modificar el original
    }

    private List<Individual> orderedCrossover(Individual p1, Individual p2) {
        // Combina genes de dos padres sin repetir pedidos
        int n = p1.genes.length;
        Individual c1 = p1.copy();
        Individual c2 = p2.copy();
        int i1 = random.nextInt(n);
        int i2 = random.nextInt(n);
        int start = Math.min(i1, i2);
        int end = Math.max(i1, i2);

        List<Integer> seg1 = new ArrayList<>();
        List<Integer> seg2 = new ArrayList<>();

        for (int i = start; i <= end; i++) {
            c1.genes[i] = p2.genes[i]; seg1.add(c1.genes[i]);
            c2.genes[i] = p1.genes[i]; seg2.add(c2.genes[i]);
        }

        int pos1 = (end + 1) % n;
        int pos2 = pos1;

        for (int k = 0; k < n; k++) {
            int g1 = p1.genes[(end + 1 + k) % n];
            if (!seg1.contains(g1)) {
                c1.genes[pos1] = g1;
                pos1 = (pos1 + 1) % n;
            }
            int g2 = p2.genes[(end + 1 + k) % n];
            if (!seg2.contains(g2)) {
                c2.genes[pos2] = g2;
                pos2 = (pos2 + 1) % n;
            }
        }

        return Arrays.asList(c1, c2);
    }

    private Individual getBestIndividual(List<Individual> pop) {
        // Retorna el individuo con menor fitness (menor distancia)
        Individual best = pop.get(0);
        for (Individual ind : pop) {
            if (ind.fitness < best.fitness) best = ind;
        }
        return best;
    }

    public void printSolution(Individual ind) {
        if (ind.getRutas() == null) {
            ind.evaluate();
        }

        Ubicacion origen = mapa.getAlmacenes().get(0).getUbicacion();

        for (Rutas ruta : ind.getRutas()) {
            System.out.println("Camión " + ruta.getCamion().getIdC()
                    + " -> Distancia: " + ruta.getDistanciaTotal()
                    + ", Paradas: " + ruta.getUbicaciones().size());

            Ubicacion anterior = origen;

            for (Ubicacion u : ruta.getUbicaciones()) {
                LocalDateTime tiempo = mapa.getFechaInicio(); // o ajusta si tienes un tiempo real por camión
                List<Ubicacion> pasos = trazarRuta(anterior, u, tiempo);


                for (Ubicacion paso : pasos) {
                    System.out.println("      → Paso por: (" + paso.getX() + "," + paso.getY() + ")");
                }

                System.out.println("   📦 Entrega en: (" + u.getX() + "," + u.getY() + ")");
                anterior = u;
            }
        }
    }


    private List<Ubicacion> trazarRuta(Ubicacion origen, Ubicacion destino, LocalDateTime tiempoInicio) {
        List<Ubicacion> pasos = new ArrayList<>();
        int x = origen.getX();
        int y = origen.getY();

        // Horizontal (Eje X)
        while (x != destino.getX()) {
            x += (destino.getX() > x) ? 1 : -1;
            Ubicacion paso = new Ubicacion(x, y);
            if (mapa.estaBloqueado(paso, tiempoInicio)) return null;
            pasos.add(paso);
        }

        // Vertical (Eje Y)
        while (y != destino.getY()) {
            y += (destino.getY() > y) ? 1 : -1;
            Ubicacion paso = new Ubicacion(x, y);
            if (mapa.estaBloqueado(paso, tiempoInicio)) return null;
            pasos.add(paso);
        }

        return pasos;
    }



    // Un Individual representa una solución posible: una forma de distribuir los pedidos entre los camiones.
    public class Individual {
        private int[] genes;                            // Permutación de pedidos
        private double fitness;                         // Distancia total de todas las rutas
        private List<Rutas> rutas;                      // Lista de rutas generadas para la solución
        private final Random random = new Random();

        public Individual(int n) {
            // Crea un individuo con pedidos en orden aleatorio
            genes = new int[n];
            for (int i = 0; i < n; i++) genes[i] = i;
            shuffleGenes();
        }

        private Individual(Individual o) {
            this.genes = o.genes.clone();
        }

        public Individual copy() {
            return new Individual(this);
        }

        public void shuffleGenes() {
            for (int i = genes.length - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int tmp = genes[i]; genes[i] = genes[j]; genes[j] = tmp;
            }
        }

        public void mutate() {
            // Intercambia dos pedidos para alterar ligeramente la solución
            int i = random.nextInt(genes.length);
            int j = random.nextInt(genes.length);
            int tmp = genes[i]; genes[i] = genes[j]; genes[j] = tmp;
        }

        public void evaluate() {
            // Intenta asignar los pedidos respetando restricciones de volumen y fecha
            // Si no se puede asignar un pedido, penaliza con fitness infinito
            Mapa mapa = GA.this.mapa;
            int tcount = mapa.getFlota().size();
            rutas = new ArrayList<>();
            List<List<Pedido>> loads = new ArrayList<>();

            for (int t = 0; t < tcount; t++) {
                Rutas r = new Rutas();
                r.setCamion(mapa.getFlota().get(t));
                r.setUbicaciones(new ArrayList<>());
                r.setDistanciaTotal(0);
                r.setTiempoTotal(0);
                rutas.add(r);
                loads.add(new ArrayList<>());
            }

            fitness = 0;
            LocalDateTime start = mapa.getFechaInicio();

            for (int g : genes) {
                Pedido p = mapa.getPedidos().get(g);
                boolean placed = false;
                for (int t = 0; t < tcount && !placed; t++) {
                    Rutas r = rutas.get(t);
                    List<Pedido> ld = loads.get(t);
                    if (canPlace(p, r, ld, start)) {
                        placeOrder(p, r, ld, start);
                        placed = true;
                    }
                }
                if (!placed) {
                    fitness = Double.MAX_VALUE;
                    return;
                }
            }

            for (Rutas r : rutas) {
                fitness += r.getDistanciaTotal();
            }
        }

        private boolean canPlace(Pedido p, Rutas r, List<Pedido> ld, LocalDateTime start) {
            // Verifica capacidad
            double vol = ld.stream().mapToDouble(Pedido::getVolumen).sum();
            if (vol + p.getVolumen() > r.getCamion().getCargaM3()) return false;

            // Origen de ruta
            Ubicacion last = r.getUbicaciones().isEmpty()
                    ? mapa.getAlmacenes().get(0).getUbicacion()
                    : r.getUbicaciones().get(r.getUbicaciones().size() - 1);

            // Calcular llegada estimada
            double dist = Math.abs(last.getX() - p.getDestino().getX()) +
                    Math.abs(last.getY() - p.getDestino().getY());
            double hrs = dist / 50.0 + 0.25;
            LocalDateTime llegada = start.plusSeconds((long)(hrs * 3600));

            if (llegada.isAfter(p.getFechaLimite())) return false;

            // Verifica si algún nodo del camino está bloqueado
            List<Ubicacion> pasos = GA.this.trazarRuta(last, p.getDestino(), llegada.minusSeconds((long)(hrs * 1800)));
            return pasos != null;
        }


        private void placeOrder(Pedido p, Rutas r, List<Pedido> ld, LocalDateTime start) {
            Ubicacion last;
            if (r.getUbicaciones().isEmpty()) {
                last = GA.this.mapa.getAlmacenes().get(0).getUbicacion();
            } else {
                last = r.getUbicaciones().get(r.getUbicaciones().size() - 1);
            }

            double dist = Math.abs(last.getX() - p.getDestino().getX())
                    + Math.abs(last.getY() - p.getDestino().getY());
            r.getUbicaciones().add(p.getDestino());
            r.setDistanciaTotal(r.getDistanciaTotal() + dist);
            r.setTiempoTotal(r.getTiempoTotal() + dist / 50.0 + 0.25);
            ld.add(p);

        }

        public double getDistance() {
            return fitness;
        }

        public List<Rutas> getRutas() {
            return rutas;
        }
    }
}