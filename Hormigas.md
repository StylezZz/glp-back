ACO:

// ALGORITMO HÍBRIDO DE COLONIA DE HORMIGAS PARA OPTIMIZACIÓN DE RUTAS PLG
// Integra las mejores características de ambos enfoques

// Parámetros del algoritmo
númeroDeHormigas ← 30
númeroDeIteraciones ← 500
maxIteracionesSinMejora ← 50
umbralConvergenciaTemprana ← 0.6  // 60% del total de iteraciones
factorEvaporación ← 0.5
alfa ← 1.0  // Importancia de las feromonas
beta ← 2.0  // Importancia de la heurística
q0 ← 0.9    // Parámetro de exploración vs explotación
feromonaInicial ← 0.1

// PROCEDIMIENTO PRINCIPAL
PROCEDIMIENTO algoritmoACOHibrido()
// 1. Configuración Inicial
Cargar_Datos()
grafo ← Crear_Grafo_Rejilla_Ciudad()
feromona ← Inicializar_Matriz_Feromonas(grafo)
heuristicaBase ← Calcular_Matriz_Heuristica_Base(grafo)
Inicializar_Variables_Globales()

    // 2. Ciclo Principal ACO
    Ciclo_Principal_ACO(grafo, feromona, heuristicaBase)
    
    // 3. Mostrar resultados finales
    Mostrar_Resultados(mejor_solucion_global)
FIN PROCEDIMIENTO

// CICLO PRINCIPAL DEL ALGORITMO
PROCEDIMIENTO Ciclo_Principal_ACO(grafo, feromona, heuristicaBase)
// Variables de control
iteracion ← 0
iterSinMejora ← 0

    MIENTRAS iteracion < númeroDeIteraciones HACER
        // A. Revisar y actualizar eventos dinámicos
        huboEventos ← Revisar_Y_Actualizar_Eventos_Dinamicos()
        SI huboEventos ENTONCES
            iterSinMejora ← 0  // Resetear contador si cambió el entorno
        FIN SI
        
        // B. Actualizar heurística con información dinámica actual
        heuristicaActual ← Actualizar_Heuristica_Dinamica(grafo, heuristicaBase)
        
        // C. Construcción de soluciones por cada hormiga
        soluciones ← []
        PARA hormiga ← 1 HASTA númeroDeHormigas HACER
            solucion ← Hormiga_Construye_Plan_Completo(grafo, feromona, heuristicaActual)
            calidad ← Evaluar_Solucion(solucion)
            AÑADIR (solucion, calidad) A soluciones
            
            // Actualizar mejor global si corresponde
            SI calidad > mejor_calidad_global ENTONCES
                mejor_solucion_global ← solucion
                mejor_calidad_global ← calidad
                // No resetear iterSinMejora aquí, se hace después
            FIN SI
        FIN PARA
        
        // D. Actualizar feromonas basado en soluciones encontradas
        feromona ← Actualizar_Feromonas(grafo, feromona, soluciones)
        
        // E. Verificar mejora y control de convergencia
        SI mejor_calidad_global > mejor_calidad_anterior ENTONCES
            iterSinMejora ← 0  // Hubo mejora, resetear contador
        SINO
            iterSinMejora ← iterSinMejora + 1  // No hubo mejora, incrementar
        FIN SI
        mejor_calidad_anterior ← mejor_calidad_global
        
        // F. Aplicar mecanismo anti-estancamiento si es necesario
        SI iterSinMejora >= maxIteracionesSinMejora ENTONCES
            SI iteracion < númeroDeIteraciones * umbralConvergenciaTemprana ENTONCES
                // Convergencia temprana: perturbar para escapar de óptimo local
                feromona ← Perturbar_Feromonas(feromona)
                iterSinMejora ← 0  // Resetear contador tras perturbación
            SINO
                // Convergencia tardía: asumir que se encontró buena solución
                IMPRIMIR "Convergencia alcanzada en iteración", iteracion
                SALIR  // Terminar bucle principal
            FIN SI
        FIN SI
        
        // G. Incrementar contador de iteración
        iteracion ← iteracion + 1
    FIN MIENTRAS
