import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

final class Box {
  private final UUID id;
  private final InetSocketAddress controllerAddress;
  private final Duration connectTimeout;
  private Socket connection;

  private Box(
    UUID id,
    InetSocketAddress controllerAddress,
    Duration connectTimeout
  ) {
    this.id = id;
    this.connectTimeout = connectTimeout;
    this.controllerAddress = controllerAddress;
  }

  public void start() throws IOException {
    connect();
    try {
      advertise();
      accept();
    } finally {
      connection.close();
    }
  }

  private void connect() throws IOException {
    connection = new Socket();
    connection.connect(controllerAddress, (int) connectTimeout.toMillis());
  }

  public void advertise() throws IOException {
    var bytes = ByteBuffer.allocate(Long.SIZE * 2);
    bytes.putLong(id.getMostSignificantBits());
    bytes.putLong(id.getLeastSignificantBits());
    connection.getOutputStream().write(bytes.flip().array());
  }

  public void accept() throws IOException {
    var input = connection.getInputStream();
    byte[] buffer = new byte[4096];
    while (!Thread.currentThread().isInterrupted() && connection.isConnected()) {
      int read = input.read(buffer);
      if (read == -1) {
        return;
      }
      if (read != 0) {
        System.out.write(buffer, 0, read);
      }
    }
  }

  public static void main(String[] arguments) throws IOException {
    validateArguments(arguments);
    var box = createBoxFromArguments(arguments);
    Files.writeString(Path.of(box.id.toString() + ".box.txt"), box.id.toString());
    try {
      box.start();
    } catch (IOException failure) {
      System.err.println("error occurred while running box");
      failure.printStackTrace();
    }
  }

  private static void validateArguments(String[] arguments) {
    if (arguments.length != addressIndex + 1) {
      System.out.println("usage: box <id> <controllerAddress>");
      System.exit(-1);
    }
  }

  private static final int idIndex = 0;
  private static final int addressIndex = 1;

  private static Box createBoxFromArguments(String[] arguments) {
    var id = UUID.fromString(arguments[idIndex]);
    var address = parseAddress(arguments[addressIndex]);
    return new Box(id, address, Duration.ofMillis(5000));
  }

  private static InetSocketAddress parseAddress(String input) {
    int portSeparator = input.indexOf(':');
    if (portSeparator == -1) {
      throw new IllegalArgumentException("port missing: " + input);
    }
    int port = Integer.parseInt(input.substring(portSeparator + 1));
    var host = input.substring(0, portSeparator);
    return host.isEmpty()
      ? new InetSocketAddress(port)
      : new InetSocketAddress(host, port);
  }
}