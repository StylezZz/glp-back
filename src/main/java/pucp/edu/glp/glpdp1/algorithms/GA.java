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

public class GA {
    private final Mapa mapa;
    private final int populationSize;
    private final int maxGenerations;
    private final double crossoverRate;
    private final double mutationRate;
    private final double elitismRate;
    private final Random random;
    private List<Individual> population;

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

    public Individual run() {
        initializePopulation();
        Individual best = getBestIndividual(population);
        int stagnationCount = 0;

        for (int gen = 0; gen < maxGenerations; gen++) {
            List<Individual> next = new ArrayList<>();
            int eliteCount = (int)(elitismRate * populationSize);
            Collections.sort(population, Comparator.comparingDouble(i -> i.fitness));

            // preserve elite
            for (int i = 0; i < eliteCount; i++) {
                next.add(population.get(i).copy());
            }

            // generate offspring
            while (next.size() < populationSize) {
                Individual p1 = tournamentSelection();
                Individual p2 = tournamentSelection();
                List<Individual> offspring;

                if (random.nextDouble() < crossoverRate) {
                    offspring = orderedCrossover(p1, p2);
                } else {
                    offspring = Arrays.asList(p1.copy(), p2.copy());
                }

                for (Individual c : offspring) {
                    c.evaluate();
                    if (random.nextDouble() < mutationRate) {
                        c.mutate();
                        c.evaluate();
                    }
                    next.add(c);
                    if (next.size() >= populationSize) break;
                }
            }

            population = next;
            Individual genBest = getBestIndividual(population);
            if (genBest.fitness < best.fitness) {
                best = genBest;
                stagnationCount = 0;
            } else {
                stagnationCount++;
            }
            if (stagnationCount >= 50) break;
        }

        return best;
    }

    private void initializePopulation() {
        population = new ArrayList<>();
        int n = mapa.getPedidos().size();
        for (int i = 0; i < populationSize; i++) {
            Individual ind = new Individual(n);
            ind.evaluate();
            population.add(ind);
        }
    }

    private Individual tournamentSelection() {
        Individual best = null;
        for (int i = 0; i < 3; i++) {
            Individual cand = population.get(random.nextInt(populationSize));
            if (best == null || cand.fitness < best.fitness) {
                best = cand;
            }
        }
        return best.copy();
    }

    private List<Individual> orderedCrossover(Individual p1, Individual p2) {
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
        Individual best = pop.get(0);
        for (Individual ind : pop) {
            if (ind.fitness < best.fitness) best = ind;
        }
        return best;
    }

    /** Imprime en consola la ruta sin NPE */
    public void printSolution(Individual ind) {
        if (ind.getRutas() == null) {
            ind.evaluate();
        }
        for (Rutas ruta : ind.getRutas()) {
            System.out.println("Camión " + ruta.getCamion().getIdC()
                    + " -> Distancia: " + ruta.getDistanciaTotal()
                    + ", Paradas: " + ruta.getUbicaciones().size());
        }
    }

    public class Individual {
        private int[] genes;
        private double fitness;
        private List<Rutas> rutas;
        private final Random random = new Random();

        public Individual(int n) {
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
            int i = random.nextInt(genes.length);
            int j = random.nextInt(genes.length);
            int tmp = genes[i]; genes[i] = genes[j]; genes[j] = tmp;
        }

        public void evaluate() {
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
            double vol = 0;
            for (Pedido x : ld) vol += x.getVolumen();
            if (vol + p.getVolumen() > r.getCamion().getCargaM3()) {
                return false;
            }

            Ubicacion last;
            if (r.getUbicaciones().isEmpty()) {
                last = GA.this.mapa.getAlmacenes().get(0).getUbicacion();
            } else {
                last = r.getUbicaciones().get(r.getUbicaciones().size() - 1);
            }

            double dist = Math.abs(last.getX() - p.getDestino().getX())
                    + Math.abs(last.getY() - p.getDestino().getY());
            double hrs = dist / 50.0 + 0.25;
            LocalDateTime arr = start.plusSeconds((long)(hrs * 3600));
            return !arr.isAfter(p.getFechaLimite());
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