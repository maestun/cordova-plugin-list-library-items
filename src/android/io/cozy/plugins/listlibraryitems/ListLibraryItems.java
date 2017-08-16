// TODO comments, header

package io.cozy.plugins.listlibraryitems;

import android.content.Context;
import android.database.Cursor;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.MimeTypeMap;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;

import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

// Needed only for fake API calls
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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
import static android.R.attr.password;

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



                        uploadItem(callbackContext, args.getJSONObject(0));
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



    private boolean uploadItem(CallbackContext callbackContext, JSONObject aPayload) {
        String uploadUrl  = aPayload.optString("serverUrl");
        JSONObject headers = aPayload.optJSONObject("headers");
        HttpURLConnection urlConnection = null;
        try {

            int bytesRead, bytesAvailable, bufferSize;
            byte[] buffer;
            final int MAX_BUFFER_SZ = 1 * 1024;

            URL url = new URL(uploadUrl);
            urlConnection = (HttpURLConnection) url.openConnection();

            File file = new File(aPayload.optString("filePath"));
            FileInputStream fis = new FileInputStream(file);
            bytesAvailable = fis.available();


            Iterator<?> keys = headers.keys();
            while (keys.hasNext()) {
                String key = (String) keys.next();
                String val = headers.getString(key);
                urlConnection.setRequestProperty(key, val);
            }
            urlConnection.setRequestProperty("Connection", "Keep-Alive");
            urlConnection.setRequestProperty("Content-Length", "" + bytesAvailable);

            urlConnection.setUseCaches(false);
            urlConnection.setDoInput(true);
            urlConnection.setDoOutput(true);
            //urlConnection.setChunkedStreamingMode(1024);
            //urlConnection.setFixedLengthStreamingMode(bytesAvailable);

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
            Log.i("Image length", bytesAvailable + "");

            while (bytesRead > 0) {
                Log.i("Remaining", "write");
                out.write(buffer, 0, bufferSize);
                Log.i("Remaining", "avail");
                bytesAvailable = fis.available();
                Log.i("Remaining", "min");
                bufferSize = Math.min(bytesAvailable, MAX_BUFFER_SZ);
                Log.i("Remaining", "read");
                bytesRead = fis.read(buffer, 0, bufferSize);
                Log.i("Remaining", "bytes " + bytesAvailable);
            }

            out.close();

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

        return true;
    }



        private class UploadFileTask extends AsyncTask<URL, Integer, String> {

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
            }

            @Override
            protected String doInBackground(URL... urls) {
                return null;
            }

            @Override
            protected void onProgressUpdate(Integer... values) {
                super.onProgressUpdate(values);
            }


            @Override
            protected void onPostExecute(String s) {
                super.onPostExecute(s);
            }
        }

}
