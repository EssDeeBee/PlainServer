package org.ser.plainserver;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * The program is a simple server that can send files to the client.
 * The program uses {@link SimpleResponse} to construct responses and
 * sending them to the client; the class contains all the headers
 * and the file itself. Also, the program uses {@link StatusResponse}
 * to store all status responses that are used in the program.
 * All the responses are stored in the html files in the root folder.
 * This approach allows to modify the responses in a convenient way
 * and create a complex responses. The only response that send using
 * no files is 404 Not Found, for cases when the root folder is missing.
 *
 * @author @ser1103.
 * @see SimpleResponse
 * @see StatusResponse
 */
public class SimpleServer {
    /**
     * The server listens on this port.  Note that the port number must
     * be greater than 1024 and lest than 65535.
     */
    private final static int LISTENING_PORT = 8080;

    // HTML file paths for the different responses from the server.
    private final static String NOT_FOUND_HTML = "/404.html";
    private final static String FORBIDDEN_HTML = "/403.html";
    private final static String BAD_REQUEST_HTML = "/400.html";
    private final static String NOT_IMPLEMENTED_HTML = "/501.html";
    private final static String INTERNAL_SERVER_ERROR_HTML = "/500.html";

    // List of supported requests type according to the assigment.
    private static final List<String> SUPPORTED_OPERATIONS =
            List.of("GET");
    private static final List<String> SUPPORTED_HTTP =
            List.of("HTTP/1.1", "HTTP/1.0");

    // Default page if server receives a folder as a requested recourse.
    private static final String DEFAULT_PAGE = "/index.html";

    // Root directory where all the files are stored.
    // There is an important that the path should be relative.
    // The reason that the program finds the current directory
    // and then tries to find the given directory in th current one.
//    private static final String ROOT_DIRECTORY = "resources/static";
    private static final String ROOT_DIRECTORY = "/src/main/resources/static";


    /**
     * Main program opens a server socket and listens for connection
     * requests.  It calls the handleConnection() method to respond
     * to connection requests.  The program runs in an infinite loop,
     * unless an error occurs.
     *
     * @param args ignored
     */
    public static void main(String[] args) throws Exception {
        ServerSocket serverSocket;
        try {
            serverSocket = new ServerSocket(LISTENING_PORT);
        } catch (Exception e) {
            System.out.println("Failed to create listening socket.");
            return;
        }
        System.out.println("Listening on port " + LISTENING_PORT);
        try {
            while (true) {
                Socket connection = serverSocket.accept();
                System.out.println("\nConnection from "
                        + connection.getRemoteSocketAddress());
                ConnectionThread connectionThread = new ConnectionThread(connection);
                connectionThread.start();
            }
        } catch (Exception e) {
            System.out.println("Server socket shut down unexpectedly!");
            System.out.println("Error: " + e);
            System.out.println("Exiting.");
        }
    }


