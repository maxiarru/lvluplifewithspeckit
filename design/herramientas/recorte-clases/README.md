# Recorte sin fondo de las imágenes de clases

Herramienta usada en la feature 002 para convertir las 76 láminas originales de clases
(1122×1402, fondo de pergamino, título, vistas extra y accesorios) en recortes con fondo
transparente que conservan solo la figura principal.

No requiere dependencias: compila `Cutout.cs` con `Add-Type` de Windows PowerShell 5.1 y
usa `System.Drawing`.

## Cómo funciona

1. Toma como color de fondo la mediana del borde de la imagen.
2. Marca como fondo candidato el pergamino, sus sombras más oscuras del mismo tono, los
   paneles casi blancos y las zonas de `overrides.csv`.
3. Rellena desde el borde a través de esos candidatos.
4. Conserva el componente más grande (la figura) y los fragmentos chicos que la tocan.
5. Quita los huecos grandes de pergamino encerrados, suaviza un píxel de borde y recorta
   con margen.

`overrides.csv` define, por imagen, zonas excluidas (`excl`, en fracciones x0,y0,x1,y1) y
colores de paneles a tratar como fondo (`sample`, punto x,y).

## Uso

Los originales están en el historial de git (commit 7c2fc30). Para regenerar:

```powershell
# exportar originales a una carpeta temporal y luego:
powershell -NoProfile -File run.ps1 -Src <carpeta-originales> -OutDir <salida> -Sheet <hoja.png> -Cols 13
```

La hoja de contacto compone los recortes sobre el fondo de la app para revisarlos.
