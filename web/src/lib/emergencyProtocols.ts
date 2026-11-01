// Mismo contenido que EmergencyProtocols.kt (Android, HU-07): no es un PDF oficial
// descargado (Defensa Civil GCBA, SAME) — texto redactado a partir de lineamientos
// generales, con el disclaimer correspondiente en la UI. Contenido fijo, igual para
// cualquier edificio, sin Firestore de por medio (funciona sin conexión por definición).

export interface EmergencyProtocol {
  id: string;
  icon: string;
  title: string;
  steps: string[];
}

export interface EmergencyContact {
  name: string;
  phone: string;
}

export const emergencyContacts: EmergencyContact[] = [
  { name: "Bomberos", phone: "100" },
  { name: "SAME (emergencias médicas)", phone: "107" },
  { name: "Policía", phone: "911" },
  { name: "Defensa Civil", phone: "103" },
];

export const emergencyProtocols: EmergencyProtocol[] = [
  {
    id: "incendio",
    icon: "🔥",
    title: "Incendio",
    steps: [
      "Mantené la calma, no corras.",
      "No uses el ascensor — usá siempre la escalera.",
      "Si hay humo, desplazate agachado/a: el aire es más limpio cerca del piso.",
      "Cerrá las puertas a tu paso para frenar el fuego, sin trabarlas con llave.",
      "Si tu ropa se prende fuego: no corras, tirate al piso y rodá.",
      "Si quedás atrapado/a, tapá las rendijas de la puerta con telas (mojadas si tenés agua a mano) y hacé señales desde la ventana.",
      "Punto de encuentro: la vereda frente al edificio, lejos de la fachada.",
      "Llamá a Bomberos apenas estés a salvo.",
    ],
  },
  {
    id: "sismo",
    icon: "🌎",
    title: "Sismo",
    steps: [
      "Durante el movimiento, alejate de ventanas, espejos y muebles que puedan caer.",
      "Si podés, ubicate bajo un marco de puerta resistente o un mueble firme, cubriendo cabeza y cuello.",
      "No uses el ascensor.",
      "Esperá a que termine el movimiento antes de salir — no lo hagas corriendo durante el sismo.",
      "Después, bajá por la escalera con calma, fijándote que no haya vidrios rotos ni cables sueltos.",
      "Punto de encuentro: un espacio abierto, lejos de fachadas y cables.",
    ],
  },
  {
    id: "corte_luz",
    icon: "💡",
    title: "Corte de luz",
    steps: [
      "Verificá si el corte es sólo del edificio o de toda la zona (preguntale a un vecino o mirá si hay luz en la calle).",
      "Si es del edificio, reportalo desde Tickets para que el Encargado/Administrador se entere.",
      "No uses velas sin supervisión — preferí una linterna.",
      "Si quedás atrapado/a en el ascensor, usá el botón de alarma o el intercomunicador; no fuerces las puertas.",
      "Desenchufá los equipos electrónicos sensibles para evitar daños cuando vuelva la luz.",
    ],
  },
  {
    id: "inundacion",
    icon: "🌊",
    title: "Inundación",
    steps: [
      "No ingreses a sótanos, cocheras ni espacios bajo nivel de calle si está entrando agua.",
      "Cortá la luz y el gas de tu unidad si el agua puede llegar a tomacorrientes o artefactos.",
      "No camines ni manejes por calles anegadas.",
      "Subí objetos de valor y equipos eléctricos a zonas altas si el agua empieza a entrar al edificio.",
      "Mantenete informado por los canales oficiales de Defensa Civil de tu zona.",
    ],
  },
];

export const protocolsDisclaimer =
  "Guía general de qué hacer ante cada siniestro, pensada para funcionar sin conexión. No reemplaza las instrucciones de Defensa Civil ni de los servicios de emergencia.";