    /**
     * The main method where the a connection being served.
     *
     * @param connection connection with a client
     */
    private static void handleConnection(Socket connection) {

        // try-with-lab11.resources allows to not to think about closing streams.
        // nevertheless as the assigment requires the finally block has been added
        // where the socket connection is closing.
        try (OutputStream outputStream = connection.getOutputStream();
             InputStream inputStream = connection.getInputStream();
             Scanner scanner = new Scanner(inputStream)) {

            String inputLine = null;
            SimpleResponse simpleResponse;
            while (scanner.hasNextLine()) {
                inputLine = scanner.nextLine();
                if (inputLine.length() > 0)
                    break;
            }

            System.out.printf("%s: The request has been received %s\n",
                    LocalDateTime.now(),
                    inputLine);

            try {
                String requestedFileName = getRequestedResource(inputLine);
                String fileName = findFile(requestedFileName).getName();
                String mimeType = getMimeType(fileName);

                simpleResponse = new SimpleResponse()
                        .setContentType(mimeType)
                        .setBody(findFile(requestedFileName))
                        .setStatus(StatusResponse.OK);

            } catch (NoSuchElementException | NullPointerException ex) {
                sendErrorResponse(404, outputStream);
                throw ex;
            } catch (AccessDeniedException ex) {
                sendErrorResponse(403, outputStream);
                throw ex;
            } catch (UnsupportedOperationException ex) {
                sendErrorResponse(501, outputStream);
                throw ex;
            } catch (IllegalArgumentException ex) {
                sendErrorResponse(400, outputStream);
                throw ex;
            } catch (Exception ex) {
                sendErrorResponse(500, outputStream);
                throw ex;
            }
            sendResponse(outputStream, simpleResponse);

        } catch (Exception ex) {
            //The exception raised while trying to open necessary steams
            //to communicate with the client, therefore nothing could be send
            //to the client and just printing the reason of the error.
            ex.printStackTrace();
        } finally {
            if (!connection.isClosed())
                try {
                    connection.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
        }

    }

    /**
     * Sends the response to the client.
     *
     * @param errorCode error code.
     * @param socketOut output stream.
     */
    static void sendErrorResponse(int errorCode, OutputStream socketOut) {
        // Sends response only for not found files.
        // this not uses external lab11.resources to send the response.
        if (errorCode == 404) {
            String notFoundError = "<html>" +
                    "<head><title>Error</title></head>" +
                    "<body>" +
                    "<h2>Error: 404 Not Found</h2>" +
                    "<p>The resource that you requested does not exist on this server.</p>" +
                    "</body>" +
                    "</html>";

            String endOfLine = "\r\n";
            try (PrintWriter printWriter = new PrintWriter(socketOut)) {
                printWriter.print("HTTP/1.1 404 Not Found" + endOfLine);
                printWriter.print("Connection: close" + endOfLine);
                printWriter.print("Content-Type: text/html" + endOfLine);
                printWriter.print(notFoundError.length() + endOfLine);

                //Terminating the line in order to split headers against the body;
                printWriter.println();
                printWriter.print(notFoundError + endOfLine);
                printWriter.flush();
            }
        } else {
            try {
                StatusResponse statusResponse = StatusResponse.findByCode(errorCode);
                SimpleResponse simpleResponse = getErrorResponse(statusResponse);
                sendResponse(socketOut, simpleResponse);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * Constructs the error response to send it to the user.
     *
     * @param statusResponse status response.
     * @return response.
     * @throws IOException throws the Exception
     *                     if any errors appear with reading the file
     */
    private static SimpleResponse getErrorResponse(StatusResponse statusResponse)
            throws IOException {
        switch (statusResponse) {
            case NOT_FOUND:
                return new SimpleResponse()
                        .setContentType(getMimeType(NOT_FOUND_HTML))
                        .setBody(findFile(NOT_FOUND_HTML))
                        .setStatus(StatusResponse.NOT_FOUND);
            case FORBIDDEN:
                return new SimpleResponse()
                        .setContentType(getMimeType(FORBIDDEN_HTML))
                        .setBody(findFile(FORBIDDEN_HTML))
                        .setStatus(StatusResponse.FORBIDDEN);
            case NOT_IMPLEMENTED:
                return new SimpleResponse()
                        .setContentType(getMimeType(NOT_IMPLEMENTED_HTML))
                        .setBody(findFile(NOT_IMPLEMENTED_HTML))
                        .setStatus(StatusResponse.NOT_IMPLEMENTED);
            case BAD_REQUEST:
                return new SimpleResponse()
                        .setContentType(getMimeType(BAD_REQUEST_HTML))
                        .setBody(findFile(BAD_REQUEST_HTML))
                        .setStatus(StatusResponse.NOT_IMPLEMENTED);
            default:
                return new SimpleResponse()
                        .setContentType(getMimeType(INTERNAL_SERVER_ERROR_HTML))
                        .setBody(findFile(INTERNAL_SERVER_ERROR_HTML))
                        .setStatus(StatusResponse.INTERNAL_ERROR);

        }
    }

    /**
     * Checks if the request line is correct and supported;
     * if so returns the requested resource.
     *
     * @param requestLine the request line.
     * @return path to the requested resource.
     */
    private static String getRequestedResource(String requestLine) {
        String[] strings = requestLine.split(" ");
        if (!SUPPORTED_OPERATIONS.contains(strings[0]))
            throw new UnsupportedOperationException("Oops! " +
                    "It seems that you send not supported operation, try again");

        if (strings.length != 3 || !SUPPORTED_HTTP.contains(strings[2]))
            throw new IllegalArgumentException("Oops! " +
                    "It seems that you send a bad request, try again");

        return strings[1];
    }

    /**
     * Sends the given response to the user.
     *
     * @param outputStream   output stream.
     * @param simpleResponse response.
     * @throws IOException throws if any error with output stream is appeared.
     */
    private static void sendResponse(OutputStream outputStream,
                                     SimpleResponse simpleResponse) throws IOException {
        String endOfLine = "\r\n";
        try (PrintWriter printWriter = new PrintWriter(outputStream)) {
            printWriter.print(simpleResponse.getStatus() + endOfLine);
            printWriter.print(simpleResponse.getConnectionStatus() + endOfLine);
            printWriter.print(simpleResponse.getContentType() + endOfLine);
            printWriter.print(simpleResponse.getContentLength() + endOfLine);

            //Terminating the line in order to split headers against the body;
            printWriter.println();
            printWriter.flush();
            sendFile(simpleResponse.getFile(), outputStream);
        }
    }


    /**
     * A class that contains all necessary information about
     * the response that must to send.
     */
    private static class SimpleResponse {
        private String contentType;
        private StatusResponse status;
        private String contentLength;
        private File file;

        /**
         * Returns the content type of the response.
         *
         * @return content type.
         */
        public String getContentType() {
            return contentType;
        }

        /**
         * Sets the content type of the response.
         * returns this in order to use setter in the chain.
         *
         * @param contentType content type.
         * @return this.
         */
        public SimpleResponse setContentType(String contentType) {
            this.contentType = "Content-Type: " + contentType;
            return this;
        }

        /**
         * Returns status of the response.
         *
         * @return status.
         */
        public String getStatus() {
            return status.getFullStatusString();
        }

        /**
         * Sets status of the response.
         * returns this in order to use setter in the chain.
         *
         * @param status status of the response.
         * @return this.
         * @see SimpleResponse
         */
        public SimpleResponse setStatus(StatusResponse status) {
            this.status = status;
            return this;
        }

        /**
         * Basically the method always returns "Connection: close".
         *
         * @return connection status which is in the impelementaion
         * is always "Connection: close".
         */
        public String getConnectionStatus() {
            return "Connection: close";
        }

        /**
         * Returns the length of the file.
         * If the file is not set yet the method returns 0.
         *
         * @return length of the content.
         */
        public String getContentLength() {
            return contentLength;
        }

        /**
         * Returns the file that has to be send.
         *
         * @return the file that has to be send.
         * @see File
         */
        public File getFile() {
            return this.file;
        }

        public SimpleResponse setBody(File file) {
            this.contentLength = "Content-Length: " + file.length();
            this.file = file;
            return this;
        }
    }

    private static String getMimeType(String fileName) {
        int pos = fileName.lastIndexOf('.');
        if (pos < 0)  // no file extension in name
            return "x-application/x-unknown";
        String ext = fileName.substring(pos + 1).toLowerCase();
        if (ext.equals("txt")) return "text/plain";
        else if (ext.equals("html")) return "text/html";
        else if (ext.equals("htm")) return "text/html";
        else if (ext.equals("css")) return "text/css";
        else if (ext.equals("js")) return "text/javascript";
        else if (ext.equals("java")) return "text/x-java";
        else if (ext.equals("jpeg")) return "image/jpeg";
        else if (ext.equals("jpg")) return "image/jpeg";
        else if (ext.equals("png")) return "image/png";
        else if (ext.equals("gif")) return "image/gif";
        else if (ext.equals("ico")) return "image/x-icon";
        else if (ext.equals("class")) return "application/java-vm";
        else if (ext.equals("jar")) return "application/java-archive";
        else if (ext.equals("zip")) return "application/zip";
        else if (ext.equals("xml")) return "application/xml";
        else if (ext.equals("xhtml")) return "application/xhtml+xml";
        else return "x-application/x-unknown";
        // Note:  x-application/x-unknown  is something made up;
        // it will probably make the browser offer to save the file.
    }

    /**
     * Enum which contains all the status responses
     * that are used in the program.
     */
    private enum StatusResponse {
        OK(200, "HTTP/1.1 200 OK"),
        BAD_REQUEST(400, "HTTP/1.1 400 Bad Request"),
        NOT_FOUND(404, "HTTP/1.1 404 Not Found"),
        FORBIDDEN(403, "HTTP/1.1 403 Forbidden"),
        INTERNAL_ERROR(500, "HTTP/1.1 500 Internal Server Error"),
        NOT_IMPLEMENTED(501, "HTTP/1.1 501 Not Implemented");

        private final int statusCode;
        private final String fullString;

        /**
         * Returns the full status string that comes first in a response.
         *
         * @return status string that comes first in a response.
         */
        public String getFullStatusString() {
            return this.fullString;
        }

        /**
         * Returns the status code of the response.
         *
         * @return the status code.
         */
        public int getStatusCode() {
            return this.statusCode;
        }

        /**
         * Returns status response by a given status code.
         * If there is no such status code the operation is
         * considered as error and the method will return
         * {@link StatusResponse#INTERNAL_ERROR}
         *
         * @param statusCode status code.
         * @return status response.
         */
        public static StatusResponse findByCode(int statusCode) {
            for (StatusResponse stc : StatusResponse.values())
                if (stc.getStatusCode() == statusCode)
                    return stc;
            return INTERNAL_ERROR;
        }

        StatusResponse(int statusCode, String fullString) {
            this.statusCode = statusCode;
            this.fullString = fullString;
        }
    }

    private static void sendFile(File file, OutputStream socketOut) throws
            IOException {
        InputStream in = new BufferedInputStream(new FileInputStream(file));
        OutputStream out = new BufferedOutputStream(socketOut);
        while (true) {
            int x = in.read(); // read one byte from file
            if (x < 0)
                break; // end of file reached
            out.write(x);  // write the byte to the socket
        }
        out.flush();
    }

    private static class ConnectionThread extends Thread {
        Socket connection;

        ConnectionThread(Socket connection) {
            this.connection = connection;
        }

        public void run() {
            handleConnection(connection);
        }

    }


    /**
     * Tries to find the path to the file.
     * The file must be in the root directory.
     * If the given path is a folder the routine will
     * try to find  {@link SimpleServer#DEFAULT_PAGE} in it.
     * if the routine could not find the file it will throw
     * the {@link NoSuchElementException}
     *
     * @param pathToFile path to a file.
     * @return the file.
     * @throws AccessDeniedException throws the exception
     *                               if there is no access to the file
     */
//    private static File findFile(String pathToFile) throws AccessDeniedException {
//        ClassLoader classLoader = SimpleServer.class.getClassLoader();
//        File file = new File(classLoader
//                .getResource(ROOT_DIRECTORY + pathToFile)
//                .getPath());
//
//        if (file.exists() && file.isDirectory())
//            file = new File(file.getAbsolutePath() + DEFAULT_PAGE);
//        if (!file.exists())
//            throw new NoSuchElementException("The given file cannot be found: " + pathToFile);
//        if (!file.canRead())
//            throw new AccessDeniedException("There is no access to the file: " + pathToFile);
//
//        return file;
//    }
    public static File findFile(String pathToFile) throws AccessDeniedException {
        var file = new File(new File("")
                .getAbsolutePath() + ROOT_DIRECTORY + pathToFile);

        if (file.exists() && file.isDirectory())
            file = new File(file.getAbsolutePath() + DEFAULT_PAGE);
        if (!file.exists())
            throw new NoSuchElementException("The given file cannot be found: " + pathToFile);
        if (!file.canRead())
            throw new AccessDeniedException("There is no access to the file: " + pathToFile);

        return file;
    }
}
