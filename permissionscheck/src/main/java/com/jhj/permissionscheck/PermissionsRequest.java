package com.jhj.permissionscheck;

import android.Manifest;
import android.app.Activity;
import android.app.FragmentManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.AppOpsManagerCompat;
import android.support.v4.content.ContextCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * 权限请求工具类
 * Created by jhj on 17-8-9.
 */

public class PermissionsRequest {

    private final Activity mActivity;
    private final String[] mPermissions;
    private final OnPermissionsListener mCallback;

    private PermissionsRequest(Builder builder) {
        mActivity = builder.mActivity;
        mPermissions = builder.mPermissions;
        mCallback = builder.mCallback;
        prepare();
    }

    private void prepare() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) { //Android 6.0之前不用检测
            List<String> deniedList = new ArrayList<>();
            for (String permission : mPermissions) {
                if (Manifest.permission.CAMERA.equals(permission) && isCameraDenied()) {
                    deniedList.add(permission);
                }
            }
            mCallback.onPermissions(deniedList, Arrays.asList(mPermissions));
        } else if (isPermissionsDenied()) { //如果有权限被禁止，进行权限请求
            requestPermissions();
        } else { // 所有权限都被允许，使用原生权限管理检验
            List<String> permissionList = getPermissionDenied(mPermissions);
            mCallback.onPermissions(permissionList, Arrays.asList(mPermissions));
        }
    }

    /**
     * 权限被禁，进行权限请求
     */
    private void requestPermissions() {
        String TAG = getClass().getName();
        PermissionsFragment fragment = (PermissionsFragment) mActivity.getFragmentManager().findFragmentByTag(TAG);

        if (fragment == null) {
            Bundle bundle = new Bundle();
            bundle.putStringArray(PermissionsFragment.REQUEST_PERMISSIONS, mPermissions);
            fragment = new PermissionsFragment();
            fragment.setArguments(bundle);

            FragmentManager fragmentManager = mActivity.getFragmentManager();
            fragmentManager
                    .beginTransaction()
                    .add(fragment, TAG)
                    .commitAllowingStateLoss();
            fragmentManager.executePendingTransactions();
        }

        fragment.permissionsCheck(new PermissionsFragment.PermissionsListener() {
            @Override
            public void onRequestPermissionsResult(String[] permissions, int[] grantResults) {
                mCallback.onPermissions(getPermissionDenied(permissions), Arrays.asList(permissions));
            }
        });


    }


    /**
     * 检测权限是否被禁止
     *
     * @return true-禁止
     */
    private boolean isPermissionsDenied() {
        for (String permission : mPermissions) {
            if (ContextCompat.checkSelfPermission(mActivity, permission) != PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取被禁止的权限
     *
     * @param permissions 所有权限
     */
    private List<String> getPermissionDenied(String... permissions) {
        List<String> allowPermissionList = new ArrayList<>();
        List<String> deniedPermissionList = new ArrayList<>();

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(mActivity, permission) == PackageManager.PERMISSION_GRANTED) {
                allowPermissionList.add(permission);
            } else {
                deniedPermissionList.add(permission);
            }
        }

        //对相机进行独立权限鉴定（有些手机能拍照，但不能扫描）
        if (allowPermissionList.contains(Manifest.permission.CAMERA) && isCameraDenied()) {
            deniedPermissionList.add(Manifest.permission.CAMERA);
            allowPermissionList.remove(Manifest.permission.CAMERA);
        }

        //对允许的权限进行底层权限鉴定
        String[] allowArray = new String[allowPermissionList.size()];
        allowPermissionList.toArray(allowArray);
        deniedPermissionList.addAll(bottomLayerPermissionsIdentify(mActivity, allowArray));


        return deniedPermissionList;

    }


    /**
     * 底层权限管理检验
     *
     * @param context     context
     * @param permissions permissions
     * @return 被禁权限
     */
    private List<String> bottomLayerPermissionsIdentify(Context context, String... permissions) {
        List<String> list = new ArrayList<>();

        for (String permission : permissions) {
            String op = AppOpsManagerCompat.permissionToOp(permission);
            if (op != null) {
                int result = AppOpsManagerCompat.noteProxyOp(context, op, context.getPackageName());
                if (result == AppOpsManagerCompat.MODE_IGNORED) { //忽略
                    list.add(permission);
                } else if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) { //禁止
                    list.add(permission);
                }
            }
        }
        return list;
    }


    /**
     * 对于6.0以下以及个别6.0类型手机禁止拍照权限后。能拍照但不能二维码扫描
     *
     * @return boolean true-权限被禁止
     */
    private boolean isCameraDenied() {
        boolean isCanUse = true;
        Camera mCamera = null;
        try {
            mCamera = Camera.open();
            Camera.Parameters mParameters = mCamera.getParameters();
            mCamera.setParameters(mParameters);
        } catch (Exception e) {
            isCanUse = false;
        }

        if (mCamera != null) {
            try {
                mCamera.release();
            } catch (Exception e) {
                e.printStackTrace();
                return !isCanUse;
            }

        }
        return !isCanUse;
    }



    public static class Builder {

        private Activity mActivity;
        private String[] mPermissions;
        private OnPermissionsListener mCallback;

        public Builder(Activity context) {
            this.mActivity = context;
        }


        public Builder requestPermissions(String... permissions) {
            this.mPermissions = permissions;
            return this;
        }

        public Builder callback(OnPermissionsListener callback) {
            this.mCallback = callback;
            return this;
        }

        public void build() {
            new PermissionsRequest(this);
        }


    }


}
