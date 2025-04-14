// --- Constantes y Parámetros del ACO ---
NUM_HORMIGAS = 50      // Número de hormigas (soluciones construidas por iteración)
NUM_ITERACIONES = 200  // Número de ciclos del algoritmo
ALFA = 1.0             // Importancia de la feromona
BETA = 2.0             // Importancia de la información heurística (visibilidad)
RHO = 0.1              // Tasa de evaporación de feromona (0 < RHO < 1)
Q = 100                // Factor de cantidad de feromona depositada
FEROMONA_INICIAL = 1.0 // Valor inicial de feromona en todas las aristas

// Penalizaciones por incumplimiento (deben ser significativamente altas)
PENALIZACION_CAPACIDAD = 100000
PENALIZACION_TIEMPO_LIMITE = 100000
PENALIZACION_BLOQUEO = 100000
PENALIZACION_CAMION_NO_DISPONIBLE = 100000
PENALIZACION_PEDIDO_NO_CUBIERTO = 500000 // Muy alta para forzar cobertura total

// --- Estructura de Datos ACO ---
// Grafo: Nodos = Almacenes (Central, Norte, Este) + Ubicaciones de Clientes con pedidos.
//        Aristas = Posibles tramos directos entre cualquier par de nodos (calculados con distancia Manhattan).
// Matriz de Feromonas: feromonas[nodo_origen][nodo_destino] = nivel de feromona.
// Matriz Heurística: heuristica[nodo_origen][nodo_destino] = valor heurístico (e.g., 1/distancia, urgencia).

// --- Funciones Principales del ACO ---

FUNCION Principal_ACO(pedidos_actuales, estado_flota_actual, bloqueos_actuales, tiempo_simulacion_actual):
// 1. Preparación
nodos_grafo = ObtenerNodosAlmacen() + [p.Ubicacion for p in pedidos_actuales]
grafo = {'Nodos': nodos_grafo} // Simplificado, la conectividad es implícita por distancia
feromonas = Inicializar_Feromonas(grafo.Nodos, FEROMONA_INICIAL)
heuristica = Calcular_Heuristica(grafo.Nodos, pedidos_actuales, tiempo_simulacion_actual)

    mejor_solucion_global = NULO // Almacenará la mejor secuencia de rutas encontrada
    mejor_costo_global = INFINITO

    // 2. Ciclo Principal del ACO
    PARA iteracion DESDE 1 HASTA NUM_ITERACIONES:
        soluciones_iteracion = [] // Almacena tuplas (solucion_construida, costo_evaluado) de esta iteración

        // 2a. Construcción de Soluciones por Hormigas
        PARA h DESDE 1 HASTA NUM_HORMIGAS:
            // Cada hormiga intenta construir un plan completo (conjunto de rutas para varios camiones)
            solucion_hormiga = Construir_Solucion_Hormiga(
                                    grafo.Nodos, feromonas, heuristica,
                                    pedidos_actuales, estado_flota_actual,
                                    bloqueos_actuales, tiempo_simulacion_actual, ALFA, BETA
                                )

            // 2b. Evaluación de la Solución Construida
            costo_solucion = Evaluar_Solucion_ACO(solucion_hormiga, bloqueos_actuales,
                                                  tiempo_simulacion_actual, estado_flota_actual, pedidos_actuales)

            AÑADIR (solucion_hormiga, costo_solucion) A soluciones_iteracion

            // 2c. Actualizar Mejor Solución Global si es necesario
            SI costo_solucion < mejor_costo_global:
                mejor_costo_global = costo_solucion
                mejor_solucion_global = solucion_hormiga
                IMPRIMIR "Nueva mejor solución encontrada en iteración", iteracion, "con costo", mejor_costo_global // Opcional

        // 2d. Actualización de Feromonas
        Actualizar_Feromonas(feromonas, soluciones_iteracion, grafo.Nodos, RHO, Q)

        // Opcional: Aquí se podría verificar si han ocurrido eventos dinámicos (averías, nuevos bloqueos)
        // y potencialmente ajustar feromonas/heurísticas o forzar una re-planificación.

    // 3. Retorno del Resultado
    RETORNAR mejor_solucion_global, mejor_costo_global

