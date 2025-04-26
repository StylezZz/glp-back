package pucp.edu.glp.glpdp1.service;

import org.springframework.cglib.core.Local;
import org.springframework.stereotype.Service;
import pucp.edu.glp.glpdp1.domain.Bloqueo;
import pucp.edu.glp.glpdp1.domain.Ubicacion;

import java.io.*;
import java.nio.Buffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BloqueosService {

    /**
     * Carga bloqueos desde un archivo de texto en la ruta especificada
     * @param rutaArchivo Ruta del archivo de bloqueos
     * @return Lista de pedidos cargados desde el archivo
     * **/
    public List<Bloqueo> cargarBloqueosDesdeArchivo(String rutaArchivo) throws IOException{
        // Usando API moderno de Java para leer archivos
        return Files.lines(Path.of(rutaArchivo))
                .map(this::parsearLineaBloqueo)
                .filter(bloqueo -> bloqueo != null)
                .toList();
    }

    /**
     * Carga pedidos desde bytes (útil cuando los datos vienen de una API)
     * @param datos Bytes que contienen los datos de pedidos
     * @return Lista de pedidos cargados desde los bytes
     * **/
    public List<Bloqueo> cargarBloqueosDesdeBytes(byte[] datos) throws IOException{
        List<Bloqueo> bloqueos = new ArrayList<>();
        try(BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(datos)))){
            String linea;
            while((linea = br.readLine())!= null){
                Bloqueo bloqueo = parsearLineaBloqueo(linea);
                if(bloqueo != null){
                    bloqueos.add(bloqueo);
                }
            }
        }
        return bloqueos;
    }

    /**
     * Parsea una línea del archivo de bloqueos y crea un objeto Bloqueo
     * @param linea Línea con formato: posX,posY,c-idCliente,##m3,##h
     * @return Objeto Bloqueo con los datos de la línea, o null si la línea no tiene el formato correcto
     * **/
    private Bloqueo parsearLineaBloqueo(String linea){
        String[] partes = linea.split(":");
        if(partes.length !=2){
            return null;
        }

        Pattern patternFechas = Pattern.compile("(\\d{2})d(\\d{2})h(\\d{2})m-(\\d{2})d(\\d{2})h(\\d{2})m");
        Matcher matcher = patternFechas.matcher(partes[0]);

        if(matcher.find()){
            int diaInicio = Integer.parseInt(matcher.group(1));
            int horaInicio = Integer.parseInt(matcher.group(2));
            int minutoInicio = Integer.parseInt(matcher.group(3));
            int diaFinal = Integer.parseInt(matcher.group(4));
            int horaFinal = Integer.parseInt(matcher.group(5));
            int minutoFinal = Integer.parseInt(matcher.group(6));

            LocalDateTime fechaInicio = LocalDateTime.of(2025, 4, diaInicio, horaInicio, minutoInicio);
            LocalDateTime fechaFinal = LocalDateTime.of(2025, 4, diaFinal, horaFinal, minutoFinal);

            String coordenadas = partes[1];
            String[] coordenadasArray = coordenadas.split(",");

            List<Ubicacion> tramosCompletos = new ArrayList<>();
            for(int i=0;i<coordenadasArray.length-2;i+=2){
                int x1 = Integer.parseInt(coordenadasArray[i].trim());
                int y1 = Integer.parseInt(coordenadasArray[i+1].trim());
                int x2 = Integer.parseInt(coordenadasArray[i+2].trim());
                int y2 = Integer.parseInt(coordenadasArray[i+3].trim());
                List<Ubicacion> puntosSegmento = generarPuntosIntermedios(x1, y1, x2, y2);
                // Agregar la ubicación a la lista de tramos
                tramosCompletos.addAll(puntosSegmento);
            }

            Set<Ubicacion> ubicacionesUnicas = new LinkedHashSet<>(tramosCompletos);
            List<Ubicacion> tramosFinales = new ArrayList<>(ubicacionesUnicas);
            return new Bloqueo(fechaInicio, fechaFinal, tramosFinales);
        }

        return null;
    }

    private List<Ubicacion> generarPuntosIntermedios(int x1,int y1,int x2,int y2){
        List<Ubicacion> puntos = new ArrayList<>();

        if (y1 == y2) {
            int inicio = Math.min(x1, x2);
            int fin = Math.max(x1, x2);
            for (int x = inicio; x <= fin; x++) {
                puntos.add(new Ubicacion(x, y1));
            }
        }
        // Si es una línea vertical
        else if (x1 == x2) {
            int inicio = Math.min(y1, y2);
            int fin = Math.max(y1, y2);
            for (int y = inicio; y <= fin; y++) {
                puntos.add(new Ubicacion(x1, y));
            }
        }

        return puntos;
    }

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
