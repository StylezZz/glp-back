# Prompt Integral para la Generación y Entrenamiento de un Modelo de IA (Metaheurísticas)

Este prompt reúne **toda la información** relevante sobre la **empresa PLG**, el **mapa de la ciudad**, las **restricciones** (bloqueos, averías, mantenimientos, etc.) y los **objetivos** (cumplir entregas en tiempo, minimizar consumo de combustible, etc.). Está diseñado para que un **modelo de IA** (especializado en Algoritmos Genéticos y de Colonia de Hormigas) pueda **analizar**, **proponer** y **evaluar soluciones** de **planificación de rutas** y asignación de recursos (camiones cisterna) en distintos escenarios.

---

## 1. Contexto General

- **Empresa PLG** se dedica a la comercialización y distribución de GLP (Gas Licuado de Petróleo) en la ciudad XYZ.
- Dispone de:
    1. Un **almacén central** (suministro ininterrumpido).
    2. Dos **tanques intermedios** (cada uno con capacidad efectiva de 160 m³) abastecidos diariamente a las 0:00.
    3. Una **flota de camiones cisterna** con capacidades de 5 m³ a 25 m³ que requieren:
        - **Mantenimiento preventivo** (24 h cada 2 meses).
        - **Mantenimiento correctivo** (ante “averías” o incidentes).
- Todos los clientes realizan pedidos con un **mínimo de 4 h de anticipación**, y la **política de la empresa** es de **cero incumplimientos** (si se respeta la anticipación mínima).
- Se desea **minimizar el consumo de combustible** (directamente asociado al peso total transportado y la distancia recorrida) y **cumplir los compromisos de entrega** sin penalidades.

### Escenarios a Resolver
1. **Operaciones día a día.**
2. **Simulación semanal** (ejecución de entre 20 y 50 minutos).
3. **Simulación hasta el colapso** (cuando no es posible cumplir la política de entrega).

### Requisitos No Funcionales
1. Presentar **dos soluciones** (ambas **metaheurísticas**) en **Java**.
2. Ejecutarse en el equipamiento del laboratorio.
3. Ser evaluado mediante la norma **NTP-ISO/IEC 29110-5-1-2 (VSE)**.

---

## 2. Mapa de la Ciudad

- La **ciudad** es una **rejilla rectangular** de **70 km** (eje X) por **50 km** (eje Y).
- **No hay diagonales ni calles curvas**; solo ejes rectos con nodos cada 1 km.
- **Origen de coordenadas** en (0, 0), ubicado en la esquina inferior izquierda.
- **Ubicación de los almacenes**:
    - Almacén **Central**: (X=12, Y=8).
    - Almacén **Intermedio Norte**: (X=42, Y=42).
    - Almacén **Intermedio Este**: (X=63, Y=3).

---

## 3. Bloqueos de Calles

- Se presentan **bloqueos planificados** por la municipalidad, que impiden el paso en ciertos **tramos** o **nodos**.
- Estructura del **archivo de bloqueos**: `aaaamm.bloqueadas`, p.e. `202504.bloqueadas`.
    - Cada registro indica:
      ```
      ##d##h##m-##d##h##m:x1,y1,x2,y2, ... ,xn,yn
      ```
      donde:
        - `##d##h##m` = día, hora, minuto de inicio y de fin del bloqueo.
        - `(xi, yi)` = coordenadas de los nodos que delimitan un **polígono** (generalmente **abierto**).
- Al estar **bloqueado** un nodo, no se puede:
    - **Atravesarlo**.
    - **Girar** en él.
- Si un camión llega a un nodo bloqueado **por error**, debe **retroceder** por el mismo camino.

**En este proyecto**, se asume que:
- Sólo hay **bloqueos planificados** (no fortuitos).
- Se trabajará con **polígonos abiertos** (siempre se puede llegar a los puntos de la poligonal, aunque no se pueda atravesar la línea bloqueada).

---

## 4. Flota de Camiones Cisterna

Existen **4 tipos** de camiones con sus pesos (tara y carga), cada uno con un consumo específico:

