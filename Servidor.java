package chat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Programa Servidor del chat bidireccional con sockets TCP.
 *
 * Responsabilidades:
 *  - Abrir el puerto de escucha y quedar atento a los llamados de los clientes.
 *  - Registrar a cada usuario que se identifica (lista de conectados).
 *  - Devolver al cliente la lista de usuarios conectados.
 *  - Reenviar los mensajes entre los dos usuarios que conversan.
 *  - Marcar como "abandonó" al usuario que envía "chao".
 *
 * Uso: java chat.Servidor [puerto]      (puerto por defecto: 59420)
 */
public class Servidor {

    public static final int PUERTO_POR_DEFECTO = 59420;

    /** Usuarios conectados: nombre -> manejador de su conexión. */
    private final Map<String, ManejadorCliente> conectados = new ConcurrentHashMap<>();

    /** Log del servidor: historial de eventos (conexiones y abandonos). */
    private final List<String> log = Collections.synchronizedList(new ArrayList<>());

    private final int puerto;

    public Servidor(int puerto) {
        this.puerto = puerto;
    }

    public static void main(String[] args) {
        int puerto = PUERTO_POR_DEFECTO;
        if (args.length > 0) {
            puerto = Integer.parseInt(args[0]);
        }
        new Servidor(puerto).iniciar();
    }

    /** Abre el ServerSocket y atiende a cada cliente en un hilo independiente. */
    public void iniciar() {
        try (ServerSocket serverSocket = new ServerSocket(puerto)) {
            registrarLog("Servidor iniciado y contestando OK (puerto " + puerto + ")");
            while (true) {
                Socket socket = serverSocket.accept();          // espera bloqueante
                ManejadorCliente manejador = new ManejadorCliente(socket, this);
                new Thread(manejador).start();                 // un hilo por cliente
            }
        } catch (IOException e) {
            System.err.println("Error en el servidor: " + e.getMessage());
        }
    }

    /** Registra un usuario. Devuelve false si el nombre ya está en uso. */
    public synchronized boolean registrarUsuario(String nombre, ManejadorCliente manejador) {
        if (conectados.containsKey(nombre.toLowerCase())) {
            return false;
        }
        conectados.put(nombre.toLowerCase(), manejador);
        registrarLog("Usuario \"" + nombre + "\" conectado");
        mostrarConectados();
        return true;
    }

    /** Marca al usuario como que abandonó el chat y lo retira de la lista. */
    public synchronized void retirarUsuario(String nombre) {
        if (nombre != null && conectados.remove(nombre.toLowerCase()) != null) {
            registrarLog("El usuario \"" + nombre + "\" abandonó");
            mostrarConectados();
        }
    }

    public ManejadorCliente buscar(String nombre) {
        return nombre == null ? null : conectados.get(nombre.toLowerCase());
    }

    /** Lista de usuarios conectados, excepto el que la solicita. */
    public List<String> listarUsuarios(String excepto) {
        List<String> nombres = new ArrayList<>();
        for (ManejadorCliente m : conectados.values()) {
            if (m.getNombre() != null && (excepto == null || !m.getNombre().equalsIgnoreCase(excepto))) {
                nombres.add(m.getNombre());
            }
        }
        Collections.sort(nombres, String.CASE_INSENSITIVE_ORDER);
        return nombres;
    }

    private void registrarLog(String evento) {
        String linea = "[" + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] " + evento;
        log.add(linea);
        System.out.println(linea);
    }

    private void mostrarConectados() {
        List<String> nombres = listarUsuarios(null);
        System.out.println("    Conectados (" + nombres.size() + "): " + String.join(", ", nombres));
    }
}
