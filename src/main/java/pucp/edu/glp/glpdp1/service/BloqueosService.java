package pucp.edu.glp.glpdp1.service;

import pucp.edu.glp.glpdp1.domain.Bloqueo;
import pucp.edu.glp.glpdp1.domain.Ubicacion;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.Buffer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class BloqueosService {
    public void cargarBloqueosFromFile(String filePath){
        try(BufferedReader reader = new BufferedReader(new FileReader(filePath))){
            String line;
            while((line = reader.readLine())!= null){
                String[] parts = line.split(":");
                List<Ubicacion> tramos = new ArrayList<>();
                String[] coordenadas = parts[0].split(",");
                for(int i=0;i<coordenadas.length;i+=2){
                    int x = Integer.parseInt(coordenadas[i].trim());
                    int y = Integer.parseInt(coordenadas[i+1].trim());
                    Ubicacion ubi = new Ubicacion(x, y);
                    // Agregar la ubicación a la lista de tramos
                    tramos.add(ubi);
                }
                String[] fechas = parts[1].split(",");
                LocalDateTime fechaInicio = LocalDateTime.parse(fechas[0].trim());
                LocalDateTime fechaFinal = LocalDateTime.parse(fechas[1].trim());
                // Crear un nuevo objeto Bloqueo y agregarlo a la lista de bloqueos
                Bloqueo bloqueo = new Bloqueo(fechaInicio, fechaFinal, tramos);

            }
        }catch(IOException | NumberFormatException e){
            throw new RuntimeException("Error al cargar los bloqueos"+e.getMessage(),e);
        }
    }
}