FIN PROCEDIMIENTO

// FUNCIONES PRINCIPALES DEL ALGORITMO

// Función 1: Creación del grafo rejilla
FUNCIÓN Crear_Grafo_Rejilla_Ciudad()
// Crea un grafo que representa la rejilla 70x50 de la ciudad
grafo ← nuevoGrafo()

    // Añadir todos los nodos (intersecciones)
    PARA x ← 0 HASTA 70 HACER
        PARA y ← 0 HASTA 50 HACER
            AÑADIR NODO(x, y) A grafo
        FIN PARA
    FIN PARA
    
    // Añadir aristas horizontales y verticales (calles)
    // Solo se permiten movimientos en los ejes X e Y
    PARA x ← 0 HASTA 70 HACER
        PARA y ← 0 HASTA 50 HACER
            SI x < 70 ENTONCES
                AÑADIR ARISTA((x,y), (x+1,y)) A grafo  // Horizontal
            FIN SI
            SI y < 50 ENTONCES
                AÑADIR ARISTA((x,y), (x,y+1)) A grafo  // Vertical
            FIN SI
        FIN PARA
    FIN PARA
    
    // Marcar nodos especiales (almacenes)
    MARCAR NODO(12, 8) COMO 'Almacén Central'
    MARCAR NODO(42, 42) COMO 'Almacén Intermedio Norte'
    MARCAR NODO(63, 3) COMO 'Almacén Intermedio Este'
    
    RETORNAR grafo
FIN FUNCIÓN

// Función 2: Inicialización de feromonas
FUNCIÓN Inicializar_Matriz_Feromonas(grafo)
// Inicializa la matriz de feromonas con un valor pequeño
matriz ← nuevaMatriz(TAMAÑO(nodos(grafo)), TAMAÑO(nodos(grafo)))

    PARA CADA arista EN grafo HACER
        origen, destino ← extremos(arista)
        matriz[origen][destino] ← feromonaInicial
        matriz[destino][origen] ← feromonaInicial  // Grafo no dirigido
    FIN PARA
    
    RETORNAR matriz
FIN FUNCIÓN

// Función 3: Cálculo de matriz heurística base
FUNCIÓN Calcular_Matriz_Heuristica_Base(grafo)
    
    // Calcula la matriz heurística basada en distancias Manhattan
    matriz ← nuevaMatriz(TAMAÑO(nodos(grafo)), TAMAÑO(nodos(grafo)))

    PARA CADA arista EN grafo HACER
        origen, destino ← extremos(arista)
        distancia ← calcularDistanciaManhattan(origen, destino)
        matriz[origen][destino] ← 1 / distancia  // Inverso de la distancia
        matriz[destino][origen] ← 1 / distancia  // Grafo no dirigido
    FIN PARA
    
    RETORNAR matriz
FIN FUNCIÓN

// Función 4: Actualización de heurística dinámica
FUNCIÓN Actualizar_Heuristica_Dinamica(grafo, heuristicaBase)
// Copia la heurística base
heuristicaActual ← COPIAR(heuristicaBase)

    // Ajustar según bloqueos actuales
    PARA CADA nodo EN grafo HACER
        SI estaBloquado(nodo, obtenerTiempoActual()) ENTONCES
            // Para todos los vecinos de este nodo, reducir drásticamente la heurística
            PARA CADA vecino EN obtenerVecinos(grafo, nodo) HACER
                // Valor muy pequeño para evitar su selección
                heuristicaActual[nodo][vecino] ← 0.0001
                heuristicaActual[vecino][nodo] ← 0.0001
            FIN PARA
        FIN SI
    FIN PARA
    
    // Ajustar también según urgencia de pedidos
    pedidosActuales ← obtenerPedidosActuales()
    PARA CADA pedido EN pedidosActuales HACER
        ubicaciónPedido ← ubicación(pedido)
        urgencia ← calcularUrgenciaNormalizada(pedido)  // 0 a 1, mayor es más urgente
        
        // Aumentar heurística hacia pedidos urgentes
        PARA CADA nodo EN grafo HACER
            SI estáCerca(nodo, ubicaciónPedido) ENTONCES
                // Boost heurístico proporcional a la urgencia
                PARA CADA vecino EN obtenerVecinos(grafo, nodo) HACER
                    SI distancia(vecino, ubicaciónPedido) < distancia(nodo, ubicaciónPedido) ENTONCES
                        heuristicaActual[nodo][vecino] ← heuristicaActual[nodo][vecino] * (1 + urgencia)
                    FIN SI
                FIN PARA
            FIN SI
        FIN PARA
    FIN PARA
    
    RETORNAR heuristicaActual
