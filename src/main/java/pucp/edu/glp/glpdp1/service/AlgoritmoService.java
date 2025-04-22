package pucp.edu.glp.glpdp1.service;

import org.springframework.stereotype.Service;
import pucp.edu.glp.glpdp1.algorithm.aco.ACOAlgorithm;
import pucp.edu.glp.glpdp1.algorithm.aco.ACOParameters;
import pucp.edu.glp.glpdp1.domain.Mapa;
import pucp.edu.glp.glpdp1.domain.Rutas;

import java.util.List;
import java.util.logging.Logger;

@Service
public class AlgoritmoService {

    private static final Logger logger = Logger.getLogger(AlgoritmoService.class.getName());

    /**
     * Genera rutas optimizadas utilizando el algoritmo ACO
     * @param mapa Mapa con los datos de la ciudad, flota, pedidos, etc.
     * @param params Parámetros del algoritmo
     * @return Lista de rutas optimizadas
     */
    public List<Rutas> generarRutasOptimizadas(Mapa mapa, ACOParameters params) {
        logger.info("Iniciando generación de rutas optimizadas con ACO");

        // Crear instancia del algoritmo con los parámetros dados
        ACOAlgorithm algoritmo = new ACOAlgorithm(mapa, params);

        // Ejecutar algoritmo
        List<Rutas> rutas = algoritmo.ejecutar();

        logger.info("Generación de rutas completada. Total rutas: " + rutas.size());

        return rutas;
    }
}
