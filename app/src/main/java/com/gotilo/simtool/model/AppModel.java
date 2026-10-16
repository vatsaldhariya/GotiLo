package com.gotilo.simtool.model;

import android.graphics.drawable.Drawable;

public class AppModel {
    public String appName;
    public String packageName;
    public Drawable icon;
    public boolean isSelected;

    public AppModel(String appName, String packageName, Drawable icon, boolean isSelected) {
        this.appName = appName;
        this.packageName = packageName;
        this.icon = icon;
        this.isSelected = isSelected;
    }
}