FIN FUNCIÓN

// Función 5: Construcción de solución completa por una hormiga
FUNCIÓN Hormiga_Construye_Plan_Completo(grafo, feromona, heuristica)
// Obtener pedidos y camiones disponibles
pedidosPendientes ← obtenerPedidosActuales()
camionesDisponibles ← obtenerCamionesDisponibles()

    // Asignar pedidos a camiones según urgencia y capacidad
    asignaciones ← Asignar_Pedidos_A_Camiones(pedidosPendientes, camionesDisponibles)
    
    planCompleto ← []
    PARA CADA (camión, pedidos) EN asignaciones HACER
        // Para cada asignación, construir una ruta óptima
        ruta ← Construir_Ruta_Para_Asignacion(camión, pedidos, grafo, feromona, heuristica)
        AÑADIR (camión, pedidos, ruta) A planCompleto
    FIN PARA
    
    RETORNAR planCompleto
FIN FUNCIÓN

// Función 6: Asignación de pedidos a camiones
FUNCIÓN Asignar_Pedidos_A_Camiones(pedidos, camionesDisponibles)
// Ordenar pedidos por urgencia (tiempo límite)
ordenarPorUrgencia(pedidos)
pedidosPendientes ← COPIAR(pedidos)
asignaciones ← []

    PARA CADA camión EN camionesDisponibles HACER
        SI pedidosPendientes ESTÁ VACÍO ENTONCES
            SALIR
        FIN SI
        
        capacidad ← obtenerCapacidad(camión)
        pedidosAsignados ← []
        
        // Asignar pedidos hasta llenar la capacidad del camión
        MIENTRAS pedidosPendientes NO ESTÉ VACÍO Y capacidad > 0 HACER
            // Intentar ajustar el pedido más urgente primero
            pedidoActual ← pedidosPendientes[0]
            
            SI volumen(pedidoActual) <= capacidad ENTONCES
                AÑADIR pedidoActual A pedidosAsignados
                ELIMINAR pedidoActual DE pedidosPendientes
                capacidad ← capacidad - volumen(pedidoActual)
            SINO
                // Si no cabe, probar con el siguiente
                PARA i ← 1 HASTA TAMAÑO(pedidosPendientes) - 1 HACER
                    SI volumen(pedidosPendientes[i]) <= capacidad ENTONCES
                        AÑADIR pedidosPendientes[i] A pedidosAsignados
                        capacidad ← capacidad - volumen(pedidosPendientes[i])
                        ELIMINAR pedidosPendientes[i] DE pedidosPendientes
                        SALIR
                    FIN SI
                FIN PARA
                
                // Si no se pudo añadir ninguno, salir
                SI capacidad = obtenerCapacidad(camión) ENTONCES
                    SALIR
                FIN SI
            FIN SI
        FIN MIENTRAS
        
        SI pedidosAsignados NO ESTÁ VACÍO ENTONCES
            AÑADIR (camión, pedidosAsignados) A asignaciones
        FIN SI
    FIN PARA
    
    RETORNAR asignaciones
FIN FUNCIÓN

