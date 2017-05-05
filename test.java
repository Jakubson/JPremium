package server.jwwwe.engine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import server.jwwwe.console.Console;
import server.jwwwe.engine.data.Header;
import server.jwwwe.engine.data.Request;
import server.jwwwe.engine.data.Response;

public class Client extends Thread
{
    private Socket clientSocket;
    private Server server;
    private BufferedReader bufferedReader;
    private BufferedWriter bufferedWriter;
    private Request request;
    private Response response;
        
    public Client(Socket clientSocket, Server server)
    {
        this.clientSocket = clientSocket;
        this.server = server;
        this.request = new Request();
        this.response = new Response();
    }
    
    @Override
    public void run()
    {
        Console.info("New connection with: " + clientSocket.getInetAddress() + "!");
        
        setName("Client JWWWE with ip: " + clientSocket.getInetAddress());
        
        try
        {
            open();
            
            buildRequest();
            buildResponse();
            
            send();
            
            close();
        }
        catch (IOException exception)
        {
            Console.error(exception.getMessage());
        }
    }
    
    public void open() throws IOException
    {
        bufferedReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        bufferedWriter = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
    }
    
    public void close() throws IOException
    {
        bufferedWriter.close();
        bufferedReader.close();
        clientSocket.close();
    }
    
    public void buildRequest() throws IOException
    {
        String string;
        
        while ((string = bufferedReader.readLine()) != null)
        {
            if (string.isEmpty())
            {
                break;
            }
            
            //TO-DO - This if
            if (string.split(" ")[0].equalsIgnoreCase("GET"))
            {
                String[] decode = string.split(" ");
                
                String method = decode[0];
                File file = new File(decode[1]);
                String version = decode[2];
                
                request.setMethod(method);
                request.setFile(file);
                request.setVersion(version);
                
                continue;
            }
            
            String[] decode = string.split(": ");
            
            String name = decode[0];
            String value = decode[1];
            
            request.write(new Header(name, value));
        }
        
        Console.info(clientSocket.getInetAddress() + " request to " + request.getFile().toString() + "!");
    }
    
    public void buildResponse()
    {
        File file = new File(server.getConfig().getHttpDocs() + request.getFile().toString());
                
        response.setVersion(request.getVersion());
        
        if (file.toString().contains(".."))
        {
            response.setCode(400);
            response.setMessage("Bad Request");
            response.setFile(new File(server.getConfig().getHttpDocs() + "/400.html"));
            
            return;
        }
        
        if (file.isDirectory())
        {
            File index = new File(file + server.getConfig().getDefaultFile().toString());
            
            if (index.exists())
            {
                request.setFile(new File(request.getFile() + server.getConfig().getDefaultFile().toString()));
                                
                buildResponse();
                
                return;
            }
            
            response.setCode(403);
            response.setMessage("Forbidden");
            response.setFile(new File(server.getConfig().getHttpDocs() + "/403.html"));
            
            return;
        }
        
        if (!file.exists())
        {
            response.setCode(404);
            response.setMessage("Not Found");
            response.setFile(new File(server.getConfig().getHttpDocs() + "/404.html"));
            
            return;
        }
        
        response.setCode(200);
        response.setMessage("OK");
        response.setFile(file);
    }
    
    public void send() throws IOException
    {
        Console.info("Sending " + response.getFile().toString() + " to " + clientSocket.getInetAddress() + "!");
        
        String body = new String(Files.readAllBytes(response.getFile().toPath()), StandardCharsets.UTF_8);
       
        response.write(new Header("Server", "JWWWE/B0.0.2"));
        response.write(new Header("Content-Type", "text/html"));
        response.write(new Header("Content-Length", body.length() + ""));
        
        bufferedWriter.write(response.getVersion() + " " + response.getCode() + " " + response.getMessage() + "\r\n");
        
        for (Header header : response.getHeaders())
        {
            bufferedWriter.write(header.getName() + ": " + header.getValue() + "\r\n");
        }
        
        bufferedWriter.write("\r\n");
        bufferedWriter.write(body);
    }
}
