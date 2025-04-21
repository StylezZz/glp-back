package pucp.edu.glp.glpdp1.service;

import org.springframework.stereotype.Service;
import pucp.edu.glp.glpdp1.domain.Pedido;
import pucp.edu.glp.glpdp1.domain.Averia;
import pucp.edu.glp.glpdp1.domain.Ubicacion;
import pucp.edu.glp.glpdp1.domain.enums.Turnos;
import pucp.edu.glp.glpdp1.domain.enums.Incidente;


import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AveriaService {

    public List<Averia> cargarAveriasDesdeBytes(byte[] datos) throws IOException {
        List<Averia> averias = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(datos)))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                linea = linea.trim();
                if (linea.isEmpty()) continue;

                String[] campos = linea.split("_");
                if (campos.length != 3) {
                    System.err.println("Formato inválido en línea: " + linea);
                    continue;
                }

                Averia a = new Averia();

                // Parseo de enum, ojo: los nombres en el fichero deben corresponder exactamente
                a.setTurno(Turnos.valueOf(campos[0]));
                a.setCodigo(campos[1]);
                a.setIncidente(Incidente.valueOf(campos[2]));
                // Si no tienes fecha en el fichero, puedes asignar la hora de carga:
                a.setFechaIncidente(LocalDateTime.now());

                averias.add(a);
            }
        }

        return averias;
    }


}