// Función 7: Construcción de ruta para una asignación
FUNCIÓN Construir_Ruta_Para_Asignacion(camión, pedidos, grafo, feromona, heuristica)
ubicaciónInicial ← obtenerUbicaciónActual(camión)
nodoActual ← ubicaciónInicial
ruta ← [nodoActual]
pedidosRestantes ← COPIAR(pedidos)

    MIENTRAS pedidosRestantes NO ESTÉ VACÍO HACER
        // Seleccionar el siguiente pedido usando regla de transición de estado
        siguiente ← Seleccionar_Siguiente_Pedido(nodoActual, pedidosRestantes, feromona, heuristica)
        
        SI siguiente = NULL ENTONCES
            SALIR // No se puede continuar, no hay opciones viables
        FIN SI
        
        // Calcular ruta desde nodoActual hasta ubicación del siguiente pedido
        subRuta ← Encontrar_Ruta_Viable(nodoActual, ubicación(siguiente), grafo)
        AÑADIR subRuta A ruta
        
        // Actualizar estado
        nodoActual ← ubicación(siguiente)
        ELIMINAR siguiente DE pedidosRestantes
    FIN MIENTRAS
    
    // Añadir ruta de regreso al almacén
    subRutaRegreso ← Encontrar_Ruta_Viable(nodoActual, ubicaciónInicial, grafo)
    AÑADIR subRutaRegreso A ruta
    
    RETORNAR ruta
FIN FUNCIÓN

// Función 8: Selección del siguiente pedido (regla de transición de estado)
FUNCIÓN Seleccionar_Siguiente_Pedido(nodoActual, pedidosRestantes, feromona, heuristica)
SI ALEATORIO() < q0 ENTONCES
// Explotación: elegir el mejor pedido según feromonas y heurística
mejorValor ← -INFINITO
mejorPedido ← NULL

        PARA CADA pedido EN pedidosRestantes HACER
            ubicaciónPedido ← ubicación(pedido)
            valor ← feromona[nodoActual][ubicaciónPedido]^alfa * heuristica[nodoActual][ubicaciónPedido]^beta
            
            SI valor > mejorValor ENTONCES
                mejorValor ← valor
                mejorPedido ← pedido
            FIN SI
        FIN PARA
        
        RETORNAR mejorPedido
    SINO
        // Exploración: selección probabilística
        total ← 0
        probabilidades ← []
        
        PARA CADA pedido EN pedidosRestantes HACER
            ubicaciónPedido ← ubicación(pedido)
            valor ← feromona[nodoActual][ubicaciónPedido]^alfa * heuristica[nodoActual][ubicaciónPedido]^beta
            probabilidades.añadir((pedido, valor))
            total ← total + valor
        FIN PARA
        
        // Normalizar probabilidades
        PARA i ← 0 HASTA TAMAÑO(probabilidades) - 1 HACER
            probabilidades[i].valor ← probabilidades[i].valor / total
        FIN PARA
        
        // Selección por ruleta
        selección ← ALEATORIO(0, 1)
        acumulado ← 0
        
        PARA CADA (pedido, prob) EN probabilidades HACER
            acumulado ← acumulado + prob
            SI acumulado >= selección ENTONCES
                RETORNAR pedido
            FIN SI
        FIN PARA
        
        // En caso de error numérico, retornar el último
        RETORNAR probabilidades[ÚLTIMO].pedido
    FIN SI
FIN FUNCIÓN

