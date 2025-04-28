package ext.schoolbright.demofacepass;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import com.srp.AuthApi.AuthApi;
import com.srp.AuthApi.AuthApplyResponse;
import com.srp.AuthApi.ErrorCodeConfig;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class AuthActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "MEGVII-LICENSE";
    public static final int GET_AUTH_OK = 0;

    /*
    * 使用demo过程中请根据自己的实际情况配置cert以及active路径
    * */
//    public static final String CERT_PATH = "/Download/CBG_Android_Face_Reco---77-Trial-one-stage.cert";
    //public static final String CERT_PATH = "/Download/CBG_Android_Face_Reco---30-Trial-two-stage.cert";
    public static final String CERT_PATH = "/Download/CBG_Android_Face_Reco---36500-Formal-one-stage.cert";
    public static final String ACTIVE_PATH = "/Download/CBG_Android_Face_Reco---30-Trial--1-active.txt";

    /* 程序所需权限 ：相机 文件存储 网络访问 */
    private static final int PERMISSIONS_REQUEST = 1;
    private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
    private static final String PERMISSION_WRITE_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;
    private static final String PERMISSION_READ_STORAGE = Manifest.permission.READ_EXTERNAL_STORAGE;
    private static final String PERMISSION_INTERNET = Manifest.permission.INTERNET;
    private static final String PERMISSION_ACCESS_NETWORK_STATE = Manifest.permission.ACCESS_NETWORK_STATE;
    private String[] Permission = new String[]{PERMISSION_CAMERA, PERMISSION_WRITE_STORAGE, PERMISSION_READ_STORAGE, PERMISSION_INTERNET, PERMISSION_ACCESS_NETWORK_STATE};
    private TextView mAuthStatus;
    private AuthApi obj;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);
        mAuthStatus = (TextView) findViewById(R.id.textView_auth_status);

        /* 申请程序所需权限 */
        if (!hasPermission()) {
            requestPermission();
        }
        mAuthStatus.setTextColor(Color.BLUE);
        mAuthStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        mAuthStatus.setBackgroundColor(Color.YELLOW);
        obj = new AuthApi();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button_single_certification:

                try {
                    singleCertification();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                break;
            case R.id.button_double_certification:
                try {
                    doubleCertification();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;

            case R.id.button_ok:
                enterMainactivity();
                break;
        }
    }



    @RequiresApi(api = Build.VERSION_CODES.M)
    private void singleCertification() throws IOException {

        String cert = readExternal(CERT_PATH).trim();

        if (TextUtils.isEmpty(cert)) {
            Toast.makeText(this, "cert is null", Toast.LENGTH_SHORT).show();
            return;
        }
        obj.authDevice(this.getApplicationContext(), cert, "", new AuthApi.AuthDeviceCallBack() {
            @Override
            public void GetAuthDeviceResult(final AuthApplyResponse result) {
                if (result.errorCode == ErrorCodeConfig.AUTH_SUCCESS) {

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showAuthResult("Apply update: OK");
                        }
                    });
                } else {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showAuthResult("Apply update: error. error code is: " + result.errorCode + " , error message: " + result.errorMessage);
                        }
                    });
                }
            }
        });

    }

    /**
     * .
     *
     * @throws IOException
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    private void doubleCertification() throws IOException {

        String cert = readExternal(CERT_PATH).trim();
        String active = readExternal(ACTIVE_PATH).trim();
        if (TextUtils.isEmpty(cert) || TextUtils.isEmpty(active)) {
            Toast.makeText(this, "cert or active is null", Toast.LENGTH_SHORT).show();
            return;
        }

        obj.authDevice(this, cert, active, new AuthApi.AuthDeviceCallBack() {
            @Override
            public void GetAuthDeviceResult(final AuthApplyResponse authApplyResponse) {

                if (authApplyResponse.errorCode == ErrorCodeConfig.AUTH_SUCCESS) {

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showAuthResult("Apply update: OK");
                        }
                    });
                } else {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showAuthResult("Apply update: error. error code is: " + authApplyResponse.errorCode + " , error message: " + authApplyResponse.errorMessage);
                        }
                    });
                }
            }
        });

    }



    public String readExternal(String filename) throws IOException {
        StringBuilder sb = new StringBuilder();
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            filename = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + filename;
            File file = new File(filename);
            if (!file.exists()) {
                return "";
            }
            FileInputStream inputStream = new FileInputStream(filename);
            byte[] buffer = new byte[1024];
            int len = inputStream.read(buffer);
            while (len > 0) {
                sb.append(new String(buffer, 0, len));
                len = inputStream.read(buffer);
            }
            inputStream.close();
        }

        return sb.toString();
    }

    private void showAuthResult(String res) {
        mAuthStatus.setText(res);
    }

    private void enterMainactivity() {
        String strAuth = mAuthStatus.getText().toString();

        if (strAuth.equals("Apply update: OK") && obj.authCheck(this)) {
            Log.d(TAG, "before enter MainActivity ");

            Intent intent = new Intent(AuthActivity.this, NewMainActivity.class);
            startActivity(intent);
            AuthActivity.this.finish();
        }
    }

    /* 判断程序是否有所需权限 android22以上需要自申请权限 */
    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(PERMISSION_READ_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(PERMISSION_WRITE_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(PERMISSION_INTERNET) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(PERMISSION_ACCESS_NETWORK_STATE) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    /* 请求程序所需权限 */
    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(Permission, PERMISSIONS_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST) {
            boolean granted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED)
                    granted = false;
            }
            if (!granted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    if (!shouldShowRequestPermissionRationale(PERMISSION_CAMERA)
                            || !shouldShowRequestPermissionRationale(PERMISSION_READ_STORAGE)
                            || !shouldShowRequestPermissionRationale(PERMISSION_WRITE_STORAGE)
                            || !shouldShowRequestPermissionRationale(PERMISSION_INTERNET)
                            || !shouldShowRequestPermissionRationale(PERMISSION_ACCESS_NETWORK_STATE)) {
                        Toast.makeText(getApplicationContext(), "需要开启摄像头网络文件存储权限", Toast.LENGTH_SHORT).show();
                    }
            }
        }
    }


}
