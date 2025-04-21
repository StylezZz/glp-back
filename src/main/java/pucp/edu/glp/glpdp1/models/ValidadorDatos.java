package pucp.edu.glp.glpdp1.aco;

import pucp.edu.glp.glpdp1.models.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Clase para validar los datos de entrada y detectar inconsistencias (RF97)
 */
public class ValidadorDatos {
    private List<String> inconsistencias;
    private Mapa mapa;

    public ValidadorDatos(Mapa mapa) {
        this.inconsistencias = new ArrayList<>();
        this.mapa = mapa;
    }

    /**
     * Valida todos los datos del sistema
     * @param flota Flota de camiones
     * @param pedidos Lista de pedidos
     * @param bloqueos Lista de bloqueos
     * @param mantenimientos Lista de mantenimientos
     * @return true si los datos son válidos, false en caso contrario
     */
    public boolean validarDatos(Flota flota, List<Pedido> pedidos,
                                List<Bloqueo> bloqueos, List<MantenimientoPreventivo> mantenimientos) {

        boolean datosValidos = true;

        datosValidos &= validarPedidos(pedidos);
        datosValidos &= validarCamiones(flota);
        datosValidos &= validarBloqueos(bloqueos);
        datosValidos &= validarMantenimientos(mantenimientos);

        // Si hay inconsistencias, mostrarlas
        if (!inconsistencias.isEmpty()) {
            System.out.println("WARNING: Se encontraron " + inconsistencias.size() + " inconsistencias en los datos:");
            for (String inconsistencia : inconsistencias) {
                System.out.println("  - " + inconsistencia);
            }
        }

        return datosValidos;
    }

    /**
     * Valida los pedidos
     * @param pedidos Lista de pedidos
     * @return true si los pedidos son válidos, false en caso contrario
     */
    private boolean validarPedidos(List<Pedido> pedidos) {
        boolean validos = true;

        for (Pedido pedido : pedidos) {
            // Validar ID de cliente
            if (pedido.getIdCliente() == null || pedido.getIdCliente().isEmpty()) {
                registrarInconsistencia("Pedido sin ID de cliente");
                validos = false;
            }

            // Validar cantidad
            if (pedido.getCantidad() <= 0) {
                registrarInconsistencia("Pedido " + pedido.getIdCliente() +
                        " con cantidad inválida: " + pedido.getCantidad());
                validos = false;
            }

            // Validar ubicación
            if (pedido.getUbicacion() == null) {
                registrarInconsistencia("Pedido " + pedido.getIdCliente() + " sin ubicación");
                validos = false;
            } else if (!esUbicacionValida(pedido.getUbicacion())) {
                registrarInconsistencia("Pedido " + pedido.getIdCliente() +
                        " con ubicación fuera de los límites del mapa: " + pedido.getUbicacion());
                validos = false;
            }

            // Validar fecha de pedido
            if (pedido.getFechaPedido() == null) {
                registrarInconsistencia("Pedido " + pedido.getIdCliente() + " sin fecha");
                validos = false;
            } else if (pedido.getFechaPedido().isAfter(LocalDateTime.now())) {
                registrarInconsistencia("Pedido " + pedido.getIdCliente() +
                        " con fecha futura: " + pedido.getFechaPedido());
                validos = false;
            }

            // Validar tiempo límite
            if (pedido.getTiempoLimite() == null) {
                registrarInconsistencia("Pedido " + pedido.getIdCliente() + " sin tiempo límite");
                validos = false;
            } else if (pedido.getTiempoLimite().isNegative() || pedido.getTiempoLimite().isZero()) {
                registrarInconsistencia("Pedido " + pedido.getIdCliente() +
                        " con tiempo límite inválido: " + pedido.getTiempoLimite());
                validos = false;
            } else if (pedido.getFechaPedido() != null &&
                    pedido.getFechaPedido().plus(pedido.getTiempoLimite()).isBefore(LocalDateTime.now())) {
                registrarInconsistencia("Pedido " + pedido.getIdCliente() +
                        " con tiempo límite ya vencido");
                // No marcamos como inválido, pero advertimos
            }
        }

        return validos;
    }