| **Tipo** | **Peso Tara (Ton)** | **Capacidad (m³ GLP)** | **Peso Carga Lleno (Ton)** | **Peso Combinado (Ton)** | **Unidades Disponibles** |
|----------|---------------------|------------------------|----------------------------|--------------------------|--------------------------|
| TA       | 2.5                | 25                     | 12.5                       | 15.0                     | 02                       |
| TB       | 2.0                | 15                     | 7.5                        | 9.5                      | 04                       |
| TC       | 1.5                | 10                     | 5.0                        | 6.5                      | 04                       |
| TD       | 1.0                | 5                      | 2.5                        | 3.5                      | 10                       |

- **Código de camión**: `TTNN` (ej. TA01, TD10).
- **Consumo de combustible** (en galones) se calcula como:Consumo = (Distancia (Km) * Peso (Ton)) / 180
donde:
  - `Distancia` es el recorrido total (ida + vuelta).
  - `Peso` es el **peso combinado** (tara + (peso carga efectiva si transporta GLP)).
  - Cada camión tiene un **tanque máximo** de 25 gal de diésel:
  - Ejemplo: a peso combinado de 15 Ton, puede recorrer **300 km** (25 gal * 180 / 15) antes de recargar combustible.
  - **Velocidad promedio**: 50 km/h.
  - **Capacidad total de la flota**: 200 m³ (si todos los camiones estuvieran completamente operativos y cargados).

### Ejemplo de Cálculo de Peso
- Si el **TA01** (tara=2.5 Ton, capacidad=25 m³) lleva **5 m³** (en lugar de 25), la carga pesa 2.5 Ton (proporcional).  
  Peso total = Tara + Peso carga(5 m³) = 2.5 Ton + 2.5 Ton = 5 Ton.

---

## 5. Mantenimientos y Disponibilidad de la Flota

### 5.1 Mantenimiento Preventivo

- Ocurre **cada 2 meses** y **dura 24 h** (día completo).
- Formato de archivo: `mantpreventivo` aaaammdd:TTNN
- Ej: `20250401:TA01` → Camión tipo TA, unidad 01, en mantenimiento el 01/04/2025.
- Camión **no disponible** desde las 00:00 hasta las 23:59 de ese día.
- Si la unidad está en ruta al inicio, debe **volver** al almacén de inmediato (considerar que sale de operación a esa hora).

### 5.2 Averías (Mantenimiento Correctivo)

- **Incidentes** registrados en un archivo `averias.txt` o vía interfaz: tt_######_ti
- `tt`: turno (T1, T2, T3)
- `######`: código de la unidad (ej. TA01, TD03, etc.)
- `ti`: tipo de incidente (TI1, TI2, TI3)
- **Tipos de incidente**:
1. **Tipo 1** (e.g., llanta baja):
    - Inmoviliza 2 horas en ese nodo.
    - Se repara en el sitio y puede continuar.
2. **Tipo 2** (e.g., motor obstruido):
    - Inmoviliza 2 horas en ese nodo.
    - Luego queda inoperativo **un turno completo** en taller:
        - Si ocurre en T1 (día A), disponible en T3 (día A).
        - Si ocurre en T2 (día A), disponible en T1 (día A+1).
        - Si ocurre en T3 (día A), disponible en T2 (día A+1).
    - Después debe regresar al almacén.
3. **Tipo 3** (e.g., choque):
    - Inmoviliza 4 horas en ese nodo.
    - Inoperativo **un día completo**:
        - Disponible en T1 del día A+3, sin importar el turno en que ocurrió el choque.
    - Después regresa al almacén.

- **Trasvase de carga**: otra unidad puede recoger parcial o totalmente la carga si sucede una avería.
- **En el escenario “colapso”**, no aplican las averías.

---

## 6. Pedidos y Archivo de Ventas

- Formato de archivo mensual: `ventas2025mm`
  ##d##h##m:posX,posY,c-idCliente,m3,hLímite
- - Ejemplo: `11d13h31m:45,43,c-167,9m3,36h`
- Pedido llega el día 11 a las 13:31.
- El cliente se ubica en (45, 43).
- Pide 9 m³ con límite 36 horas (desde la hora del pedido).
- Los pedidos son **24 x 7** y se deben **cumplir** dentro del plazo.

---

## 7. Objetivos Específicos para la IA

