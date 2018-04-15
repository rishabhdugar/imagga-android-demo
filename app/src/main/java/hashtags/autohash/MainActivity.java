package hashtags.autohash;

import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;


public class MainActivity extends AppCompatActivity {

    ImageView imageView = null;
    TextView textView = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        createRefs();
    }

    private void createRefs() {
        //fix for  exposed beyond app through ClipData.Item.getUri()
        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());
        //fix over
        imageView = (ImageView) findViewById(R.id.selectedImage);
        textView = (TextView) findViewById(R.id.generated_tags);
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectImage();
            }
        });
    }

    String returnid = null;

    public class PostImageToImaggaAsync extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {
            Log.i("imagga pre", "post photo");
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                String response = postImageToImagga(picturePath);
                Log.i("imagga", response);
                JSONObject jsonObject = new JSONObject(response);
                Log.i("imagga id", jsonObject.getJSONArray("uploaded").getJSONObject(0).getString("id"));
                returnid = jsonObject.getJSONArray("uploaded").getJSONObject(0).getString("id");
                //int age = jsonObject.getInt("age");
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            if (returnid != null) {
                GetTagFromImaggaAsync getTagFromImaggaAsyncobj = new GetTagFromImaggaAsync();
                getTagFromImaggaAsyncobj.execute();
            }
        }
    }

    public class GetTagFromImaggaAsync extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                final String response = getTagFromImagga(returnid);
                Log.i("imagga tag", response);
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            JSONObject jsonObject = new JSONObject(response);
                            JSONArray jArray= jsonObject.getJSONArray("results").getJSONObject(0).getJSONArray("tags");
                            String hastags= "";
                            for(int i=0;i<jArray.length();i++){
                                hastags = hastags+"#"+jArray.getJSONObject(i).getString("tag")+" ";
                            }
                            textView.setText(hastags);
                        }catch (Exception e){

                        }
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
        }
    }

    public String postImageToImagga(String filepath) throws Exception {
        HttpURLConnection connection = null;
        DataOutputStream outputStream = null;
        InputStream inputStream = null;

        String twoHyphens = "--";
        String boundary = "*****" + Long.toString(System.currentTimeMillis()) + "*****";
        String lineEnd = "\r\n";

        int bytesRead, bytesAvailable, bufferSize;
        byte[] buffer;
        int maxBufferSize = 1 * 1024 * 1024;

        String filefield = "image";

        String[] q = filepath.split("/");
        int idx = q.length - 1;

        File file = new File(filepath);
        FileInputStream fileInputStream = new FileInputStream(file);

        URL url = new URL("https://api.imagga.com/v1/content");
        connection = (HttpURLConnection) url.openConnection();

        connection.setDoInput(true);
        connection.setDoOutput(true);
        connection.setUseCaches(false);

        connection.setRequestMethod("POST");
        connection.setRequestProperty("Connection", "Keep-Alive");
        connection.setRequestProperty("User-Agent", "Android Multipart HTTP Client 1.0");
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        connection.setRequestProperty("Authorization", Constants.basic_auth);

        outputStream = new DataOutputStream(connection.getOutputStream());
        outputStream.writeBytes(twoHyphens + boundary + lineEnd);
        outputStream.writeBytes("Content-Disposition: form-data; name=\"" + filefield + "\"; filename=\"" + q[idx] + "\"" + lineEnd);
        outputStream.writeBytes("Content-Type: image/jpeg" + lineEnd);
        outputStream.writeBytes("Content-Transfer-Encoding: binary" + lineEnd);
        outputStream.writeBytes(lineEnd);

        bytesAvailable = fileInputStream.available();
        bufferSize = Math.min(bytesAvailable, maxBufferSize);
        buffer = new byte[bufferSize];

        bytesRead = fileInputStream.read(buffer, 0, bufferSize);
        while (bytesRead > 0) {
            outputStream.write(buffer, 0, bufferSize);
            bytesAvailable = fileInputStream.available();
            bufferSize = Math.min(bytesAvailable, maxBufferSize);
            bytesRead = fileInputStream.read(buffer, 0, bufferSize);
        }

        outputStream.writeBytes(lineEnd);
        outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

        inputStream = connection.getInputStream();

        int status = connection.getResponseCode();
        if (status == HttpURLConnection.HTTP_OK) {
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }

            inputStream.close();
            connection.disconnect();
            fileInputStream.close();
            outputStream.flush();
            outputStream.close();

            return response.toString();
        } else {
            throw new Exception("Non ok response returned");
        }
    }

    public String getTagFromImagga(String id) throws Exception {

        String basicAuth = Constants.basic_auth;

        String endpoint_url = "https://api.imagga.com/v1/tagging";
        String image_url = id;

        String url = endpoint_url + "?content=" + image_url;
        URL urlObject = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) urlObject.openConnection();

        connection.setRequestProperty("Authorization", basicAuth);

        int responseCode = connection.getResponseCode();

        System.out.println("\nSending 'GET' request to URL : " + url);
        System.out.println("Response Code : " + responseCode);
//res
        BufferedReader connectionInput = new BufferedReader(new InputStreamReader(connection.getInputStream()));

        String jsonResponse = connectionInput.readLine();

        connectionInput.close();

        System.out.println(jsonResponse);
        return jsonResponse;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void selectImage() {
        final CharSequence[] options = {"Take Photo", "Choose from Gallery", "Cancel"};
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("Add Photo!");
        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int item) {
                if (options[item].equals("Take Photo")) {
                    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    File f = new File(android.os.Environment.getExternalStorageDirectory(), "temp.jpg");
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(f));
                    startActivityForResult(intent, 1);
                } else if (options[item].equals("Choose from Gallery")) {
                    Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    startActivityForResult(intent, 2);
                } else if (options[item].equals("Cancel")) {
                    dialog.dismiss();
                }
            }
        });
        builder.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        picturePath=null;
        if (resultCode == RESULT_OK) {
            if (requestCode == 1) {
                File f = new File(Environment.getExternalStorageDirectory().toString());
                for (File temp : f.listFiles()) {
                    if (temp.getName().equals("temp.jpg")) {
                        f = temp;
                        break;
                    }
                }
                try {
                    Bitmap bitmap;
                    BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
                    bitmap = BitmapFactory.decodeFile(f.getAbsolutePath(), bitmapOptions);
                    picturePath=f.getPath();
                    imageView.setImageBitmap(bitmap);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (requestCode == 2) {
                Uri selectedImage = data.getData();
                String[] filePath = {MediaStore.Images.Media.DATA};
                Cursor c = getContentResolver().query(selectedImage, filePath, null, null, null);
                c.moveToFirst();
                int columnIndex = c.getColumnIndex(filePath[0]);
                picturePath = c.getString(columnIndex);
                c.close();
                Bitmap thumbnail = (BitmapFactory.decodeFile(picturePath));
                imageView.setImageBitmap(thumbnail);
                Log.w("path of image from ", picturePath + "");
                //viewImage.setImageBitmap(thumbnail);
            }
            if(picturePath!=null) {
                PostImageToImaggaAsync postImageToImaggaAsync = new PostImageToImaggaAsync();
                postImageToImaggaAsync.execute();
            }else{
                somethingWentWrong();
            }
        }
    }

    private void somethingWentWrong() {
    }

    String picturePath = null;
}
