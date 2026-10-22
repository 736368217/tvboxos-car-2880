package com.github.tvbox.osc.ui.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Locale;

public class LocalFileAdapter extends BaseQuickAdapter<File, BaseViewHolder> {
    private static final DecimalFormat SIZE_FORMAT = new DecimalFormat("0.#");
    private File parentDir;

    public LocalFileAdapter() {
        super(R.layout.item_local_file, new ArrayList<File>());
    }

    @Override
    protected void convert(BaseViewHolder helper, File item) {
        boolean isParent = parentDir != null && parentDir.equals(item);
        helper.setText(R.id.tvType, item.isDirectory() ? "目录" : "文件");
        helper.setText(R.id.tvName, isParent ? ".." : item.getName());
        helper.setText(R.id.tvInfo, item.isDirectory() ? "进入" : formatSize(item.length()));
        helper.setImageResource(R.id.ivFileIcon, iconFor(item));
    }

    private int iconFor(File file) {
        if (file.isDirectory()) return R.drawable.ic_tvbox_file_folder;
        String name = file.getName().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot + 1) : "";
        if (ext.matches("mp3|flac|wav|aac|m4a|ogg|ape|wma")) return R.drawable.ic_tvbox_file_audio_file;
        if (ext.matches("zip|rar|7z|tar|gz|apk")) return R.drawable.ic_tvbox_file_archive;
        if (ext.matches("mp4|mkv|avi|ts|m2ts|mov|webm|rmvb|flv|wmv|m4v")) return R.drawable.ic_tvbox_file_video_file;
        return R.drawable.ic_tvbox_file_video_file;
    }

    public void setParentDir(File parentDir) {
        this.parentDir = parentDir;
    }

    private String formatSize(long size) {
        if (size < 1024) return size + " B";
        double kb = size / 1024.0;
        if (kb < 1024) return SIZE_FORMAT.format(kb) + " KB";
        double mb = kb / 1024.0;
        if (mb < 1024) return SIZE_FORMAT.format(mb) + " MB";
        return SIZE_FORMAT.format(mb / 1024.0) + " GB";
    }
}