// Función 9: Encontrar ruta viable con A* considerando bloqueos
FUNCIÓN Encontrar_Ruta_Viable(origen, destino, grafo)
// Implementa algoritmo A* considerando los bloqueos de calles
nodoInicial ← origen
nodoObjetivo ← destino
listaAbierta ← [nodoInicial]  // Nodos por explorar
listaCerrada ← []             // Nodos ya explorados
padres ← {}                   // Mapa de nodo a su padre
g ← {nodoInicial: 0}          // Costo acumulado desde el origen
f ← {nodoInicial: calcularDistanciaManhattan(nodoInicial, nodoObjetivo)}  // Costo estimado total

    MIENTRAS listaAbierta NO ESTÉ VACÍA HACER
        actual ← nodo en listaAbierta con menor f
        
        SI actual = nodoObjetivo ENTONCES
            RETORNAR Reconstruir_Ruta(padres, actual)
        FIN SI
        
        ELIMINAR actual DE listaAbierta
        AÑADIR actual A listaCerrada
        
        // Explorar solo vecinos horizontales y verticales (no diagonales)
        PARA CADA vecino DE obtenerVecinosOrtogonales(grafo, actual) HACER
            SI vecino EN listaCerrada ENTONCES
                CONTINUAR
            FIN SI
            
            // Verificar si el vecino está bloqueado en el tiempo estimado de paso
            SI estaBloquado(vecino, tiempoEstimadoDePaso(g[actual])) ENTONCES
                CONTINUAR
            FIN SI
            
            costoTentativo ← g[actual] + 1  // Distancia siempre es 1 entre nodos adyacentes
            
            SI vecino NO EN listaAbierta ENTONCES
                AÑADIR vecino A listaAbierta
                mejorCamino ← VERDADERO
            SINO SI costoTentativo < g[vecino] ENTONCES
                mejorCamino ← VERDADERO
            SINO
                mejorCamino ← FALSO
            FIN SI
            
            SI mejorCamino ENTONCES
                padres[vecino] ← actual
                g[vecino] ← costoTentativo
                f[vecino] ← g[vecino] + calcularDistanciaManhattan(vecino, nodoObjetivo)
            FIN SI
        FIN PARA
    FIN MIENTRAS
    
    // No se encontró ruta viable
    RETORNAR []
FIN FUNCIÓN

// Función 10: Reconstrucción de ruta a partir de padres
FUNCIÓN Reconstruir_Ruta(padres, nodoActual)
rutaTotal ← [nodoActual]
MIENTRAS nodoActual EN padres HACER
nodoActual ← padres[nodoActual]
INSERTAR nodoActual AL INICIO DE rutaTotal
FIN MIENTRAS
RETORNAR rutaTotal
FIN FUNCIÓN

// Función 11: Evaluar solución completa
FUNCIÓN Evaluar_Solucion(solucion)
// Evalúa una solución completa (todas las asignaciones y rutas)
consumoTotal ← 0
penalizaciónTiempo ← 0
penalizaciónRestricciones ← 0

    PARA CADA (camión, pedidos, ruta) EN solucion HACER
        // Calcular consumo para esta ruta
        distanciaTotal ← calcularDistanciaTotal(ruta)
        pesoInicial ← obtenerPesoTara(camión) + calcularPesoCargaTotal(pedidos)
        
        // El peso va disminuyendo a medida que se entregan pedidos
        pesoActual ← pesoInicial
        tiempoActual ← obtenerTiempoActual()
        
        PARA CADA segmento EN ruta HACER
            distanciaSegmento ← calcularDistancia(segmento)
            consumoSegmento ← (distanciaSegmento * pesoActual) / 180
            consumoTotal ← consumoTotal + consumoSegmento
            
            // Actualizar peso si se entregó un pedido
            SI esEntregaPedido(segmento) ENTONCES
                pedidoEntregado ← obtenerPedido(segmento)
                pesoActual ← pesoActual - calcularPesoCarga(pedidoEntregado)
                
                // Verificar tiempo límite
                tiempoEntrega ← tiempoActual + tiempoEstimadoRecorrido(segmento)
                SI tiempoEntrega > obtenerTiempoLímite(pedidoEntregado) ENTONCES
                    // Penalización por entrega tardía
                    penalizaciónTiempo ← penalizaciónTiempo + 1000 * (tiempoEntrega - obtenerTiempoLímite(pedidoEntregado))
                FIN SI
                
                tiempoActual ← tiempoEntrega
            FIN SI
            
            // Verificar si hay bloqueos en la ruta en el momento del paso
            SI hayBloqueoEnSegmento(segmento, tiempoActual) ENTONCES
                penalizaciónRestricciones ← penalizaciónRestricciones + 5000
            FIN SI
        FIN PARA
        
        // Verificar si el camión tiene mantenimiento durante la ruta
        SI tieneProgramadoMantenimiento(camión, tiempoActual, tiempoActual + tiempoEstimadoTotal(ruta)) ENTONCES
            penalizaciónRestricciones ← penalizaciónRestricciones + 10000
        FIN SI
    FIN PARA
    
    // Calcular calidad total (inversamente proporcional a los costos/penalizaciones)
    calidad ← 1 / (1 + consumoTotal + penalizaciónTiempo + penalizaciónRestricciones)
    
    RETORNAR calidad
