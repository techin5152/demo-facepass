package ext.schoolbright.demofacepass;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.view.Window;

import com.srp.AuthApi.AuthApi;

public class SplashActivity extends Activity {

    private Handler mHandler = new Handler();
    private AuthApi authApi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().requestFeature(Window.FEATURE_NO_TITLE);

        int mCurrentOrientation = getResources().getConfiguration().orientation;

        if (mCurrentOrientation == Configuration.ORIENTATION_PORTRAIT) {
            setContentView(R.layout.activity_splash);
        } else if (mCurrentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            setContentView(R.layout.activity_splash);
        }

        authApi = new AuthApi();


        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (authApi.authCheck(SplashActivity.this)){
                    Intent intent = new Intent(SplashActivity.this, NewMainActivity.class);
                    startActivity(intent);
                    SplashActivity.this.finish();
                }else {
                    Intent intent = new Intent(SplashActivity.this, AuthActivity.class);
                    startActivity(intent);
                    SplashActivity.this.finish();
                }


            }
        }, 1000);
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
