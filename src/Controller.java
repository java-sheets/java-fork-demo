import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

final class Controller {
  private final int port;
  private final String boxBinaryPath;
  private final Executor executor;
  private ServerSocket socket;

  private Controller(int port, String boxBinaryPath, Executor executor) {
    this.port = port;
    this.boxBinaryPath = boxBinaryPath;
    this.executor = executor;
  }

  public void start() throws IOException {
    socket = new ServerSocket();
    socket.bind(new InetSocketAddress(port));
    try {
      listen();
    } finally {
      socket.close();
    }
  }

  private void listen() throws IOException {
    while (!Thread.currentThread().isInterrupted() && socket.isBound()) {
      var client = socket.accept();
      executor.execute(() -> {
        try {
          accept(client);
        } catch (IOException failure) {
          System.err.println(
            "encountered error while serving " + client.getRemoteSocketAddress()
          );
          failure.printStackTrace();
        }
      });
    }
  }

  private void accept(Socket client) throws IOException {
    var input = client.getInputStream();
    byte[] initialPacket = input.readNBytes(Long.SIZE * 2);
    var id = readId(initialPacket);
    System.err.println("accepted client with id " + id);
    useBox(client);
    client.close();
  }

  private static final String[] firstNames = {
    "Leonard", "Lionel", "Elton", "Nina", "Art", "Tracy", "Freddy",
  };

  private static final String[] lastNames = {
    "Cohen", "Richie", "John", "Simone", "Garfunkel", "Chapman", "Mercury"
  };

  private void useBox(Socket client) throws IOException {
    var payload = createPayload();
    client.getOutputStream().write(payload);
  }

  private byte[] createPayload() {
    var random = ThreadLocalRandom.current();
    var firstName = firstNames[random.nextInt(firstNames.length)];
    var lastName = lastNames[random.nextInt(lastNames.length)];
    var fullName = firstName + " " + lastName;
    return fullName.getBytes(StandardCharsets.UTF_8);
  }

  private UUID readId(byte[] bytes) {
    var buffer = ByteBuffer.wrap(bytes);
    return new UUID(
      buffer.getLong(),
      buffer.getLong()
    );
  }

  public void startBox(UUID id) throws IOException {
    System.err.println("starting box " + id);
    var process = startBoxProcess();
    executor.execute(() -> {
      redirectBoxOutput(id, process);
      redirectBoxErrors(id, process);
    });
  }

  private void redirectBoxOutput(UUID id, Process process) {
    var prefix = "%s: ".formatted(id).getBytes(StandardCharsets.UTF_8);
    var suffix = "\r\n".getBytes(StandardCharsets.UTF_8);
    try {
      redirect(prefix, suffix, process.getInputStream(), System.out);
    } catch (IOException failedRedirect) {
      System.err.println("encountered error while redirecting " + id);
      failedRedirect.printStackTrace();
    }
  }

  private void redirectBoxErrors(UUID id, Process process) {
    var prefix = "Error in %s: ".formatted(id).getBytes(StandardCharsets.UTF_8);
    var suffix = "\r\n".getBytes(StandardCharsets.UTF_8);
    try {
      redirect(prefix, suffix, process.getErrorStream(), System.out);
    } catch (IOException failedRedirect) {
      System.err.println("encountered error while redirecting " + id);
      failedRedirect.printStackTrace();
    }
  }

  private static final String boxMemory = System.getProperty("box.memory", "2M");

  private Process startBoxProcess() throws IOException {
    var ownAddress = ":" + port;
    return new ProcessBuilder()
      .command(
        "java",
        "-Xmx%s".formatted(boxMemory),
        "-Xms%s".formatted(boxMemory),
        "-XX:+UseSerialGC",
        /* program */ "-jar", boxBinaryPath,
        /* arguments */ UUID.randomUUID().toString(), ownAddress
      ).start();
  }

  private static void redirect(
    byte[] prefix,
    byte[] suffix,
    InputStream input,
    OutputStream output
  ) throws IOException {
    byte[] buffer = new byte[1024];
    int read;
    while ((read = input.read(buffer)) != -1) {
      synchronized (output) {
        output.write(prefix);
        output.write(buffer, 0, read);
        output.write(suffix);
      }
    }
  }

  // All logging is done to System.err since output is redirected to System.out
  public static void main(String[] arguments) throws InterruptedException {
    validateArguments(arguments);
    var executor = Executors.newCachedThreadPool();
    var controller = new Controller(3_1337, arguments[0], executor);
    runController(executor, controller);
    int boxCount = arguments.length > 1 ? Integer.parseInt(arguments[1]) : 3;
    startSomeBoxes(controller, boxCount);
    Thread.sleep(10_000);
    executor.shutdown();
  }

  private static void runController(Executor executor, Controller controller) {
    executor.execute(() -> {
      try {
        controller.start();
      } catch (IOException failure) {
        System.err.println("encountered error in controller");
        failure.printStackTrace();
      }
    });
  }

  private static void startSomeBoxes(Controller controller, int count) {
    try {
      for (int index = 0; index < count; index++) {
        controller.startBox(UUID.randomUUID());
      }
    } catch (IOException failure) {
      System.err.println("failed to start boxes");
    }
  }

  private static void validateArguments(String[] arguments) {
    if (arguments.length < 1) {
      System.err.println("usage: ./run <path-to-box-binary> [box-count]");
      System.exit(-1);
    }
  }
}