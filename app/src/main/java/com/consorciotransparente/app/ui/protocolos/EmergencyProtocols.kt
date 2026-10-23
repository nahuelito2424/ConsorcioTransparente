package com.consorciotransparente.app.ui.protocolos

/**
 * HU-07: guía general de qué hacer ante cada tipo de siniestro, más los teléfonos de
 * emergencia nacionales. No es un PDF oficial descargado (Defensa Civil GCBA, SAME):
 * no hay forma de traer y verificar esos documentos reales desde acá, así que el
 * contenido es texto redactado a partir de lineamientos generales reconocidos — se lo
 * deja explícito en la UI para no hacerlo pasar por protocolo oficial. Por eso mismo no
 * hay `data/model`/Repository: es contenido fijo, bundleado con la app (funciona sin
 * conexión por definición, no por caché), igual para cualquier edificio.
 */
data class EmergencyProtocol(
    val id: String,
    val icon: String,
    val title: String,
    val steps: List<String>
)

data class EmergencyContact(val name: String, val phone: String)

val emergencyContacts = listOf(
    EmergencyContact("Bomberos", "100"),
    EmergencyContact("SAME (emergencias médicas)", "107"),
    EmergencyContact("Policía", "911"),
    EmergencyContact("Defensa Civil", "103")
)

val emergencyProtocols = listOf(
    EmergencyProtocol(
        id = "incendio",
        icon = "🔥",
        title = "Incendio",
        steps = listOf(
            "Mantené la calma, no corras.",
            "No uses el ascensor — usá siempre la escalera.",
            "Si hay humo, desplazate agachado/a: el aire es más limpio cerca del piso.",
            "Cerrá las puertas a tu paso para frenar el fuego, sin trabarlas con llave.",
            "Si tu ropa se prende fuego: no corras, tirate al piso y rodá.",
            "Si quedás atrapado/a, tapá las rendijas de la puerta con telas (mojadas si tenés agua a mano) y hacé señales desde la ventana.",
            "Punto de encuentro: la vereda frente al edificio, lejos de la fachada.",
            "Llamá a Bomberos apenas estés a salvo."
        )
    ),
    EmergencyProtocol(
        id = "sismo",
        icon = "🌎",
        title = "Sismo",
        steps = listOf(
            "Durante el movimiento, alejate de ventanas, espejos y muebles que puedan caer.",
            "Si podés, ubicate bajo un marco de puerta resistente o un mueble firme, cubriendo cabeza y cuello.",
            "No uses el ascensor.",
            "Esperá a que termine el movimiento antes de salir — no lo hagas corriendo durante el sismo.",
            "Después, bajá por la escalera con calma, fijándote que no haya vidrios rotos ni cables sueltos.",
            "Punto de encuentro: un espacio abierto, lejos de fachadas y cables."
        )
    ),
    EmergencyProtocol(
        id = "corte_luz",
        icon = "💡",
        title = "Corte de luz",
        steps = listOf(
            "Verificá si el corte es sólo del edificio o de toda la zona (preguntale a un vecino o mirá si hay luz en la calle).",
            "Si es del edificio, reportalo desde Más → Tickets para que el Encargado/Administrador se entere.",
            "No uses velas sin supervisión — preferí una linterna.",
            "Si quedás atrapado/a en el ascensor, usá el botón de alarma o el intercomunicador; no fuerces las puertas.",
            "Desenchufá los equipos electrónicos sensibles para evitar daños cuando vuelva la luz."
        )
    ),
    EmergencyProtocol(
        id = "inundacion",
        icon = "🌊",
        title = "Inundación",
        steps = listOf(
            "No ingreses a sótanos, cocheras ni espacios bajo nivel de calle si está entrando agua.",
            "Cortá la luz y el gas de tu unidad si el agua puede llegar a tomacorrientes o artefactos.",
            "No camines ni manejes por calles anegadas.",
            "Subí objetos de valor y equipos eléctricos a zonas altas si el agua empieza a entrar al edificio.",
            "Mantenete informado por los canales oficiales de Defensa Civil de tu zona."
        )
    )
)