1. **Planificador y Replanificador de Rutas**:
- Asignar camiones a rutas para **cumplir** con todos los pedidos sin **incumplimientos**.
- Minimizar el **consumo de combustible** (basado en distancias y peso combinado).
- Respetar:
    - **Capacidad** de cada camión.
    - **Mantenimientos** (preventivos y correctivos).
    - **Bloqueos** de calles (mapa y horarios).
    - Tiempos de entrega.

2. **Monitoreo en Tiempo Real (Visualizador)**:
- Mostrar un **mapa** con posiciones y rutas actuales.
- Incluir información de **rendimiento** (consumo, tiempos, estado de cada camión, etc.).

3. **Escenarios**:
- **Día a día**.
- **Simulación semanal** (20 a 50 min. de ejecución).
- **Simulación de colapso** (cuando la demanda no puede satisfacerse).

4. **Parámetros, Restricciones y Métricas**:
- **Función objetivo / Fitness**: minimización de combustible + cumplimiento de tiempos.
- **Penalizaciones** por exceder capacidad, ignorar mantenimientos, entrar en rutas bloqueadas, etc.
- **Evaluar** y **comparar** el **Algoritmo Genético** vs. **Algoritmo de Hormigas** en:
    - Tiempo de convergencia.
    - Calidad de la solución (combustible consumido, pedidos cumplidos).
    - Estabilidad frente a averías y bloqueos.

---

## 8. Instrucciones para el Modelo de IA

1. **Analizar y Representar**:
- Modelo de la ciudad como **nodos** (0 ≤ X ≤ 70, 0 ≤ Y ≤ 50).
- Almacenes centrales/intermedios como nodos fijos.
- **Carreteras bloqueadas** según el archivo `aaaamm.bloqueadas`.
- **Pedidos** como demanda de GLP con límite de entrega.
- **Flota** con mantenimientos y averías.

2. **Proponer** (o “aprender a proponer”) **Codificaciones**:
- **Algoritmo Genético (AG)**:
    - Estructura de cromosomas (secuencia de visitas, asignación de camiones, etc.).
    - Operadores (selección, cruce, mutación).
- **Algoritmo de Hormigas (ACO)**:
    - Modelar nodos, feromonas, heurísticas de visibilidad.
    - Mecanismos de **evaporación** y **depósito de feromonas**.

3. **Diseñar la Función Objetivo**:
- Minimizar **consumo total** y **tiempo de entrega**.
- Incluir **restricciones** (bloqueos, capacidad de camiones, disponibilidad por mantenimientos y averías).

4. **Gestionar** Incidentes (averías) y Mantenimientos:
- Decidir si replanificar en **tiempo real** cuando ocurre una avería.
- Incorporar las reglas de inmovilización y taller.
- Permitir el trasvase de carga a otra unidad si conviene.

5. **Mostrar Resultados**:
- Planes de ruta para cada camión, por orden de entregas.
- Consumo de combustible estimado.
- Tiempos de arribo a cada cliente.
- Estado de cada camión (disponible, en mantenimiento, averiado, etc.).
- Detección de **punto de colapso** (escenario 3).

---

## 9. Formato y Uso de Este Prompt

1. **Entrega** este bloque de información o secciones separadas como “contexto” al **modelo de IA** en tu entorno de entrenamiento.
2. **Permite** que el modelo genere representaciones internas o proponga soluciones (rutas optimizadas, heurísticas de asignación).
3. **Revisa y Ajusta** resultados, verificando:
- Fe factible la ruta (sin pasar por bloqueos).
- Respetan las horas límite y las capacidades de los camiones.
- Se minimiza el consumo según la fórmula.

---

### Resumen

Este **prompt integral** expone:
- El **problema de logística** de PLG (distribución de GLP en una **rejilla 70×50 km**, con **almacenes fijos**, **pedidos** y **restricciones de tiempo**).
- Las **condiciones de bloqueos**, mantenimientos y averías.
- La **meta**: **planificar y replanificar** rutas optimizadas con **metaheurísticas** (AG y ACO), minimizando **consumo** y garantizando **puntualidad**.

**El objetivo** es que la IA aprenda, proponga y mejore algoritmos de optimización, cumpliendo los requisitos operativos y normativos establecidos.


