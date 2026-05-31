package com.example.yunpan_app;

import android.content.SharedPreferences;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;
import java.util.*;
import java.net.*;
import java.io.*;
import org.json.*;

public class FilesFragment extends Fragment {
    private RecyclerView rv;
    private FileAdapter adapter;
    private List<FileItem> items = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup c, Bundle saved) {
        View v = inflater.inflate(R.layout.fragment_files, c, false);
        rv = v.findViewById(R.id.file_list);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new FileAdapter(items);
        rv.setAdapter(adapter);
        loadFiles();
        return v;
    }

    private void loadFiles() {
        new Thread(() -> {
            try {
                String json = null;
                // 从本地存储节点 API 获取文件列表
                URL localUrl = new URL("http://127.0.0.1:8080/api/files/list");
                HttpURLConnection lc = (HttpURLConnection) localUrl.openConnection();
                lc.setConnectTimeout(2000);
                SharedPreferences prefs = getActivity().getSharedPreferences("yunpan", 0);
                lc.setRequestProperty("X-Node-Token", prefs.getString("node_token", ""));
                if (lc.getResponseCode() == 200) json = StreamUtils.readUtf8(lc.getInputStream());
                if (json != null) {
                    JSONArray arr = new JSONArray(json);
                    items.clear();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        items.add(new FileItem(o.getString("fileName"), o.getLong("size")));
                    }
                }
            } catch (Exception ignored) {}
            getActivity().runOnUiThread(() -> adapter.notifyDataSetChanged());
        }).start();
    }

    static class FileItem { String name; long size; FileItem(String n, long s) { name=n; size=s; } }
    class FileAdapter extends RecyclerView.Adapter<FileAdapter.VH> {
        List<FileItem> data;
        FileAdapter(List<FileItem> d) { data = d; }
        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(android.R.layout.simple_list_item_2, p, false));
        }
        @Override public void onBindViewHolder(VH h, int i) {
            h.t1.setText(data.get(i).name);
            long s = data.get(i).size;
            h.t2.setText(s >= 1073741824 ? String.format("%.1f GB", s/1073741824.0) :
                    s >= 1048576 ? String.format("%.1f MB", s/1048576.0) :
                    s >= 1024 ? String.format("%.1f KB", s/1024.0) : s + " B");
        }
        @Override public int getItemCount() { return data.size(); }
        class VH extends RecyclerView.ViewHolder {
            TextView t1, t2;
            VH(View v) { super(v); t1 = v.findViewById(android.R.id.text1); t2 = v.findViewById(android.R.id.text2); }
        }
    }
}
