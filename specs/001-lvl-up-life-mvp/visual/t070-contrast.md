# Apariencia y contraste de Home y clases — T070

Pixel_6a_API_30, paleta clara Material3 usada por la app, escala de fuente normal.
Fuentes y fondos se obtienen de los colores efectivos del componente y se confirma
su presencia en los píxeles de texto/fondo de cada captura. Se excluyen los bordes
antialias de los glifos del cálculo; no se promedian colores del dibujo.

Razón: `(Lmayor + 0,05) / (Lmenor + 0,05)` con luminancia relativa sRGB, umbral
de linealización0,04045 y coeficientes0,2126/0,7152/0,0722. Se compara contra4,5
sin redondear; los valores de esta tabla se redondean solo para presentación.
Definiciones normativas: [razón de contraste](https://www.w3.org/TR/WCAG22/#dfn-contrast-ratio)
y [luminancia relativa](https://www.w3.org/TR/WCAG22/#dfn-relative-luminance).

| Texto | Fondo | Razón medida | Usos verificados |
|---|---|---:|---|
| #1D1B20 | #FEF7FF | 16,23:1 | Clase, etapa, nivel, XP y mensajes Loading/Stale/Error |
| #1D1B20 | #E6E0E9 | 13,17:1 | Estado bloqueado/desbloqueado, nivel mínimo y actualización deshabilitada |
| #FFFFFF | #6750A4 | 6,44:1 | Actualizar, Clases, Volver y Reintentar |

Los17 pares lógicos y recuentos de píxeles observados están en
[t070-contrast.jsonl](t070-contrast.jsonl). AppearanceContrastTest verifica los
colores realmente dibujados, no solo constantes de una paleta. Las capturas
seleccionadas inspeccionadas están en [t070-appearance-contrast.png](t070-appearance-contrast.png).

La cabecera muestra imagen completa88dp, clase, etapa y nivel recibido; conserva
los seis atributos y controles. Las tarjetas de catálogo muestran imágenes completas
160dp y comunican el bloqueo con texto/semántica, sin depender solo del color.
El botón deshabilitado mantiene el objetivo de legibilidad del proyecto, aunque
[WCAG 1.4.3](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html)
exceptúa componentes inactivos. La deshabilitación se comunica también semánticamente
y junto al mensaje de actualización.

Alcance: estos pares de Home y catálogo, en esta paleta/configuración. No acredita
conformidad WCAG global, paleta oscura, todas las escalas de fuente o todos los flujos;
esa revisión transversal corresponde a T079. Los textos internos de las láminas
ilustradas no sustituyen la información funcional de clase/etapa, presentada como
texto nativo y descripción accesible. No se modificaron los76 PNG originales.
