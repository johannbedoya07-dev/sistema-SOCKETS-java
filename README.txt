PROYECTO PersistenciaTransaccional - Entrega 1: Chat bidireccional con sockets (Java)

Importar en Eclipse
1. File > New > Java Project > nombre: PersistenciaTransaccional (JRE: jdk-11).
2. Copie la carpeta src/chat dentro de la carpeta src del proyecto y pulse F5 (Refresh).

Ejecutar
1. Clic derecho en Servidor.java > Run As > Java Application  (queda escuchando en el puerto 59420).
2. Clic derecho en Cliente.java > Run As > Java Application  (una vez por cada usuario: poli01, poli02, poli03).
   En la consola del cliente: IP 127.0.0.1, puerto 59420 y su nombre (Enter acepta el valor por defecto).
   Use el botón "Display Selected Console" de Eclipse para pasar entre las consolas.

Comandos del cliente
  /chat <nombre>   elegir usuario para conversar
  /lista           ver usuarios conectados
  chao             abandonar el chat
