package com.stepan.skyboundrunner;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

public final class MainActivity extends Activity {
    private RunnerView runnerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        runnerView = new RunnerView(this);
        if (savedInstanceState != null) {
            runnerView.restore(savedInstanceState);
        }
        setContentView(runnerView);
    }

    @Override
    protected void onPause() {
        runnerView.pauseForBackground();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (runnerView != null) {
            runnerView.onForeground();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        runnerView.save(outState);
        super.onSaveInstanceState(outState);
    }
}