FIN FUNCIÓN

// Función 12: Actualización de feromonas
FUNCIÓN Actualizar_Feromonas(grafo, feromona, soluciones)
// Evaporación global de feromonas
PARA i ← 0 HASTA TAMAÑO(feromona) - 1 HACER
PARA j ← 0 HASTA TAMAÑO(feromona) - 1 HACER
feromona[i][j] ← feromona[i][j] * (1 - factorEvaporación)
FIN PARA
FIN PARA

    // Depósito de feromonas proporcional a la calidad
    PARA CADA (solucion, calidad) EN soluciones HACER
        // Factor de depósito base proporcional a la calidad
        factorDeposito ← calidad * 10
        
        // Para cada ruta en la solución
        PARA CADA (camión, pedidos, ruta) EN solucion HACER
            // Para cada segmento de la ruta
            PARA i ← 0 HASTA TAMAÑO(ruta) - 2 HACER
                nodoOrigen ← ruta[i]
                nodoDestino ← ruta[i+1]
                
                // Depósito de feromona inversamente proporcional a la distancia
                // y proporcional a la calidad de la solución
                feromona[nodoOrigen][nodoDestino] ← feromona[nodoOrigen][nodoDestino] + factorDeposito
                feromona[nodoDestino][nodoOrigen] ← feromona[nodoDestino][nodoOrigen] + factorDeposito
            FIN PARA
        FIN PARA
    FIN PARA
    
    RETORNAR feromona
FIN FUNCIÓN

// Función 13: Perturbación de feromonas para escapar de óptimos locales
FUNCIÓN Perturbar_Feromonas(feromona)
// Añadir perturbaciones aleatorias a la matriz de feromonas
perturbación ← 0.2  // 20% de perturbación máxima

    PARA i ← 0 HASTA TAMAÑO(feromona) - 1 HACER
        PARA j ← 0 HASTA TAMAÑO(feromona) - 1 HACER
            // Añadir ruido aleatorio entre -perturbación y +perturbación
            factorRuido ← 1 + ALEATORIO(-perturbación, perturbación)
            feromona[i][j] ← feromona[i][j] * factorRuido
            
            // Garantizar valor mínimo de feromona
            SI feromona[i][j] < feromonaInicial / 2 ENTONCES
                feromona[i][j] ← feromonaInicial / 2
            FIN SI
        FIN PARA
    FIN PARA
    
    // Adicionalmente, reforzar aleatoriamente algunos caminos
    // para fomentar exploración en nuevas áreas
    numRefuerzos ← TAMAÑO(feromona) / 10  // 10% de los nodos
    
    PARA k ← 1 HASTA numRefuerzos HACER
        i ← ALEATORIO_ENTERO(0, TAMAÑO(feromona) - 1)
        j ← ALEATORIO_ENTERO(0, TAMAÑO(feromona) - 1)
        
        // Solo reforzar si son nodos adyacentes
        SI sonAdyacentes(i, j) ENTONCES
            feromona[i][j] ← feromona[i][j] * 3  // Triplicar feromona
            feromona[j][i] ← feromona[j][i] * 3
        FIN SI
    FIN PARA
    
    RETORNAR feromona
FIN FUNCIÓN