// --- Funciones Auxiliares del ACO ---
****
FUNCION Inicializar_Feromonas(nodos, valor_inicial):
matriz_feromonas = {} // Usar diccionario de diccionarios para sparse/flexibilidad
PARA CADA nodo1 EN nodos:
matriz_feromonas[nodo1] = {}
PARA CADA nodo2 EN nodos:
SI nodo1 != nodo2:
matriz_feromonas[nodo1][nodo2] = valor_inicial
RETORNAR matriz_feromonas

FUNCION Calcular_Heuristica(nodos, pedidos, tiempo_actual):
matriz_heuristica = {}
mapa_pedidos = {p.Ubicacion: p for p in pedidos} // Acceso rápido a datos del pedido por ubicación

    PARA CADA nodo1 EN nodos:
        matriz_heuristica[nodo1] = {}
        PARA CADA nodo2 EN nodos:
            SI nodo1 != nodo2 Y nodo2 en mapa_pedidos: // Solo calculamos heurística hacia clientes
                distancia = CalcularDistancia(nodo1, nodo2)
                pedido_destino = mapa_pedidos[nodo2]
                tiempo_restante = (pedido_destino.HoraPedido + pedido_destino.HLimite) - tiempo_actual
                urgencia = 1.0 / (tiempo_restante + 0.0001) // Más urgente si queda poco tiempo

                // Heurística combinada: favorece nodos cercanos y urgentes
                // Ajustar pesos según importancia relativa
                valor_heuristico = (1.0 / (distancia + 0.0001)) * 1.0 + (urgencia) * 0.5
                matriz_heuristica[nodo1][nodo2] = valor_heuristico
            SINO:
                 matriz_heuristica[nodo1][nodo2] = 0.0 // No hay heurística directa a almacenes o a sí mismo
    RETORNAR matriz_heuristica


FUNCION Construir_Solucion_Hormiga(nodos_grafo, feromonas, heuristica, pedidos_originales, estado_flota, bloqueos, tiempo_base, alfa, beta):
solucion_completa_hormiga = [] // Lista de rutas [Ruta1, Ruta2, ...]
pedidos_pendientes = CopiaProfunda(pedidos_originales)
// Considerar estado dinámico de camiones: ubicación, carga, disponibilidad
camiones_simulados = Inicializar_Estado_Camiones(estado_flota, tiempo_base)

    MIENTRAS HAY pedidos_pendientes:
        camion_elegido = Seleccionar_Camion_Para_Nueva_Ruta(camiones_simulados, tiempo_base)
        SI camion_elegido ES NULO:
            BREAK // No hay camiones disponibles para iniciar más rutas

        // Iniciar ruta desde almacén asociado al camión o el más cercano/conveniente
        nodo_inicio_ruta = camion_elegido.UbicacionActual // Debe ser un almacén
        ruta_actual = {'CamionID': camion_elegido.ID, 'SecuenciaNodos': [nodo_inicio_ruta], 'TiempoSalida': camion_elegido.TiempoDisponible}
        ubicacion_actual_camion = nodo_inicio_ruta
        carga_actual_m3 = camion_elegido.CargaActual
        tiempo_actual_ruta = camion_elegido.TiempoDisponible

        // Bucle para añadir clientes a la ruta actual
        MIENTRAS VERDADERO:
            candidatos_factibles = Obtener_Candidatos_Factibles_Para_Ruta(
                                        ubicacion_actual_camion, pedidos_pendientes, carga_actual_m3,
                                        camion_elegido, tiempo_actual_ruta, bloqueos
                                    )

            SI NO HAY candidatos_factibles:
                BREAK // No más clientes posibles para esta ruta/camión

            siguiente_nodo_cliente = Elegir_Siguiente_Nodo_Probabilistico(
                                        ubicacion_actual_camion, candidatos_factibles,
                                        feromonas, heuristica, alfa, beta
                                     )

            // Simular viaje y entrega
            pedido_entregado = ObtenerPedidoPorNodo(siguiente_nodo_cliente)
            distancia = CalcularDistancia(ubicacion_actual_camion, siguiente_nodo_cliente)
            tiempo_viaje = CalcularTiempoViaje(distancia, camion_elegido.VelocidadPromedio)
            tiempo_llegada = tiempo_actual_ruta + tiempo_viaje

            // Actualizar estado
            AÑADIR siguiente_nodo_cliente A ruta_actual['SecuenciaNodos']
            Eliminar pedido_entregado DE pedidos_pendientes
            carga_actual_m3 -= pedido_entregado.MetrosCubicos
            tiempo_actual_ruta = tiempo_llegada // Asumir tiempo de servicio 0 o añadirlo aquí
            ubicacion_actual_camion = siguiente_nodo_cliente
            camion_elegido.CargaActual = carga_actual_m3 // Actualizar estado simulado

        // Finalizar ruta volviendo al almacén
        nodo_almacen_final = Encontrar_Almacen_Cercano(ubicacion_actual_camion) // O almacén origen
        dist_retorno = CalcularDistancia(ubicacion_actual_camion, nodo_almacen_final)
        tiempo_viaje_retorno = CalcularTiempoViaje(dist_retorno, camion_elegido.VelocidadPromedio)
        tiempo_fin_ruta = tiempo_actual_ruta + tiempo_viaje_retorno

        AÑADIR nodo_almacen_final A ruta_actual['SecuenciaNodos']
        ruta_actual['TiempoLlegadaAlmacen'] = tiempo_fin_ruta
        AÑADIR ruta_actual A solucion_completa_hormiga

        // Actualizar disponibilidad del camión simulado para posible uso futuro en la misma construcción
        camion_elegido.TiempoDisponible = tiempo_fin_ruta
        camion_elegido.UbicacionActual = nodo_almacen_final
        // Aquí podría simularse recarga si es necesario y permitido

    RETORNAR solucion_completa_hormiga

