package com.gh4a.fragment;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.ListPreference;

/**
 * 镜像源偏好：沿用 ListPreference 的值存储/展示，
 * 但点击时不弹原生单选对话框，改由 SettingsFragment 弹出
 * “选择 + 实时测速”合并对话框（onClick 空实现，原生对话框永不弹出）。
 */
public class MirrorPresetPreference extends ListPreference {

    public interface OnPickListener {
        void onPickMirrorSource();
    }

    private OnPickListener mPickListener;

    public MirrorPresetPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setOnPickListener(OnPickListener listener) {
        mPickListener = listener;
    }

    @Override
    protected void onClick() {
        // 不调 super.onClick()：原生单选对话框永不弹出
        if (mPickListener != null) {
            mPickListener.onPickMirrorSource();
        }
    }
}
