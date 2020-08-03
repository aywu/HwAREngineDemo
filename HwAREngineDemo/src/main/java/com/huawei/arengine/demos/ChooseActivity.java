/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2020-2020. All rights reserved.
 */

package com.huawei.arengine.demos;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.huawei.arengine.demos.common.PermissionManageUtil;

/**
 * ChooseActivity
 *
 * @author HW
 * @since 2020-03-31
 */
public class ChooseActivity extends Activity {
    private static final String TAG = ChooseActivity.class.getSimpleName();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_choose);
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();

        // AREngine requires camera permissions to operate. If we did not yet obtain runtime
        // permission on Android M and above, now is a good time to ask the user for it.
        PermissionManageUtil.onResume(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (!PermissionManageUtil.hasPermission(this)) {
            Toast.makeText(this,
               "This application needs camera permission.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    /**
     * choose activity.
     *
     * @param view View
     */
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_WorldAR_Java:
                startActivity(new Intent(this,
                    com.huawei.arengine.demos.java.world.WorldActivity.class));
                break;
            case R.id.btn_FaceAR:
                startActivity(new Intent(this,
                    com.huawei.arengine.demos.java.face.FaceActivity.class));
                break;
            case R.id.btn_body3d:
                startActivity(new Intent(this,
                    com.huawei.arengine.demos.java.body3d.BodyActivity.class));
                break;
            case R.id.btn_hand:
                startActivity(new Intent(this,
                    com.huawei.arengine.demos.java.hand.HandActivity.class));
                break;
            default:
                Log.e(TAG, "onClick error!");
        }
    }
}