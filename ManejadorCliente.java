package chat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Atiende, en un hilo propio, la conexión de un cliente con el servidor.
 *
 * Protocolo de aplicación (mensajes de texto, una línea cada uno, campos separados por "|"):
 *
 *   Cliente -> Servidor                 Servidor -> Cliente
 *   LOGIN|nombre                        OK|mensaje   /   ERROR|motivo
 *   LISTA                               USUARIOS|u1,u2,u3
 *   CHAT|destino                        OK|mensaje   /   ERROR|motivo
 *   MSG|texto                           MSG|origen|texto   (al destinatario)
 *   CHAO                                INFO|texto         (avisos del servidor)
 */
public class ManejadorCliente implements Runnable {

    private final Socket socket;
    private final Servidor servidor;
    private BufferedReader entrada;
    private PrintWriter salida;
    private String nombre;
    private volatile String companero;   // usuario con el que conversa

    public ManejadorCliente(Socket socket, Servidor servidor) {
        this.socket = socket;
        this.servidor = servidor;
    }

    public String getNombre() {
        return nombre;
    }

    @Override
    public void run() {
        try {
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            salida = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);

            if (!autenticar()) {
                return;
            }
            enviarLista();

            String linea;
            while ((linea = entrada.readLine()) != null) {
                if (!procesar(linea)) {
                    break;              // el usuario envió "chao"
                }
            }
        } catch (IOException e) {
            // conexión cerrada abruptamente por el cliente
        } finally {
            desconectar();
        }
    }

    /** Paso 1: el cliente se identifica con LOGIN|nombre. */
    private boolean autenticar() throws IOException {
        String linea;
        while ((linea = entrada.readLine()) != null) {
            String[] partes = linea.split("\\|", 2);
            if (partes.length == 2 && partes[0].equals("LOGIN") && !partes[1].trim().isEmpty()) {
                String propuesto = partes[1].trim();
                nombre = propuesto;
                if (servidor.registrarUsuario(propuesto, this)) {
                    enviar("OK|Bienvenido " + nombre + ", estás conectado al servidor");
                    return true;
                }
                nombre = null;
                enviar("ERROR|El nombre \"" + propuesto + "\" ya está en uso, escriba otro");
            } else {
                enviar("ERROR|Debe identificarse primero (LOGIN|nombre)");
            }
        }
        return false;
    }

    /** Procesa un comando. Devuelve false cuando la sesión debe terminar. */
    private boolean procesar(String linea) {
        String[] partes = linea.split("\\|", 2);
        String comando = partes[0];
        String dato = partes.length > 1 ? partes[1] : "";

        switch (comando) {
            case "LISTA":
                enviarLista();
                return true;
            case "CHAT":
                elegirCompanero(dato.trim());
                return true;
            case "MSG":
                if (dato.trim().equalsIgnoreCase("chao")) {
                    if (companero != null) {
                        reenviar(dato);  // el compañero también ve el "chao"
                    }
                    return false;
                }
                reenviar(dato);
                return true;
            case "CHAO":
                return false;
            default:
                enviar("ERROR|Comando no reconocido: " + comando);
                return true;
        }
    }

    private void enviarLista() {
        List<String> otros = servidor.listarUsuarios(nombre);
        enviar("USUARIOS|" + String.join(",", otros));
    }

    /** El usuario elige con quién conversar; el elegido queda emparejado si está libre. */
    private void elegirCompanero(String destino) {
        ManejadorCliente otro = servidor.buscar(destino);
        if (otro == null) {
            enviar("ERROR|El usuario \"" + destino + "\" no está conectado");
            return;
        }
        if (otro == this) {
            enviar("ERROR|No puede chatear consigo mismo");
            return;
        }
        companero = otro.getNombre();
        enviar("OK|Ahora conversa con " + companero + ". Escriba \"chao\" para salir");
        if (otro.companero == null) {
            otro.companero = nombre;
            otro.enviar("INFO|" + nombre + " inició una conversación con usted. Ya puede responderle");
        } else if (!otro.companero.equalsIgnoreCase(nombre)) {
            otro.enviar("INFO|" + nombre + " quiere conversar con usted (use /chat " + nombre + ")");
        }
    }

    private void reenviar(String texto) {
        if (companero == null) {
            enviar("ERROR|Primero elija un usuario con /chat <nombre>");
            return;
        }
        ManejadorCliente otro = servidor.buscar(companero);
        if (otro == null) {
            enviar("ERROR|" + companero + " ya no está conectado");
            companero = null;
            return;
        }
        otro.enviar("MSG|" + nombre + "|" + texto);
    }

    synchronized void enviar(String mensaje) {
        if (salida != null) {
            salida.println(mensaje);
        }
    }

    private void desconectar() {
        if (nombre != null) {
            servidor.retirarUsuario(nombre);
            // avisar a quien conversaba con este usuario
            for (String otroNombre : servidor.listarUsuarios(nombre)) {
                ManejadorCliente otro = servidor.buscar(otroNombre);
                if (otro != null && nombre.equalsIgnoreCase(otro.companero)) {
                    otro.companero = null;
                    otro.enviar("INFO|" + nombre + " abandonó el chat");
                }
            }
        }
        try {
            socket.close();
        } catch (IOException ignorada) {
            // nada que hacer
        }
    }
}
