---
name: conventional-commit
description: Redacta mensajes de commit según Conventional Commits. Se usa al preparar un commit o al solicitar redactar, revisar o corregir su mensaje a partir de los cambios del proyecto.
---

# Conventional Commit

Antes de elegir el tipo, el scope y la descripción, mirá qué cambió realmente. Revisá `git status --short`, `git diff --cached` y, cuando corresponda, `git diff` y el contenido de los archivos nuevos. Distinguí los cambios preparados para el commit de los demás cambios del repositorio; describí únicamente los que integran el commit solicitado. Si no tenés acceso al diff, pedí los cambios necesarios para redactar el mensaje sin inventarlos.

## Formato

```text
tipo(scope): descripción en imperativo
```

El scope es opcional. Si no aporta claridad, usá `tipo: descripción`.

Elegí el tipo según el propósito del cambio:

- `feat`: funcionalidad nueva.
- `fix`: arreglo de un error.
- `docs`: documentación.
- `refactor`: reorganización del código sin cambiar su comportamiento.
- `test`: incorporación o modificación de pruebas.
- `chore`: mantenimiento del proyecto, herramientas o configuración.

El scope indica la parte del proyecto afectada, por ejemplo `auth`, `tickets` o `prd`.

Escribí la descripción en español, en imperativo, en minúscula y sin punto final. Usá verbos de acción como `agregar`, `rechazar` o `aclarar`, siguiendo los ejemplos. Mantené la descripción corta, con un máximo de 72 caracteres; procurá que el encabezado completo también entre en ese límite.

Elegí el tipo y la descripción por el cambio observado, no solamente por el nombre de los archivos. Si hay cambios independientes, proponé mensajes separados en lugar de ocultarlos bajo una descripción genérica.

## Ejemplos

```text
feat(auth): agregar validación de email en el registro
fix(tickets): rechazar tickets con asunto vacío
docs(prd): aclarar criterio de control de acceso
```

## Entrega

Si se pide un mensaje, devolvé el mensaje listo para usar. Redactar un mensaje no implica crear un commit: ejecutá operaciones de Git que modifican el repositorio únicamente dentro del alcance solicitado por el usuario.
