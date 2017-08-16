// TODO comments, header

package io.cozy.plugins.listlibraryitems;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.MimeTypeMap;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;

// Needed only for fake API calls

public class ListLibraryItems extends CordovaPlugin {

    private Context mContext;
    private static final String PERMISSION_ERROR = "Permission Denial: This application is not allowed to access Photo data.";
    private SimpleDateFormat mDateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");


    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        // Plugin specific one off initialization code here, this one doesn't
        // have any
        mContext = this.cordova.getActivity().getApplicationContext();
    }

    @Override
    public boolean execute(String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {

        if ("isAuthorized".equals(action)) {
            this.isAuthorized(callbackContext);
            return true;
        } else if ("requestReadAuthorization".equals(action)) {
            this.requestReadAuthorization(callbackContext);
            return true;
        } else if ("listItems".equals(action)) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    try {
                        if (!cordova.hasPermission(READ_EXTERNAL_STORAGE)) {
                            callbackContext.error(PERMISSION_ERROR);
                        }
                        else {
                            listItems(callbackContext, args.getBoolean(0), args.getBoolean(1), args.getBoolean(2));
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                        callbackContext.error(e.getMessage());
                    }
                }
            });
            return true;
        } else if ("uploadItem".equals(action)) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    try {
                        UploadFilePayload ufp = new UploadFilePayload();
                        ufp.mJSONObject = args.getJSONObject(0);
                        ufp.mCallbackContext = callbackContext;
                       // new UploadFileTask().execute(ufp);


                        String uploadUrl  = ufp.mJSONObject.optString("serverUrl");
                        JSONObject headers = ufp.mJSONObject.optJSONObject("headers");
                        String local = ufp.mJSONObject.optString("filePath");

                        sendFileToServer(local, uploadUrl);
                    } catch (Exception e) {
                        e.printStackTrace();
                        callbackContext.error(e.getMessage());
                    }
                }
            });
            return true;
        }
        // No action matched
        return false;
    }


    private void isAuthorized(CallbackContext callbackContext) {
        boolean ok = cordova.hasPermission(READ_EXTERNAL_STORAGE);
        callbackContext.success(ok ? 1 : 0);
    }


    private void requestReadAuthorization(CallbackContext callbackContext) {
        try {
            if (!cordova.hasPermission(READ_EXTERNAL_STORAGE)) {
                List<String> permissions = new ArrayList<String>();
                permissions.add(READ_EXTERNAL_STORAGE);
                cordova.requestPermissions(this, 0, permissions.toArray(new String[0]));
            } else {
                callbackContext.success();
            }
        } catch (Exception e) {
            e.printStackTrace();
            callbackContext.error(e.getMessage());
        }
    }

    private boolean listItems(CallbackContext callbackContext, boolean includePictures, boolean includeVideos, boolean includeCloud) {
        try {
            // All columns here: https://developer.android.com/reference/android/provider/MediaStore.Images.ImageColumns.html,
            // https://developer.android.com/reference/android/provider/MediaStore.MediaColumns.html
            JSONObject columns = new JSONObject() {{
                put("int.id", MediaStore.Images.Media._ID);
                put("fileName", MediaStore.Images.ImageColumns.DISPLAY_NAME);
                put("int.width", MediaStore.Images.ImageColumns.WIDTH);
                put("int.height", MediaStore.Images.ImageColumns.HEIGHT);
                put("libraryId", MediaStore.Images.ImageColumns.BUCKET_ID);
                put("date.creationDate", MediaStore.Images.ImageColumns.DATE_TAKEN);
                // put("float.latitude", MediaStore.Images.ImageColumns.LATITUDE);
                // put("float.longitude", MediaStore.Images.ImageColumns.LONGITUDE);
                put("filePath", MediaStore.MediaColumns.DATA);
            }};

            final ArrayList<JSONObject> queryResults = new ArrayList();

            if(includePictures) {
                queryResults.addAll(queryContentProvider(mContext, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns));
                queryResults.addAll(queryContentProvider(mContext, MediaStore.Images.Media.INTERNAL_CONTENT_URI, columns));
            }
            if(includeVideos) {
                queryResults.addAll(queryContentProvider(mContext, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, columns));
                queryResults.addAll(queryContentProvider(mContext, MediaStore.Video.Media.INTERNAL_CONTENT_URI, columns));
            }

            JSONObject result = new JSONObject();
            result.put("count", queryResults.size());
            result.put("library", new JSONArray(queryResults));
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, result);
            callbackContext.sendPluginResult(pluginResult);
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            callbackContext.error(e.getMessage());
            return false;
        }
    }


    private ArrayList<JSONObject> queryContentProvider(Context context, Uri collection, JSONObject columns) throws Exception {

        // TODO: filter
        // https://stackoverflow.com/a/4495753

        final ArrayList<String> columnNames = new ArrayList<String>();
        final ArrayList<String> columnValues = new ArrayList<String>();

        Iterator<String> iteratorFields = columns.keys();

        while (iteratorFields.hasNext()) {
            String column = iteratorFields.next();

            columnNames.add(column);
            columnValues.add("" + columns.getString(column));
        }

        final String sortOrder = MediaStore.Images.Media.DATE_TAKEN;

        final Cursor cursor = context.getContentResolver().query(
                collection,
                columnValues.toArray(new String[columns.length()]), "", null, sortOrder);

        final ArrayList<JSONObject> buffer = new ArrayList<JSONObject>();

        if (cursor.moveToFirst()) {
            do {
                JSONObject item = new JSONObject();

                for (String column : columnNames) {
                    int columnIndex = cursor.getColumnIndex(columns.get(column).toString());

                    if (column.startsWith("int.")) {
                        item.put(column.substring(4), cursor.getInt(columnIndex));
                        /*if (column.substring(4).equals("width") && item.getInt("width") == 0) {
                            System.err.println("cursor: " + cursor.getInt(columnIndex));
                        }*/
                    } else if (column.startsWith("float.")) {
                        item.put(column.substring(6), cursor.getFloat(columnIndex));
                    } else if (column.startsWith("date.")) {
                        long intDate = cursor.getLong(columnIndex);
                        Date date = new Date(intDate);
                        item.put(column.substring(5), mDateFormatter.format(date));
                    } else {
                        item.put(column, cursor.getString(columnIndex));
                    }
                }

                item.put("mimeType", getMimeType(item.getString("filePath")));
                buffer.add(item);

                // TODO: return partial result

            }
            while (cursor.moveToNext());
        }

        cursor.close();
        return buffer;
    }


    private static String getMimeType(String url) {
        String type = null;
        String extension = MimeTypeMap.getFileExtensionFromUrl(url);
        if (extension != null) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        return type;
    }






    private class UploadFilePayload {
        protected CallbackContext mCallbackContext;
        protected JSONObject mJSONObject;
    }


    private class UploadFileTask extends AsyncTask<UploadFilePayload, Integer, String> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected String doInBackground(UploadFilePayload... uploadFilePayloads) {

            JSONObject json = uploadFilePayloads[0].mJSONObject;
            CallbackContext callback = uploadFilePayloads[0].mCallbackContext;

            String uploadUrl  = json.optString("serverUrl");
            JSONObject headers = json.optJSONObject("headers");
            HttpURLConnection urlConnection = null;
            try {

                int bytesRead, bytesAvailable, bufferSize;
                byte[] buffer;
                final int MAX_BUFFER_SZ = 1 * 1024;

                URL url = new URL(uploadUrl);
                urlConnection = (HttpURLConnection) url.openConnection();

                // get file sz
                File file = new File(json.optString("filePath"));
                FileInputStream fis = new FileInputStream(file);
                bytesAvailable = fis.available();
                final int FILE_SZ = bytesAvailable;

                // custom headers
                Iterator<?> keys = headers.keys();
                while (keys.hasNext()) {
                    String key = (String) keys.next();
                    String val = headers.getString(key);
                    urlConnection.setRequestProperty(key, val);
                }
                // urlConnection.setRequestProperty("Connection", "Keep-Alive");
                urlConnection.setRequestProperty("Content-Length", "" + bytesAvailable);
                urlConnection.setRequestProperty("User-Agent", System.getProperty("http.agent"));

                // config
                urlConnection.setUseCaches(false);
                urlConnection.setDoInput(true);
                urlConnection.setDoOutput(true);
                urlConnection.setRequestMethod("POST");

                // urlConnection.setChunkedStreamingMode(1024);
                // urlConnection.setFixedLengthStreamingMode(bytesAvailable);

                Authenticator.setDefault(new Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication("user", "pass".toCharArray());
                    }
                });

                OutputStream out = urlConnection.getOutputStream();
                bufferSize = Math.min(bytesAvailable, MAX_BUFFER_SZ);
                buffer = new byte[bufferSize];

                // Read file
                bytesRead = fis.read(buffer, 0, bufferSize);

                int total_bytes = bytesRead;
                while (bytesRead > 0) {

                    out.write(buffer, 0, bufferSize);
                    bytesAvailable = fis.available();
                    bufferSize = Math.min(bytesAvailable, MAX_BUFFER_SZ);
                    bytesRead = fis.read(buffer, 0, bufferSize);
                    total_bytes += bytesRead;
                    publishProgress(bufferSize, total_bytes, FILE_SZ);
                }

                out.flush();
                out.close();

                // InputStream is = urlConnection.getInputStream();

                int serverResponseCode = urlConnection.getResponseCode();
                String serverResponseMessage = urlConnection.getResponseMessage();
                Log.i("Server Response Code ", "" + serverResponseCode);
                Log.i("Server Response Message", serverResponseMessage);

            } catch (Exception e) {
                Log.e("", e.getLocalizedMessage());

            } finally {
                if(urlConnection != null) {
                    urlConnection.disconnect();
                }
            }

            return "";
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);

            Log.i("", "Wrote " + values[0] + " bytes (total: " + values[1] + " / " + values[2] + ")" );
        }


        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
        }
    }





    public String sendFileToServer(String filename, String targetUrl) {
        String response = "error";
        Log.e("Image filename", filename);
        Log.e("url", targetUrl);
        HttpURLConnection connection = null;
        DataOutputStream outputStream = null;
        // DataInputStream inputStream = null;

        String pathToOurFile = filename;
        String urlServer = targetUrl;
        String lineEnd = "\r\n";
        String twoHyphens = "--";
        String boundary = "*****";
        DateFormat df = new SimpleDateFormat("yyyy_MM_dd_HH:mm:ss");

        int bytesRead, bytesAvailable, bufferSize;
        byte[] buffer;
        int maxBufferSize = 1 * 1024;
        try {
            FileInputStream fileInputStream = new FileInputStream(new File(
                    pathToOurFile));

            URL url = new URL(urlServer);
            connection = (HttpURLConnection) url.openConnection();

            // Allow Inputs & Outputs
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setChunkedStreamingMode(1024);
            // Enable POST method
            connection.setRequestMethod("POST");

            connection.setRequestProperty("Connection", "Keep-Alive");
            connection.setRequestProperty("Content-Type",
                    "multipart/form-data;boundary=" + boundary);

            outputStream = new DataOutputStream(connection.getOutputStream());
            outputStream.writeBytes(twoHyphens + boundary + lineEnd);

            String connstr = null;
            connstr = "Content-Disposition: form-data; name=\"uploadedfile\";filename=\""
                    + pathToOurFile + "\"" + lineEnd;
            Log.i("Connstr", connstr);

            outputStream.writeBytes(connstr);
            outputStream.writeBytes(lineEnd);

            bytesAvailable = fileInputStream.available();
            bufferSize = Math.min(bytesAvailable, maxBufferSize);
            buffer = new byte[bufferSize];

            // Read file
            bytesRead = fileInputStream.read(buffer, 0, bufferSize);
            Log.e("Image length", bytesAvailable + "");
            try {
                while (bytesRead > 0) {
                    try {
                        outputStream.write(buffer, 0, bufferSize);
                    } catch (OutOfMemoryError e) {
                        e.printStackTrace();
                        response = "outofmemoryerror";
                        return response;
                    }
                    bytesAvailable = fileInputStream.available();
                    bufferSize = Math.min(bytesAvailable, maxBufferSize);
                    bytesRead = fileInputStream.read(buffer, 0, bufferSize);
                }
            } catch (Exception e) {
                e.printStackTrace();
                response = "error";
                return response;
            }
            outputStream.writeBytes(lineEnd);
            outputStream.writeBytes(twoHyphens + boundary + twoHyphens
                    + lineEnd);

            // Responses from the server (code and message)
            int serverResponseCode = connection.getResponseCode();
            String serverResponseMessage = connection.getResponseMessage();
            Log.i("Server Response Code ", "" + serverResponseCode);
            Log.i("Server Response Message", serverResponseMessage);

            if (serverResponseCode == 200) {
                response = "true";
            }

            String CDate = null;
            Date serverTime = new Date(connection.getDate());
            try {
                CDate = df.format(serverTime);
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("Date Exception", e.getMessage() + " Parse Exception");
            }
            Log.i("Server Response Time", CDate + "");

            filename = CDate
                    + filename.substring(filename.lastIndexOf("."),
                    filename.length());
            Log.i("File Name in Server : ", filename);

            fileInputStream.close();
            outputStream.flush();
            outputStream.close();
            outputStream = null;
        } catch (Exception ex) {
            // Exception handling
            response = "error";
            Log.e("Send file Exception", ex.getMessage() + "");
            ex.printStackTrace();
        }
        return response;
    }


}
