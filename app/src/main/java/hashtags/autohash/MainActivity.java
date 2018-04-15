package hashtags.autohash;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

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
    Button copy = null,share=null;
    Context mContext = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = getApplicationContext();
        dlg = new ProgressDialog(this);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        createRefs();
    }

    private void requestPerms() {
        ActivityCompat.requestPermissions(MainActivity.this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.CAMERA},
                1);
    }
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    selectImage();
                } else {
                    Toast.makeText(MainActivity.this, getStringFromResource(R.string.perm_allow), Toast.LENGTH_SHORT).show();
                }
                return;
            }
        }
    }

    private void createRefs() {
        //fix for  exposed beyond app through ClipData.Item.getUri()
        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());
        //fix over
        imageView = (ImageView) findViewById(R.id.selectedImage);
        textView = (TextView) findViewById(R.id.generated_tags);
        copy= (Button)findViewById(R.id.copy);
        share=(Button)findViewById(R.id.share);
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int MyVersion = Build.VERSION.SDK_INT;
                if (MyVersion > Build.VERSION_CODES.LOLLIPOP_MR1) {
                    if (checkIfAlreadyhavePermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) && checkIfAlreadyhavePermission(Manifest.permission.CAMERA)) {
                        selectImage();
                    }
                    else{
                        requestPerms();
                }
                }else{
                    selectImage();
                }
            }
        });
        copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                copyToClipboard();
            }
        });
        share.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                shareIntent();
            }
        });

    }
    private boolean checkIfAlreadyhavePermission(String writeExternalStorage) {
        int result = ContextCompat.checkSelfPermission(this, writeExternalStorage);
        if (result == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            return false;
        }
    }
    private void copyToClipboard(){
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(getStringFromResource(R.string.share_subject), textView.getText().toString());
        clipboard.setPrimaryClip(clip);
        showToast(getStringFromResource(R.string.copied));
    }
    private void shareIntent(){
        String shareBody = textView.getText().toString();
        Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
        sharingIntent.setType("text/plain");
        sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, getStringFromResource(R.string.share_title));
        sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareBody);
        startActivity(Intent.createChooser(sharingIntent, getResources().getString(R.string.share_using)));
    }
    ProgressDialog dlg = null;
    public void showProgress(String message) {
        try {
            dlg.setMessage(message);
            dlg.show();
        }catch (Exception e){

        }
    }
    public void dismissProgress() {
        try{
            dlg.dismiss();
        }
        catch (Exception e){

        }
    }
    private void showToast(String message){
        Toast.makeText(mContext,message,Toast.LENGTH_SHORT).show();
    }
    private String getStringFromResource(int id){
        return getResources().getString(id);
    }
    String returnid = null;

    public class PostImageToImaggaAsync extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {
            Log.i("imagga pre", "post photo");
            showProgress(getStringFromResource(R.string.analyzing));
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                String response = postImageToImagga(picturePath);
                JSONObject jsonObject = new JSONObject(response);
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
            else {
                dismissProgress();
                somethingWentWrong();
            }
        }
    }

    public class GetTagFromImaggaAsync extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {

            showProgress(getStringFromResource(R.string.getting_hashtags));
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                final String response = getTagFromImagga(returnid);
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
            dismissProgress();
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
        BufferedReader connectionInput = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String jsonResponse = connectionInput.readLine();
        connectionInput.close();
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
        if (id == R.id.action_exit) {
            finish();
            System.exit(0);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void selectImage() {
        final CharSequence[] options = {getStringFromResource(R.string.take_photo),getStringFromResource(R.string.choose_gallery),getStringFromResource(R.string.cancel)};
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle(getStringFromResource(R.string.add_photo));
        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int item) {
                if (options[item].equals(getStringFromResource(R.string.take_photo))) {
                    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    File f = new File(android.os.Environment.getExternalStorageDirectory(), "temp.jpg");
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(f));
                    startActivityForResult(intent, 1);
                } else if (options[item].equals(getStringFromResource(R.string.choose_gallery))) {
                    Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    startActivityForResult(intent, 2);
                } else if (options[item].equals(getStringFromResource(R.string.cancel))) {
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
        showToast(getStringFromResource(R.string.something_wrong));
    }

    String picturePath = null;
}
