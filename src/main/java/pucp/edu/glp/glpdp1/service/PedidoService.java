package pucp.edu.glp.glpdp1.service;

import org.springframework.stereotype.Service;
import pucp.edu.glp.glpdp1.domain.Pedido;
import pucp.edu.glp.glpdp1.domain.Ubicacion;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PedidoService {

    /**
     * Carga pedidos desde un archivo de texto en la ruta especificada
     * @param rutaArchivo Ruta del archivo de pedidos
     * @return Lista de pedidos cargados desde el archivo
     */
    public List<Pedido> cargarPedidosDesdeArchivo(String rutaArchivo) throws IOException {
        // Usando API moderna de Java para leer archivos
        return Files.lines(Path.of(rutaArchivo))
                .map(this::parsearLineaPedido)
                .filter(pedido -> pedido != null)
                .toList();
    }

    /**
     * Carga pedidos desde bytes (útil cuando los datos vienen de una API)
     * @param datos Bytes que contienen los datos de pedidos
     * @return Lista de pedidos cargados desde los bytes
     */
    public List<Pedido> cargarPedidosDesdeBytes(byte[] datos) throws IOException {
        List<Pedido> pedidos = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(datos)))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                Pedido pedido = parsearLineaPedido(linea);
                if (pedido != null) {
                    pedidos.add(pedido);
                }
            }
        }
        return pedidos;
    }

    /**
     * Parsea una línea del archivo de pedidos y crea un objeto Pedido
     * @param linea Línea con formato: ##d##h##m:posX,posY,c-idCliente,##m3,##h
     * @return Objeto Pedido con los datos de la línea, o null si la línea no tiene el formato correcto
     */
    private Pedido parsearLineaPedido(String linea) {
        // Expresión regular para extraer los componentes
        Pattern pattern = Pattern.compile("(\\d+)d(\\d+)h(\\d+)m:(\\d+),(\\d+),c-(\\d+),(\\d+)m3,(\\d+)h");
        Matcher matcher = pattern.matcher(linea);

        if (matcher.find()) {
            int dia = Integer.parseInt(matcher.group(1));
            int hora = Integer.parseInt(matcher.group(2));
            int minuto = Integer.parseInt(matcher.group(3));
            int posX = Integer.parseInt(matcher.group(4));
            int posY = Integer.parseInt(matcher.group(5));
            String idClienteNum = matcher.group(6);
            double volumen = Double.parseDouble(matcher.group(7));
            int horasLimite = Integer.parseInt(matcher.group(8));

            // Crear el objeto Pedido
            Pedido pedido = new Pedido();

            // Crear la ubicación de destino
            Ubicacion ubicacion = new Ubicacion(posX, posY);
            pedido.setDestino(ubicacion);

            // Establecer la fecha de registro
            // Asumimos una fecha base y sumamos días, horas y minutos
            LocalDateTime fechaBase = LocalDateTime.of(2025, 1, 1, 0, 0);
            LocalDateTime fechaRegistro = fechaBase
                    .plusDays(dia)
                    .plusHours(hora)
                    .plusMinutes(minuto);
            pedido.setFechaRegistro(fechaRegistro);


            // Establecer otros campos
            pedido.setIdCliente("c-" + idClienteNum);
            pedido.setVolumen(volumen);
            pedido.setHorasLimite(horasLimite);

            // Calcular la fecha límite
            LocalDateTime fechaLimite = fechaRegistro.plusHours(horasLimite);
            pedido.setFechaLimite(fechaLimite);

            // Asignar ID - en un sistema real, esto podría venir de una base de datos
            // o generarse de manera más sofisticada
            pedido.setIdPedido(Math.abs(pedido.hashCode()));

            return pedido;
        }

        return null; // Si la línea no coincide con el formato esperado
    }
}