FUNCION Obtener_Candidatos_Factibles_Para_Ruta(ubicacion_actual, pedidos_pendientes, carga_actual_m3, camion, tiempo_ruta_actual, bloqueos):
candidatos = []
PARA CADA pedido EN pedidos_pendientes:
nodo_cliente = pedido.Ubicacion
// 1. Chequeo Capacidad
SI pedido.MetrosCubicos <= carga_actual_m3:
dist = CalcularDistancia(ubicacion_actual, nodo_cliente)
tiempo_viaje = CalcularTiempoViaje(dist, camion.VelocidadPromedio)
tiempo_llegada_estimado = tiempo_ruta_actual + tiempo_viaje

            // 2. Chequeo Bloqueo
            SI NO VerificarBloqueo(ubicacion_actual, nodo_cliente, tiempo_llegada_estimado):
                // 3. Chequeo Ventana de Tiempo
                SI EsTiempoValido(pedido, tiempo_llegada_estimado):
                    // 4. Chequeo Combustible (opcional, más complejo, verificar si puede llegar Y regresar)
                    // SI TieneCombustibleSuficiente(camion, distancia_ida_vuelta_estimada):
                        AÑADIR nodo_cliente A candidatos
    RETORNAR candidatos


FUNCION Elegir_Siguiente_Nodo_Probabilistico(nodo_actual, nodos_candidatos, feromonas, heuristica, alfa, beta):
suma_ponderada = 0.0
probabilidades_seleccion = {}

    PARA CADA nodo_candidato EN nodos_candidatos:
        valor_feromona = feromonas[nodo_actual][nodo_candidato] ** alfa
        valor_heuristica = heuristica[nodo_actual][nodo_candidato] ** beta
        ponderacion = valor_feromona * valor_heuristica
        probabilidades_seleccion[nodo_candidato] = ponderacion
        suma_ponderada += ponderacion

    // Selección por Ruleta
    valor_ruleta = ALEATORIO_ENTRE(0, suma_ponderada)
    acumulado = 0.0
    PARA CADA nodo_candidato EN nodos_candidatos:
        acumulado += probabilidades_seleccion[nodo_candidato]
        SI acumulado >= valor_ruleta:
            RETORNAR nodo_candidato

    // Fallback: si suma_ponderada es 0 o error numérico, elegir uno al azar
    SI HAY nodos_candidatos:
        RETORNAR ALEATORIO(nodos_candidatos)
    SINO:
        RETORNAR NULO

