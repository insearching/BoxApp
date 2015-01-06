package com.boxapp.utils;

/**
 * Created by insearching on 19.06.2014.
 */

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

/**
 * This utility class provides an abstraction layer for sending multipart HTTP
 * POST requests to a web server.
 *
 * @author www.codejava.net
 */
public class MultipartUtility {
    private final String boundary;
    private static final String LINE_FEED = "\r\n";
    private HttpURLConnection httpConn;
    private String charset;
    private OutputStream outputStream;
    private PrintWriter writer;
    private UploadStatusCallback callback;

    /**
     * This constructor initializes a new HTTP POST request with content type
     * is set to multipart/form-data
     *
     * @param requestURL
     * @param charset
     * @throws IOException
     */
    public MultipartUtility(UploadStatusCallback callback, String requestURL, String accessToken, String charset)
            throws IOException {
        this.charset = charset;
        this.callback = callback;

        // creates a unique boundary based on time stamp
        boundary = "===" + System.currentTimeMillis() + "===";

        URL url = new URL(requestURL);
        httpConn = (HttpURLConnection) url.openConnection();
        httpConn.setUseCaches(false);
        httpConn.setDoOutput(true); // indicates POST method
        httpConn.setDoInput(true);
        httpConn.setRequestProperty("Authorization", "Bearer " + accessToken);
        httpConn.setRequestProperty("Content-Type",
                "multipart/form-data; boundary=" + boundary);
        outputStream = httpConn.getOutputStream();
        writer = new PrintWriter(new OutputStreamWriter(outputStream, charset),
                true);
    }

    /**
     * Adds a form field to the request
     *
     * @param name  field name
     * @param value field value
     */
    public void addFormField(String name, String value) {
        writer.append("--" + boundary).append(LINE_FEED);
        writer.append("Content-Disposition: form-data; name=\"" + name + "\"")
                .append(LINE_FEED);
        writer.append("Content-Type: text/plain; charset=" + charset).append(
                LINE_FEED);
        writer.append(LINE_FEED);
        writer.append(value).append(LINE_FEED);
        writer.flush();
    }

    /**
     * Adds a upload file section to the request
     *
     * @param fieldName  name attribute in <input type="file" name="..." />
     * @param uploadFile a File to be uploaded
     * @throws IOException
     */
    public void addFilePart(String fieldName, File uploadFile)
            throws IOException {
        String fileName = uploadFile.getName();
        writer.append("--" + boundary).append(LINE_FEED);
        writer.append(
                "Content-Disposition: form-data; name=\"" + fieldName
                        + "\"; filename=\"" + fileName + "\""
        )
                .append(LINE_FEED);
        writer.append(
                "Content-Type: "
                        + URLConnection.guessContentTypeFromName(fileName)
        )
                .append(LINE_FEED);
        writer.append("Content-Transfer-Encoding: binary").append(LINE_FEED);
        writer.append(LINE_FEED);
        writer.flush();

        FileInputStream inputStream = new FileInputStream(uploadFile);
        byte[] buffer = new byte[4096];
        int bytesRead = -1;
        int total = 0;
        long lengthOfFile = uploadFile.length();
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            total += bytesRead;
            outputStream.write(buffer, 0, bytesRead);
            callback.onProgressUpdate((int) ((total * 100) / lengthOfFile), (int) lengthOfFile);
        }

        outputStream.flush();
        inputStream.close();

        writer.append(LINE_FEED);
        writer.flush();
        Log.d("Multipart", "Uploading finished");
    }

//    /**
//     * Completes the request and receives response from the server.
//     *
//     * @return a list of Strings as response in case the server returned
//     * status OK, otherwise an exception is thrown.
//     * @throws IOException
//     */
//    public ResponseEntity finish() throws IOException {
//
//        writer.append(LINE_FEED).flush();
//        writer.append("--" + boundary + "--").append(LINE_FEED);
//        writer.close();
//
//        // checks server's status code first
//        int status = httpConn.getResponseCode();
//        ResponseEntity entity = new ResponseEntity(status);
//        if (status == HttpURLConnection.HTTP_CREATED) {
//            BufferedReader reader = new BufferedReader(new InputStreamReader(
//                    httpConn.getInputStream()));
//            StringBuilder builder = new StringBuilder();
//            String line;
//            while ((line = reader.readLine()) != null) {
//                builder.append(line);
//            }
//            reader.close();
//            httpConn.disconnect();
//            entity.setInfo(BoxHelper.findObject(builder.toString(), 0));
//
//        }
//
//        return entity;
//    }

    public interface UploadStatusCallback {
        public void onProgressUpdate(Integer... progress);
    }
}
