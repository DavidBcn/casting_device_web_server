package dropbox.nordicloop.com.dropbox;

import android.content.Context;
import android.content.res.AssetFileDescriptor;

import java.io.*;
import java.util.*;

/**
 * Created by davidf on 15-08-09.
 */
public class WebServer extends NanoHTTPD {
    public static final String MIME_PLAINTEXT = "text/plain";
    public static final String MIME_HTML = "text/html";
    public static final String MIME_JS = "application/javascript";
    public static final String MIME_CSS = "text/css";
    public static final String MIME_PNG = "image/png";
    public static final String MIME_DEFAULT_BINARY = "application/octet-stream";
    public static final String MIME_XML = "text/xml";

    Context mContext;
    public WebServer(Context context) throws IOException {
        super(8080);
        mContext = context;
        start();
        System.out.println( "\nRunning! Point your browsers to http://localhost:8080/ \n" );
    }

    @Override
    public Response serve(IHTTPSession session){
        if(session.getUri().contains(".png")){
            AssetFileDescriptor fd;
            try {
                fd = mContext.getAssets().openFd("yoshi.png");
                // HTTP_OK = "200 OK" or HTTP_OK = Status.OK;(check comments)
                return new NanoHTTPD.Response(Response.Status.OK, MIME_PNG, fd.createInputStream()
                        , fd.getLength());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        String msg = "<html><body><h1>Hello server</h1>\n";
        Map<String, String> parms = session.getParms();
        if (parms.get("username") == null) {
            msg += "<form action='?' method='get'>\n  <p>Your name: <input type='text' name='username'></p>\n" + "</form>\n";
        } else {
            msg += "<p>Hello, " + parms.get("username") + "!</p>";
        }
        return newFixedLengthResponse( msg + "</body></html>\n" );
    }
}
