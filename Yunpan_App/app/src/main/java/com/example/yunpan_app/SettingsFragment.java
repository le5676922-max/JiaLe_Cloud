package com.example.yunpan_app;

import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

public class SettingsFragment extends Fragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup c, Bundle saved) {
        View v = inflater.inflate(R.layout.fragment_settings, c, false);
        SharedPreferences prefs = getActivity().getSharedPreferences("yunpan", 0);

        TextView emailEt = v.findViewById(R.id.email_input);
        EditText bindEt = v.findViewById(R.id.host_input);
        Button scanBtn = v.findViewById(R.id.scan_btn);
        Button disconnectBtn = v.findViewById(R.id.disconnect_btn);

        emailEt.setText(prefs.getString("email", ""));
        bindEt.setText("");

        scanBtn.setOnClickListener(x -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).startBindScanner();
            }
        });

        v.findViewById(R.id.save_btn).setOnClickListener(x -> {
            String ticket = bindEt.getText().toString().trim();
            if (ticket.isEmpty()) {
                Toast.makeText(getContext(), "请输入绑定码", Toast.LENGTH_SHORT).show();
                return;
            }
            new Thread(() -> {
                try {
                    new AppBindClient(requireContext()).bind(ticket);
                    requireActivity().runOnUiThread(() -> {
                        ContextCompat.startForegroundService(requireContext(), new Intent(getActivity(), NodeService.class));
                        emailEt.setText(prefs.getString("email", ""));
                        bindEt.setText("");
                        Toast.makeText(getContext(), "节点已绑定", Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception e) {
                    requireActivity().runOnUiThread(() -> Toast.makeText(getContext(),
                            "绑定失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }, "settings-bind").start();
        });

        disconnectBtn.setOnClickListener(x -> {
            new Thread(() -> {
                try {
                    new AppBindClient(requireContext()).unbind();
                } catch (Exception ignored) {
                }
                requireActivity().runOnUiThread(() -> {
                    getActivity().stopService(new Intent(getActivity(), NodeService.class));
                    prefs.edit().clear().apply();
                    getActivity().recreate();
                });
            }, "settings-unbind").start();
        });

        return v;
    }
}
