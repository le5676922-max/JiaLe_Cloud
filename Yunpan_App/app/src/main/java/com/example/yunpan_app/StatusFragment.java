package com.example.yunpan_app;

import android.content.SharedPreferences;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.fragment.app.Fragment;
import java.net.*;
import java.io.*;

public class StatusFragment extends Fragment {
    private TextView stripTitle, stripSub, stripBadge, totalVal, usedVal, usedPct, progressPct, speedUp, speedDown;
    private ProgressBar progressBar;
    private Handler handler = new Handler();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup c, Bundle saved) {
        View v = inflater.inflate(R.layout.fragment_status, c, false);
        stripTitle = v.findViewById(R.id.strip_title);
        stripSub = v.findViewById(R.id.strip_sub);
        stripBadge = v.findViewById(R.id.strip_badge);
        totalVal = v.findViewById(R.id.total_val);
        usedVal = v.findViewById(R.id.used_val);
        usedPct = v.findViewById(R.id.used_pct);
        progressPct = v.findViewById(R.id.progress_pct);
        progressBar = v.findViewById(R.id.progress_bar);
        speedUp = v.findViewById(R.id.speed_up);
        speedDown = v.findViewById(R.id.speed_down);
        refreshStatus();
        return v;
    }

    private void refreshStatus() {
        new Thread(() -> {
            boolean online = false;
            try {
                URL url = new URL("http://127.0.0.1:8080/api/health");
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(3000);
                online = c.getResponseCode() == 200;
            } catch (Exception ignored) {}

            final boolean isOnline = online;
            handler.post(() -> {
                if (isOnline) {
                    stripTitle.setText("节点运行中");
                    stripSub.setText("存储节点正在工作");
                    stripBadge.setText("在线");
                    stripBadge.setTextColor(0xFF34C759);
                    updateHeader(true);
                } else {
                    stripTitle.setText("等待连接");
                    stripSub.setText("请先配置并启动节点");
                    stripBadge.setText("离线");
                    stripBadge.setTextColor(0xFFFF3B30);
                    updateHeader(false);
                }
                totalVal.setText("--");
                usedVal.setText("--");
                usedPct.setText("--");
                progressPct.setText("0%");
                progressBar.setProgress(0);
                speedUp.setText("-- KB/s");
                speedDown.setText("-- KB/s");
            });
        }).start();
        handler.postDelayed(this::refreshStatus, 10000);
    }

    private void updateHeader(boolean online) {
        if (getActivity() != null) {
            View dot = getActivity().findViewById(R.id.header_dot);
            TextView txt = getActivity().findViewById(R.id.header_status_text);
            if (dot != null) {
                dot.setBackgroundResource(online ? R.drawable.status_dot_online : R.drawable.status_dot_offline);
            }
            if (txt != null) {
                txt.setText(online ? "在线" : "离线");
                txt.setTextColor(online ? 0xFF34C759 : 0xFF8E8E93);
            }
        }
    }
}