// Función 14: Revisar y actualizar eventos dinámicos
FUNCIÓN Revisar_Y_Actualizar_Eventos_Dinamicos()
    // 1. Iniciar sección crítica para evitar accesos concurrentes a estadoAverias
    LOCK(estadoAverias)

    // 2. Consultar nuevas averías provenientes del sistema de monitoreo (ej. colas o notificaciones)
    nuevasAverias ← obtenerNuevasAverias()
    
    // 3. Procesar cada avería nueva
    PARA CADA averia EN nuevasAverias HACER
        // Verificar si la avería ya está registrada o si existe algún conflicto (por ejemplo, averías repetidas)
        SI existeConflictoConEventoExistente(averia, estadoAverias.listaAverias) ENTONCES
            Resolver_Conflicto_Averia(averia, estadoAverias)
        SINO
            // Agregar la nueva avería a la lista de averías dinámicas
            AÑADIR averia A estadoAverias.listaAverias
        FIN SI
        
        // Actualizar inmediatamente el estado del camión afectado según el tipo de avería
        camion ← obtenerCamion(averia)
        actualizarEstadoCamion(camion, averia)
    FIN PARA

    // 4. Filtrar o limpiar averías que ya hayan sido resueltas o hayan expirado
    estadoAverias.listaAverias ← FiltrarEventosVigentes(estadoAverias.listaAverias)
    
    // 5. Liberar la sección crítica para permitir acceso a otros procesos
    UNLOCK(estadoAverias)
    
    // 6. Retornar indicador de que se han producido actualizaciones en el entorno de averías
    RETORNAR (nuevasAverias NO ESTÁ VACÍO)
FIN FUNCIÓN

// Función 15: Gestionar averías (trasvase y adaptación)
FUNCIÓN Gestionar_Averias(camionAveriado, pedidos, soluciones)
tipoAveria ← obtenerTipoAveria(camionAveriado)

    // Verificar si se puede hacer trasvase
    SI puedeRealizarTrasvase(tipoAveria) ENTONCES
        camionesDisponibles ← obtenerCamionesDisponiblesParaTrasvase(soluciones)
        
        // Asignar pedidos a otros camiones disponibles
        PARA CADA pedido EN pedidos HACER
            // Buscar camión con capacidad suficiente
            mejorCamion ← NULL
            mejorValor ← INFINITO
            
            PARA CADA camion EN camionesDisponibles HACER
                SI tieneCapacidadPara(camion, pedido) ENTONCES
                    // Evaluar costo del trasvase
                    costo ← evaluarCostoTrasvase(camion, pedido, ubicacionActual(camionAveriado))
                    SI costo < mejorValor ENTONCES
                        mejorValor ← costo
                        mejorCamion ← camion
                    FIN SI
                FIN SI
            FIN PARA
            
            // Realizar trasvase si se encontró camión adecuado
            SI mejorCamion ≠ NULL ENTONCES
                realizarTrasvase(mejorCamion, camionAveriado, pedido, soluciones)
            FIN SI
        FIN PARA
    FIN SI
    
    // Actualizar estado y ruta del camión averiado
    actualizarEstadoCamionAveriado(camionAveriado, tipoAveria, soluciones)
    
    RETORNAR soluciones
FIN FUNCIÓN

// Función 16: Calcular distancia Manhattan
FUNCIÓN calcularDistanciaManhattan(punto1, punto2)
    // Distancia Manhattan (solo movimientos horizontales y verticales)
    RETORNAR ABS(punto1.x - punto2.x) + ABS(punto1.y - punto2.y)
FIN FUNCIÓN

// Función auxiliar: obtener vecinos ortogonales (solo H/V, no diagonales)
FUNCIÓN obtenerVecinosOrtogonales(grafo, nodo)
vecinos ← []

    // Comprobar los cuatro vecinos posibles (arriba, abajo, izquierda, derecha)
    direcciones ← [(0,1), (0,-1), (1,0), (-1,0)]
    
    PARA CADA (dx, dy) EN direcciones HACER
        vecinoX ← nodo.x + dx
        vecinoY ← nodo.y + dy
        
        // Verificar si el vecino está dentro de los límites del grafo
        SI 0 <= vecinoX <= 70 Y 0 <= vecinoY <= 50 ENTONCES
            vecino ← obtenerNodo(grafo, vecinoX, vecinoY)
            AÑADIR vecino A vecinos
        FIN SI
    FIN PARA
    
    RETORNAR vecinos
FIN FUNCIÓN
