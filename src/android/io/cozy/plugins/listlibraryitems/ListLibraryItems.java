package io.cozy.plugins.listlibraryitems;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.content.pm.PackageManager;

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

public class ListLibraryItems extends CordovaPlugin {

    private Context mContext;
    private CallbackContext mCallbackContext;
    private static final String PERMISSION_ERROR = "Permission Denial: This application is not allowed to access Photo data.";
    private SimpleDateFormat mDateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        mContext = this.cordova.getActivity().getApplicationContext();
    }

    @Override
    public boolean execute(String action, final JSONArray args, final CallbackContext callbackContext)
            throws JSONException {
        mCallbackContext = callbackContext;

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
                        } else {
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
                        new UploadFileTask().execute(ufp);
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

    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults)
            throws JSONException {
        for (int r : grantResults) {
            if (r == PackageManager.PERMISSION_DENIED) {
                this.mCallbackContext.error("Permission denied");
                return;
            }
        }

        this.mCallbackContext.success();
    }

    private boolean listItems(CallbackContext callbackContext, boolean includePictures, boolean includeVideos,
            boolean includeCloud) {
        try {
            // All columns here: https://developer.android.com/reference/android/provider/MediaStore.Images.ImageColumns.html,
            // https://developer.android.com/reference/android/provider/MediaStore.MediaColumns.html
            JSONObject columns = new JSONObject() {
                {
                    put("int.id", MediaStore.Images.Media._ID);
                    put("fileName", MediaStore.Images.ImageColumns.DISPLAY_NAME);
                    put("int.width", MediaStore.Images.ImageColumns.WIDTH);
                    put("int.height", MediaStore.Images.ImageColumns.HEIGHT);
                    put("libraryId", MediaStore.Images.ImageColumns.BUCKET_ID);
                    put("date.creationDate", MediaStore.Images.ImageColumns.DATE_TAKEN);
                    // put("float.latitude", MediaStore.Images.ImageColumns.LATITUDE);
                    // put("float.longitude", MediaStore.Images.ImageColumns.LONGITUDE);
                    put("filePath", MediaStore.MediaColumns.DATA);
                }
            };

            final ArrayList<JSONObject> queryResults = new ArrayList();

            if (includePictures) {
                queryResults
                        .addAll(queryContentProvider(mContext, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns));
                queryResults
                        .addAll(queryContentProvider(mContext, MediaStore.Images.Media.INTERNAL_CONTENT_URI, columns));
            }
            if (includeVideos) {
                queryResults
                        .addAll(queryContentProvider(mContext, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, columns));
                queryResults
                        .addAll(queryContentProvider(mContext, MediaStore.Video.Media.INTERNAL_CONTENT_URI, columns));
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

    private ArrayList<JSONObject> queryContentProvider(Context context, Uri collection, JSONObject columns)
            throws Exception {

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

        // https://stackoverflow.com/a/20429397
        final String selection = MediaStore.Images.Media.BUCKET_DISPLAY_NAME + " = ?";
        final String[] selectionArgs = new String[] { "Camera" };

        final String sortOrder = MediaStore.Images.Media.DATE_TAKEN;

        final Cursor cursor = context.getContentResolver().query(collection,
                columnValues.toArray(new String[columns.length()]), selection, selectionArgs, sortOrder);

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

            } while (cursor.moveToNext());
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

    private class UploadFileTask extends AsyncTask<UploadFilePayload, Integer, Integer> {

        private CallbackContext mCallback;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Integer doInBackground(UploadFilePayload... uploadFilePayloads) {

            final int MAX_BUFFER_SZ = 1024;
            Integer response_code = 0;

            JSONObject json = uploadFilePayloads[0].mJSONObject;
            mCallback = uploadFilePayloads[0].mCallbackContext;

            String file_path = json.optString("filePath");
            String upload_url = json.optString("serverUrl");
            JSONObject headers = json.optJSONObject("headers");
            HttpURLConnection huc = null;
            try {
                // retrieve params from JS

                int bytes_read, bytes_available, buffer_size, total_bytes;
                byte[] buffer;
                huc = (HttpURLConnection) new URL(upload_url).openConnection();

                // get file sz
                File file = new File(file_path);
                FileInputStream fis = new FileInputStream(file);
                bytes_available = fis.available();
                final long FILE_SZ = file.length();

                // set custom headers
                Iterator<?> keys = headers.keys();
                while (keys.hasNext()) {
                    String key = (String) keys.next();
                    String val = headers.getString(key);
                    huc.setRequestProperty(key, val);
                }
                huc.setRequestProperty("Content-Length", "" + FILE_SZ);
                huc.setRequestProperty("User-Agent", System.getProperty("http.agent"));

                // config request
                huc.setUseCaches(false);
                huc.setDoInput(true);
                huc.setDoOutput(true);
                huc.setRequestMethod("POST");
                huc.setChunkedStreamingMode(1024 * 1000);
                huc.setInstanceFollowRedirects(true);

                // read input file chunks + publish progress
                OutputStream out = huc.getOutputStream();
                buffer_size = 4 * 1024;
                buffer = new byte[buffer_size];
                total_bytes = 0;
                while ((bytes_read = fis.read(buffer, 0, bytes_read)) != -1) {
                    out.write(buffer, 0, bytes_read);
                    total_bytes += bytes_read;
                    publishProgress(buffer_size, total_bytes, FILE_SZ);
                }
                out.flush();
                out.close();

                // get server response
                response_code = huc.getResponseCode();
                String response_message = huc.getResponseMessage();

                // back to JS
                if (response_code / 100 != 2) {
                    // error
                    JSONObject json_error = new JSONObject();
                    json_error.put("code", response_code);
                    json_error.put("source", file_path);
                    json_error.put("target", upload_url);
                    json_error.put("message", response_message);

                    PluginResult pr = new PluginResult(PluginResult.Status.ERROR, json_error);
                    mCallback.sendPluginResult(pr);
                } else {
                    // ok
                    InputStream is = huc.getInputStream();
                    StringBuilder sb = new StringBuilder();
                    BufferedReader br = new BufferedReader(new InputStreamReader(is));
                    String read;
                    while ((read = br.readLine()) != null) {
                        sb.append(read);
                    }
                    br.close();
                    PluginResult pr = new PluginResult(PluginResult.Status.OK, sb.toString());
                    mCallback.sendPluginResult(pr);
                }
            } catch (Exception e) {
                e.printStackTrace();
                JSONObject json_error = new JSONObject();
                try {
                    json_error.put("code", -1);
                    json_error.put("source", file_path);
                    json_error.put("target", upload_url);
                    json_error.put("message", e.toString());
                } catch (JSONException ex) {

                }
                PluginResult pr = new PluginResult(PluginResult.Status.ERROR, json_error);
                mCallback.sendPluginResult(pr);
            } finally {
                if (huc != null) {
                    huc.disconnect();
                }
            }
            return response_code;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);

            Log.i("", "Wrote " + values[0] + " bytes (total: " + values[1] + " / " + values[2] + ")");

            // TODO: send progress
        }

        @Override
        protected void onPostExecute(Integer aResponseCode) {
            super.onPostExecute(aResponseCode);
        }
    }
}
