package chat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Programa Cliente del chat bidireccional con sockets TCP (versión por consola).
 *
 * Al arrancar pide los datos de conexión: IP del servidor, puerto y nombre de usuario.
 * Luego muestra los usuarios conectados para elegir con quién conversar.
 *
 * Comandos durante la conversación:
 *   /lista            vuelve a pedir la lista de usuarios conectados
 *   /chat <nombre>    elige (o cambia) el usuario con quien conversar
 *   chao              abandona el chat y termina el programa
 *   cualquier texto   se envía al usuario elegido
 *
 * Uso: java chat.Cliente
 */
public class Cliente {

    private static volatile boolean activo = true;

    public static void main(String[] args) {
        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        try {
            System.out.print("IP del Servidor [127.0.0.1]: ");
            String ip = leer(teclado, "127.0.0.1");
            System.out.print("Puerto de la conexión [" + Servidor.PUERTO_POR_DEFECTO + "]: ");
            int puerto = Integer.parseInt(leer(teclado, String.valueOf(Servidor.PUERTO_POR_DEFECTO)));

            try (Socket socket = new Socket(ip, puerto);
                 BufferedReader entrada = new BufferedReader(
                         new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter salida = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {

                // 1. Identificación ante el servidor
                String respuesta;
                do {
                    System.out.print("Escriba su nombre: ");
                    String nombre = leer(teclado, "");
                    salida.println("LOGIN|" + nombre);
                    respuesta = entrada.readLine();
                    if (respuesta == null) {
                        System.out.println("El servidor cerró la conexión.");
                        return;
                    }
                    mostrar(respuesta);
                } while (respuesta.startsWith("ERROR"));

                // 2. Hilo que escucha al servidor mientras el usuario escribe (chat bidireccional)
                Thread receptor = new Thread(() -> {
                    try {
                        String linea;
                        while ((linea = entrada.readLine()) != null) {
                            mostrar(linea);
                        }
                    } catch (IOException e) {
                        // socket cerrado
                    }
                    if (activo) {
                        System.out.println("Conexión con el servidor terminada.");
                        System.exit(0);
                    }
                });
                receptor.setDaemon(true);
                receptor.start();

                System.out.println("Elija un usuario con /chat <nombre>  |  /lista para actualizar  |  chao para salir");

                // 3. Lectura de teclado y envío al servidor
                String linea;
                while ((linea = teclado.readLine()) != null) {
                    linea = linea.trim();
                    if (linea.isEmpty()) {
                        continue;
                    }
                    if (linea.equalsIgnoreCase("chao")) {
                        activo = false;
                        salida.println("MSG|chao");
                        System.out.println("Abandonó el chat. Hasta pronto.");
                        break;
                    } else if (linea.equalsIgnoreCase("/lista")) {
                        salida.println("LISTA");
                    } else if (linea.toLowerCase().startsWith("/chat ")) {
                        salida.println("CHAT|" + linea.substring(6).trim());
                    } else {
                        salida.println("MSG|" + linea);
                    }
                }
                activo = false;
            }
        } catch (IOException e) {
            System.err.println("No fue posible conectarse con el servidor: " + e.getMessage());
        } catch (NumberFormatException e) {
            System.err.println("El puerto debe ser un número.");
        }
    }

    private static String leer(BufferedReader teclado, String porDefecto) throws IOException {
        String valor = teclado.readLine();
        return (valor == null || valor.trim().isEmpty()) ? porDefecto : valor.trim();
    }

    /** Traduce los mensajes del protocolo a texto legible para el usuario. */
    private static void mostrar(String linea) {
        String[] p = linea.split("\\|", 3);
        switch (p[0]) {
            case "OK":
                System.out.println("[Servidor] " + (p.length > 1 ? p[1] : "OK"));
                break;
            case "ERROR":
                System.out.println("[Error] " + (p.length > 1 ? p[1] : ""));
                break;
            case "INFO":
                System.out.println("[Aviso] " + (p.length > 1 ? p[1] : ""));
                break;
            case "USUARIOS":
                String lista = p.length > 1 ? p[1] : "";
                System.out.println("[Servidor] Usuarios conectados: "
                        + (lista.isEmpty() ? "(ninguno más por ahora)" : lista.replace(",", ", ")));
                break;
            case "MSG":
                System.out.println(p[1] + " dice: " + (p.length > 2 ? p[2] : ""));
                break;
            default:
                System.out.println(linea);
        }
    }
}