FUNCION Evaluar_Solucion_ACO(solucion, bloqueos, tiempo_base, flota_estado, pedidos_originales):
costo_total_combustible = 0
penalizacion_total = 0
pedidos_cubiertos_en_solucion = set()

    // Verificar que todos los camiones usados estaban disponibles al inicio de su ruta
    PARA CADA ruta EN solucion:
        camion = flota_estado.ObtenerCamion(ruta['CamionID'])
        SI NO camion.EstaDisponible(ruta['TiempoSalida']):
            penalizacion_total += PENALIZACION_CAMION_NO_DISPONIBLE

        // Simular la ruta para calcular costo y verificar restricciones post-facto
        ubicacion_actual = ruta['SecuenciaNodos'][0]
        tiempo_actual = ruta['TiempoSalida']
        carga_actual_m3 = camion.Capacidad // Asumiendo sale lleno del almacén inicial
        peso_tara = camion.PesoTara

        PARA i DESDE 0 HASTA LARGO(ruta['SecuenciaNodos']) - 2:
            nodo_siguiente = ruta['SecuenciaNodos'][i+1]
            distancia_tramo = CalcularDistancia(ubicacion_actual, nodo_siguiente)
            tiempo_viaje_tramo = CalcularTiempoViaje(distancia_tramo, camion.VelocidadPromedio)
            tiempo_llegada_estimado = tiempo_actual + tiempo_viaje_tramo

            // Recalcular peso combinado para el tramo
            peso_carga = PesoCargaEfectiva(carga_actual_m3, camion.Tipo)
            peso_combinado = peso_tara + peso_carga
            costo_total_combustible += CalcularConsumoCombustible(distancia_tramo, peso_combinado)

            // Re-verificar restricciones (importante si la construcción tuvo heurísticas imperfectas)
            SI VerificarBloqueo(ubicacion_actual, nodo_siguiente, tiempo_llegada_estimado):
                penalizacion_total += PENALIZACION_BLOQUEO
            SI nodo_siguiente.EsCliente():
                pedido = ObtenerPedidoPorNodo(nodo_siguiente)
                pedidos_cubiertos_en_solucion.add(pedido.ID)
                SI NO EsTiempoValido(pedido, tiempo_llegada_estimado):
                    penalizacion_total += PENALIZACION_TIEMPO_LIMITE
                // Actualizar carga después de la entrega
                carga_actual_m3 -= pedido.MetrosCubicos
                SI carga_actual_m3 < 0: // Chequeo implícito de capacidad si se carga bien
                    penalizacion_total += PENALIZACION_CAPACIDAD

            // Actualizar para siguiente tramo
            ubicacion_actual = nodo_siguiente
            tiempo_actual = tiempo_llegada_estimado

    // Penalizar por pedidos no cubiertos
    num_pedidos_no_cubiertos = LARGO(pedidos_originales) - LARGO(pedidos_cubiertos_en_solucion)
    penalizacion_total += num_pedidos_no_cubiertos * PENALIZACION_PEDIDO_NO_CUBIERTO

    RETORNAR costo_total_combustible + penalizacion_total

FUNCION Actualizar_Feromonas(feromonas, soluciones_iteracion, nodos, rho, Q):
// 1. Evaporación en todas las aristas
PARA CADA nodo1 EN nodos:
PARA CADA nodo2 EN nodos:
SI nodo1 != nodo2 Y nodo2 en feromonas[nodo1]:
feromonas[nodo1][nodo2] *= (1.0 - rho)

    // 2. Depósito basado en la calidad de las soluciones
    PARA CADA (solucion, costo) EN soluciones_iteracion:
        SI costo >= INFINITO O costo == 0: CONTINUAR // No depositar para inválidas o costo cero

        delta_feromona = Q / costo // Soluciones de menor costo depositan más feromona

        PARA CADA ruta EN solucion:
            PARA i DESDE 0 HASTA LARGO(ruta['SecuenciaNodos']) - 2:
                nodo_origen = ruta['SecuenciaNodos'][i]
                nodo_destino = ruta['SecuenciaNodos'][i+1]
                // Añadir feromona al tramo recorrido
                feromonas[nodo_origen][nodo_destino] += delta_feromona
                // Considerar si el depósito es simétrico (feromonas[destino][origen] += delta) o no

// --- Funciones Adicionales para Simulación de Estado ---
FUNCION Inicializar_Estado_Camiones(estado_flota_base, tiempo_inicial):
// Crea una copia del estado de la flota para la simulación de una hormiga
// Asegurando que la carga inicial, ubicación y tiempo disponible sean correctos
// ...
RETORNAR estado_simulado_flota

FUNCION Seleccionar_Camion_Para_Nueva_Ruta(camiones_simulados, tiempo_actual):
// Elige un camión disponible (tiempo disponible <= tiempo_actual) desde un almacén
// Podría ser el primero disponible, el más cercano a pedidos urgentes, etc.
// ...
RETORNAR camion_elegido O NULO