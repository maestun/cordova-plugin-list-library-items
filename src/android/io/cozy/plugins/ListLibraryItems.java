// TODO comments, header

package io.cozy.plugins;

import android.content.Context;
import android.database.Cursor;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.MediaStore;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;

import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

// Needed only for fake API calls
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;

public class ListLibraryItems extends CordovaPlugin {

    private final  Context mContext = this.cordova.getActivity().getApplicationContext();
    private static final String PERMISSION_ERROR = "Permission Denial: This application is not allowed to access Photo data.";
    private SimpleDateFormat mDateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");


    private interface ChunkResultRunnable {
        void run(ArrayList<JSONObject> chunk, int chunkNum, boolean isLastChunk);
    }


    @Override
    protected void pluginInitialize() {
        super.pluginInitialize();
    }

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        // Plugin specific one off initialization code here, this one doesn't
        // have any
    }

    @Override
    public boolean execute(String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        // Which method was called? With many methods in a
        // plugin we could do this another way e.g. reflection
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

                        final JSONObject options = args.optJSONObject(0);
                        final int itemsInChunk = options.getInt("itemsInChunk");
                        final double chunkTimeSec = options.getDouble("chunkTimeSec");

                        if (!cordova.hasPermission(READ_EXTERNAL_STORAGE)) {
                            callbackContext.error(PERMISSION_ERROR);
                            return;
                        }

                        listItems(args.getBoolean(0), args.getBoolean(1), args.getBoolean(2), itemsInChunk, chunkTimeSec, new ChunkResultRunnable() {
                            @Override
                            public void run(ArrayList<JSONObject> library, int chunkNum, boolean isLastChunk) {
                                try {

                                    JSONObject result = createListItemsResult(library, chunkNum, isLastChunk);
                                    PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, result);
                                    pluginResult.setKeepCallback(!isLastChunk);
                                    callbackContext.sendPluginResult(pluginResult);

                                } catch (Exception e) {
                                    e.printStackTrace();
                                    callbackContext.error(e.getMessage());
                                }
                            }
                        });

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


    private static JSONObject createListItemsResult(ArrayList<JSONObject> library, int chunkNum, boolean isLastChunk) throws JSONException {
        JSONObject result = new JSONObject();
        result.put("chunkNum", chunkNum);
        result.put("isLastChunk", isLastChunk);
        result.put("library", new JSONArray(library));
        return result;
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

    private void listItems(boolean includePictures, boolean includeVideos, boolean includeCloud,
                           int itemsInChunk, double chunkTimeSec,
                           ChunkResultRunnable completion) {
        try {
            // All columns here: https://developer.android.com/reference/android/provider/MediaStore.Images.ImageColumns.html,
            // https://developer.android.com/reference/android/provider/MediaStore.MediaColumns.html
            JSONObject columns = new JSONObject() {{
                put("int.id", MediaStore.Images.Media._ID);
                put("fileName", MediaStore.Images.ImageColumns.DISPLAY_NAME);
                put("int.width", MediaStore.Images.ImageColumns.WIDTH);
                put("int.height", MediaStore.Images.ImageColumns.HEIGHT);
                put("albumId", MediaStore.Images.ImageColumns.BUCKET_ID);
                put("date.creationDate", MediaStore.Images.ImageColumns.DATE_TAKEN);
                put("float.latitude", MediaStore.Images.ImageColumns.LATITUDE);
                put("float.longitude", MediaStore.Images.ImageColumns.LONGITUDE);
                put("nativeURL", MediaStore.MediaColumns.DATA); // will not be returned to javascript
            }};


            final ArrayList<JSONObject> queryResults = queryContentProvider(mContext, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns, "");

            ArrayList<JSONObject> chunk = new ArrayList<JSONObject>();

            long chunkStartTime = SystemClock.elapsedRealtime();
            int chunkNum = 0;

            for (int i = 0; i < queryResults.size(); i++) {
                JSONObject queryResult = queryResults.get(i);

                // swap width and height if needed

                int orientation = getImageOrientation(new File(queryResult.getString("nativeURL")));
                if (isOrientationSwapsDimensions(orientation)) { // swap width and height
                    int tempWidth = queryResult.getInt("width");
                    queryResult.put("width", queryResult.getInt("height"));
                    queryResult.put("height", tempWidth);
                }


                // photoId is in format "imageid;imageurl"
                queryResult.put("id",queryResult.get("id") + ";" + queryResult.get("nativeURL"));

                queryResult.remove("nativeURL"); // Not needed

                String albumId = queryResult.getString("albumId");
                queryResult.remove("albumId");
                if (false /* includeAlbumData */) {
                    JSONArray albumsArray = new JSONArray();
                    albumsArray.put(albumId);
                    queryResult.put("albumIds", albumsArray);
                }

                chunk.add(queryResult);

                if (i == queryResults.size() - 1) { // Last item
                    completion.run(chunk, chunkNum, true);
                } else if ((itemsInChunk > 0 && chunk.size() == itemsInChunk) || (chunkTimeSec > 0 && (SystemClock.elapsedRealtime() - chunkStartTime) >= chunkTimeSec * 1000)) {
                    completion.run(chunk, chunkNum, false);
                    chunkNum += 1;
                    chunk = new ArrayList<JSONObject>();
                    chunkStartTime = SystemClock.elapsedRealtime();
                }
            }

        } catch (JSONException ex) {
            // TODO:

        } catch (IOException ex) {
            // Do nothing
        }
    }


    private static int getImageOrientation(File imageFile) throws IOException {

        ExifInterface exif = new ExifInterface(imageFile.getAbsolutePath());
        int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

        return orientation;
    }


    // Returns true if orientation rotates image by 90 or 270 degrees.
    private static boolean isOrientationSwapsDimensions(int orientation) {
        return orientation == ExifInterface.ORIENTATION_TRANSPOSE // 5
                || orientation == ExifInterface.ORIENTATION_ROTATE_90 // 6
                || orientation == ExifInterface.ORIENTATION_TRANSVERSE // 7
                || orientation == ExifInterface.ORIENTATION_ROTATE_270; // 8
    }


    private ArrayList<JSONObject> queryContentProvider(Context context, Uri collection, JSONObject columns, String whereClause) throws JSONException {

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
                columnValues.toArray(new String[columns.length()]),
                whereClause, null, sortOrder);

        final ArrayList<JSONObject> buffer = new ArrayList<JSONObject>();

        if (cursor.moveToFirst()) {
            do {
                JSONObject item = new JSONObject();

                for (String column : columnNames) {
                    int columnIndex = cursor.getColumnIndex(columns.get(column).toString());

                    if (column.startsWith("int.")) {
                        item.put(column.substring(4), cursor.getInt(columnIndex));
                        if (column.substring(4).equals("width") && item.getInt("width") == 0) {
                            System.err.println("cursor: " + cursor.getInt(columnIndex));
                        }
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
                buffer.add(item);

                // TODO: return partial result

            }
            while (cursor.moveToNext());
        }

        cursor.close();

        return buffer;

    }
}