    /**
     * Valida los camiones
     * @param flota Flota de camiones
     * @return true si los camiones son válidos, false en caso contrario
     */
    private boolean validarCamiones(Flota flota) {
        boolean validos = true;
        List<Camion> camiones = new ArrayList<>();

        // Obtener todos los camiones (disponibles y no disponibles)
        camiones.addAll(flota.obtenerDisponibles());
        // Aquí añadiríamos otros camiones no disponibles si hubiera un método para ello

        // Verificar que hay camiones
        if (camiones.isEmpty()) {
            registrarInconsistencia("No hay camiones disponibles en la flota");
            return false;
        }

        // Validar cada camión
        for (Camion camion : camiones) {
            // Validar código
            if (camion.getCodigo() == null || camion.getCodigo().isEmpty()) {
                registrarInconsistencia("Camión sin código");
                validos = false;
            }

            // Validar tipo
            if (camion.getTipo() == null) {
                registrarInconsistencia("Camión " + camion.getCodigo() + " sin tipo definido");
                validos = false;
            }

            // Validar capacidad de tanque
            if (camion.getCapacidadTanque() <= 0) {
                registrarInconsistencia("Camión " + camion.getCodigo() +
                        " con capacidad de tanque inválida: " + camion.getCapacidadTanque());
                validos = false;
            }

            // Validar combustible actual
            if (camion.getCombustibleActual() < 0) {
                registrarInconsistencia("Camión " + camion.getCodigo() +
                        " con combustible actual negativo: " + camion.getCombustibleActual());
                validos = false;
            } else if (camion.getCombustibleActual() > camion.getCapacidadTanque()) {
                registrarInconsistencia("Camión " + camion.getCodigo() +
                        " con combustible actual (" + camion.getCombustibleActual() +
                        ") mayor que capacidad (" + camion.getCapacidadTanque() + ")");
                validos = false;
            } else if (camion.getCombustibleActual() < camion.getCapacidadTanque() * 0.1) {
                registrarInconsistencia("Camión " + camion.getCodigo() +
                        " con nivel de combustible muy bajo: " +
                        String.format("%.2f%%", 100.0 * camion.getCombustibleActual() / camion.getCapacidadTanque()));
                // No marcamos como inválido, pero advertimos
            }

            // Validar carga actual
            if (camion.getCargaActual() < 0) {
                registrarInconsistencia("Camión " + camion.getCodigo() +
                        " con carga actual negativa: " + camion.getCargaActual());
                validos = false;
            } else if (camion.getTipo() != null && camion.getCargaActual() > camion.getTipo().getCapacidad()) {
                registrarInconsistencia("Camión " + camion.getCodigo() +
                        " con carga actual (" + camion.getCargaActual() +
                        ") mayor que capacidad (" + camion.getTipo().getCapacidad() + ")");
                validos = false;
            }

            // Validar posición actual
            if (camion.getPosicionActual() == null) {
                registrarInconsistencia("Camión " + camion.getCodigo() + " sin posición actual");
                validos = false;
            } else if (!esUbicacionValida(camion.getPosicionActual())) {
                registrarInconsistencia("Camión " + camion.getCodigo() +
                        " con posición fuera de los límites del mapa: " + camion.getPosicionActual());
                validos = false;
            }
        }

        return validos;
    }

    /**
     * Valida los bloqueos
     * @param bloqueos Lista de bloqueos
     * @return true si los bloqueos son válidos, false en caso contrario
     */
    private boolean validarBloqueos(List<Bloqueo> bloqueos) {
        boolean validos = true;

        for (Bloqueo bloqueo : bloqueos) {
            // Validar fechas de inicio y fin
            if (bloqueo.getInicio() == null) {
                registrarInconsistencia("Bloqueo sin fecha de inicio");
                validos = false;
            }

            if (bloqueo.getFin() == null) {
                registrarInconsistencia("Bloqueo sin fecha de fin");
                validos = false;
            }

            if (bloqueo.getInicio() != null && bloqueo.getFin() != null &&
                    bloqueo.getInicio().isAfter(bloqueo.getFin())) {
                registrarInconsistencia("Bloqueo con fecha de inicio (" + bloqueo.getInicio() +
                        ") posterior a fecha de fin (" + bloqueo.getFin() + ")");
                validos = false;
            }

            // Validar nodos del polígono
            List<Coordenada> nodos = bloqueo.getNodosPoligono();
            if (nodos == null || nodos.isEmpty()) {
                registrarInconsistencia("Bloqueo sin nodos definidos");
                validos = false;
            } else if (nodos.size() < 3) {
                registrarInconsistencia("Bloqueo con menos de 3 nodos, no forma un polígono válido");
                validos = false;
            } else {
                // Verificar que los nodos están dentro de los límites del mapa
                for (Coordenada nodo : nodos) {
                    if (!esUbicacionValida(nodo)) {
                        registrarInconsistencia("Bloqueo con nodo fuera de los límites del mapa: " + nodo);
                        validos = false;
                        break;
                    }
                }
            }
        }

        return validos;
    }

    /**
     * Valida los mantenimientos preventivos
     * @param mantenimientos Lista de mantenimientos
     * @return true si los mantenimientos son válidos, false en caso contrario
     */
    private boolean validarMantenimientos(List<MantenimientoPreventivo> mantenimientos) {
        boolean validos = true;

        for (MantenimientoPreventivo mantenimiento : mantenimientos) {
            // Validar código de camión
            if (mantenimiento.getCodigoCamion() == null || mantenimiento.getCodigoCamion().isEmpty()) {
                registrarInconsistencia("Mantenimiento sin código de camión");
                validos = false;
            }

            // Validar fecha
            if (mantenimiento.getFecha() == null) {
                registrarInconsistencia("Mantenimiento para camión " + mantenimiento.getCodigoCamion() + " sin fecha");
                validos = false;
            } else if (mantenimiento.getFecha().isBefore(LocalDateTime.now().toLocalDate())) {
                registrarInconsistencia("Mantenimiento para camión " + mantenimiento.getCodigoCamion() +
                        " con fecha pasada: " + mantenimiento.getFecha());
                // No marcamos como inválido, pero advertimos
            }
        }

        return validos;
    }

    /**
     * Verifica si una ubicación está dentro de los límites del mapa
     * @param ubicacion Coordenada a verificar
     * @return true si está dentro de los límites, false en caso contrario
     */
    private boolean esUbicacionValida(Coordenada ubicacion) {
        return ubicacion != null &&
                ubicacion.getX() >= 0 && ubicacion.getX() <= mapa.getAncho() &&
                ubicacion.getY() >= 0 && ubicacion.getY() <= mapa.getAlto();
    }

    /**
     * Registra una inconsistencia detectada
     * @param mensaje Mensaje de inconsistencia
     */
    private void registrarInconsistencia(String mensaje) {
        inconsistencias.add(mensaje);
    }

    /**
     * Obtiene la lista de inconsistencias detectadas
     * @return Lista de mensajes de inconsistencia
     */
    public List<String> getInconsistencias() {
        return new ArrayList<>(inconsistencias);
    }
}